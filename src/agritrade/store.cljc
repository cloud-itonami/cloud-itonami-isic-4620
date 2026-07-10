(ns agritrade.store
  "SSoT for the agri-wholesale actor, behind a `Store` protocol so
  the backend is a swap, not a rewrite -- the same seam every prior
  `cloud-itonami-isic-*` actor in this fleet uses.

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/agritrade/store_contract_test.clj), which is the whole point:
  the actor, the Agri Trading Governor and the audit ledger never know
  which SSoT they run on.

  Like the fuel-wholesale sibling's `fuel-order` entity, this vertical's
  `dispatch` and `settle` actuation events apply SEQUENTIALLY to the
  SAME `agri-order` -- a delivery happens first (grain/feed/seed/fibre
  or live animals leave the elevator/store/yard), invoice settlement
  happens later, on the same order record. This matches the sequential
  dual-actuation shape, with dedicated double-actuation-guard booleans
  (`:dispatched?`/`:invoiced?`, never a `:status` value).

  The `agri-order` record carries a `:consignment-kind` (`:plant` |
  `:animal`) that routes which biosecurity certificate boolean
  (`:phytosanitary-certificate?` for `:plant`, `:animal-health-
  certificate?` for `:animal`) `agritrade.governor` actually checks --
  see `agritrade.facts` and `agritrade.governor` for why ISIC 4620
  needs this split where the single-commodity fuel-wholesale sibling
  does not.

  The ledger stays append-only on every backend: 'which agri-order was
  verified for a jurisdiction with no official spec-basis, which
  counterparty had credit-uncleared / no contract / a missing
  phytosanitary or animal-health certificate / an unresolved
  sanctions-screening flag, which order was dispatched, which invoice
  was settled, on what jurisdictional and biosecurity basis, approved
  by whom' is always a query over an immutable log -- the audit trail a
  regulator, a counterparty, or an operator trusting an agri-wholesale
  actor needs, and the evidence an operator needs if a delivery or an
  invoice is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [agritrade.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (agri-order [s id])
  (all-agri-orders [s])
  (assessment-of [s agri-order-id] "committed biosecurity assessment, or nil")
  (ledger [s])
  (delivery-history [s] "the append-only agri-delivery history (agritrade.registry drafts)")
  (invoice-history [s] "the append-only agri-invoice history (agritrade.registry drafts)")
  (next-delivery-sequence [s jurisdiction] "next delivery-number sequence for a jurisdiction")
  (next-invoice-sequence [s jurisdiction] "next invoice-number sequence for a jurisdiction")
  (agri-order-already-dispatched? [s agri-order-id] "has this consignment already been dispatched?")
  (agri-order-already-invoiced? [s agri-order-id] "has this order's invoice already been settled?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-agri-orders [s agri-orders] "replace/seed the agri-order directory (map id->agri-order)"))

;; ----------------------------- demo data -----------------------------

(defn- base-order
  "The neutral, clean `:consignment-kind :plant` agri-order shape (every
  field in its safe state), so each demo order below isolates exactly
  ONE failure mode by overriding a single field."
  [overrides]
  (merge {:id "ao-1" :order-id "AO-2026-0001" :consignment-kind :plant
          :commodity "Feed Wheat No. 2 (bulk)" :quantity 5000 :unit "metric tons"
          :counterparty "Akita Grain Traders Co" :price 245.00
          :contract-terms "FOB elevator, net 30 days"
          :credit-cleared? true :sanctions-screened? true
          :phytosanitary-certificate? true :animal-health-certificate? true
          :dispatched? false :invoiced? false
          :jurisdiction "JPN" :status :intake
          :dispatch-number nil :invoice-number nil}
         overrides))

(defn demo-data
  "A small, self-contained agri-order set covering both actuation
  lifecycles (delivery, invoice settlement) plus the Agri Trading
  Governor's own checks -- including BOTH consignment kinds
  (`:plant`/`:animal`) so both certificate checks are exercised -- so
  the actor + tests run offline. Each violation order isolates exactly
  ONE failure mode (the rest stay clean) following the 'exercise the
  failure mode directly, never only via a happy-path actuation'
  discipline every sibling governor's demo data establishes."
  []
  {:agri-orders
   (into {}
         (for [o [(base-order {:id "ao-1" :order-id "AO-2026-0001"})
                  (base-order {:id "ao-2" :order-id "AO-2026-0002"
                               :counterparty "Atlantis Grain Ltd"
                               :jurisdiction "ATL"})
                  (base-order {:id "ao-3" :order-id "AO-2026-0003"
                               :counterparty "Cedar Grain Co"
                               :credit-cleared? false})
                  (base-order {:id "ao-4" :order-id "AO-2026-0004"
                               :counterparty "Delta Feed BV"
                               :contract-terms nil})
                  (base-order {:id "ao-5" :order-id "AO-2026-0005"
                               :counterparty "Eagle Grain SA"
                               :sanctions-screened? false})
                  (base-order {:id "ao-6" :order-id "AO-2026-0006"
                               :counterparty "Foxtrot Seed Co"
                               :phytosanitary-certificate? false})
                  (base-order {:id "ao-7" :order-id "AO-2026-0007"
                               :consignment-kind :animal
                               :commodity "Holstein dairy heifers"
                               :quantity 120 :unit "head"
                               :counterparty "Golden Hills Livestock Co"
                               :price 1850.00})
                  (base-order {:id "ao-8" :order-id "AO-2026-0008"
                               :consignment-kind :animal
                               :commodity "Angus feeder cattle"
                               :quantity 80 :unit "head"
                               :counterparty "Highland Cattle Traders"
                               :price 1620.00
                               :animal-health-certificate? false})]]
           [(:id o) o]))})

;; ----------------------------- shared commit logic -----------------------------

(defn- deliver-order!
  "Backend-agnostic `:order/mark-dispatched` -- looks up the agri-order
  via the protocol and drafts the agri-delivery record, and returns
  {:result .. :agri-order-patch ..} for the caller to persist."
  [s agri-order-id]
  (let [ao (agri-order s agri-order-id)
        seq-n (next-delivery-sequence s (:jurisdiction ao))
        result (registry/register-delivery-record agri-order-id (:jurisdiction ao) seq-n)]
    {:result result
     :agri-order-patch {:dispatched? true
                        :dispatch-number (get result "delivery_number")}}))

(defn- invoice-order!
  "Backend-agnostic `:order/mark-invoiced` -- looks up the agri-order
  via the protocol and drafts the agri-invoice record, and returns
  {:result .. :agri-order-patch ..} for the caller to persist."
  [s agri-order-id]
  (let [ao (agri-order s agri-order-id)
        seq-n (next-invoice-sequence s (:jurisdiction ao))
        result (registry/register-invoice-record agri-order-id (:jurisdiction ao) seq-n)]
    {:result result
     :agri-order-patch {:invoiced? true
                        :invoice-number (get result "invoice_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (agri-order [_ id] (get-in @a [:agri-orders id]))
  (all-agri-orders [_] (sort-by :id (vals (:agri-orders @a))))
  (assessment-of [_ agri-order-id] (get-in @a [:assessments agri-order-id]))
  (ledger [_] (:ledger @a))
  (delivery-history [_] (:deliveries @a))
  (invoice-history [_] (:invoices @a))
  (next-delivery-sequence [_ jurisdiction] (get-in @a [:delivery-sequences jurisdiction] 0))
  (next-invoice-sequence [_ jurisdiction] (get-in @a [:invoice-sequences jurisdiction] 0))
  (agri-order-already-dispatched? [_ agri-order-id] (boolean (get-in @a [:agri-orders agri-order-id :dispatched?])))
  (agri-order-already-invoiced? [_ agri-order-id] (boolean (get-in @a [:agri-orders agri-order-id :invoiced?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :order/upsert
      (swap! a update-in [:agri-orders (:id value)] merge value)

      :biosecurity-assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :order/mark-dispatched
      (let [agri-order-id (first path)
            {:keys [result agri-order-patch]} (deliver-order! s agri-order-id)
            jurisdiction (:jurisdiction (agri-order s agri-order-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:delivery-sequences jurisdiction] (fnil inc 0))
                       (update-in [:agri-orders agri-order-id] merge agri-order-patch)
                       (update :deliveries registry/append result))))
        result)

      :order/mark-invoiced
      (let [agri-order-id (first path)
            {:keys [result agri-order-patch]} (invoice-order! s agri-order-id)
            jurisdiction (:jurisdiction (agri-order s agri-order-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:invoice-sequences jurisdiction] (fnil inc 0))
                       (update-in [:agri-orders agri-order-id] merge agri-order-patch)
                       (update :invoices registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-agri-orders [s agri-orders] (when (seq agri-orders) (swap! a assoc :agri-orders agri-orders)) s))

(defn seed-db
  "A MemStore seeded with the demo agri-order set. The deterministic default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {}
                           :ledger [] :delivery-sequences {} :deliveries []
                           :invoice-sequences {} :invoices []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (assessment payloads, ledger facts, delivery/
  invoice records) are stored as EDN strings so `langchain.db`
  doesn't expand them into sub-entities -- the same convention every
  sibling actor's store uses."
  {:agri-order/id                        {:db/unique :db.unique/identity}
   :assessment/agri-order-id             {:db/unique :db.unique/identity}
   :ledger/seq                           {:db/unique :db.unique/identity}
   :delivery/seq                         {:db/unique :db.unique/identity}
   :invoice/seq                          {:db/unique :db.unique/identity}
   :delivery-sequence/jurisdiction       {:db/unique :db.unique/identity}
   :invoice-sequence/jurisdiction        {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

;; Every agri-order field is stored as its own Datomic attr so a governor
;; pull reads the exact ground truth (no blob decode). Boolean fields
;; are coerced on read so a missing attr reads back as false (parity
;; with MemStore). [field-key tx-attr boolean?]
(def ^:private agri-order-fields
  [[:id :agri-order/id false]
   [:order-id :agri-order/order-id false]
   [:consignment-kind :agri-order/consignment-kind false]
   [:commodity :agri-order/commodity false]
   [:quantity :agri-order/quantity false]
   [:unit :agri-order/unit false]
   [:counterparty :agri-order/counterparty false]
   [:price :agri-order/price false]
   [:contract-terms :agri-order/contract-terms false]
   [:credit-cleared? :agri-order/credit-cleared? true]
   [:sanctions-screened? :agri-order/sanctions-screened? true]
   [:phytosanitary-certificate? :agri-order/phytosanitary-certificate? true]
   [:animal-health-certificate? :agri-order/animal-health-certificate? true]
   [:dispatched? :agri-order/dispatched? true]
   [:invoiced? :agri-order/invoiced? true]
   [:jurisdiction :agri-order/jurisdiction false]
   [:status :agri-order/status false]
   [:dispatch-number :agri-order/dispatch-number false]
   [:invoice-number :agri-order/invoice-number false]])

(defn- agri-order->tx [ao]
  (reduce (fn [tx [k attr _bool?]]
            (let [v (get ao k)]
              (cond-> tx (some? v) (assoc attr v))))
          {:agri-order/id (:id ao)}
          agri-order-fields))

(def ^:private agri-order-pull (mapv second agri-order-fields))

(defn- pull->agri-order [m]
  (when (:agri-order/id m)
    (reduce (fn [ao [k attr bool?]]
              (let [v (get m attr)]
                (cond
                  bool?        (assoc ao k (boolean v))
                  (some? v)    (assoc ao k v)
                  :else        ao)))
            {:id (:agri-order/id m)}
            agri-order-fields)))

(defrecord DatomicStore [conn]
  Store
  (agri-order [_ id]
    (pull->agri-order (d/pull (d/db conn) agri-order-pull [:agri-order/id id])))
  (all-agri-orders [_]
    (->> (d/q '[:find [?id ...] :where [?e :agri-order/id ?id]] (d/db conn))
         (map #(pull->agri-order (d/pull (d/db conn) agri-order-pull [:agri-order/id %])))
         (sort-by :id)))
  (assessment-of [_ agri-order-id]
    (dec* (d/q '[:find ?p . :in $ ?aoid
                :where [?a :assessment/agri-order-id ?aoid] [?a :assessment/payload ?p]]
              (d/db conn) agri-order-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (delivery-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :delivery/seq ?s] [?e :delivery/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (invoice-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :invoice/seq ?s] [?e :invoice/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-delivery-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :delivery-sequence/jurisdiction ?j] [?e :delivery-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-invoice-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :invoice-sequence/jurisdiction ?j] [?e :invoice-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (agri-order-already-dispatched? [s agri-order-id]
    (boolean (:dispatched? (agri-order s agri-order-id))))
  (agri-order-already-invoiced? [s agri-order-id]
    (boolean (:invoiced? (agri-order s agri-order-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :order/upsert
      (d/transact! conn [(agri-order->tx value)])

      :biosecurity-assessment/set
      (d/transact! conn [{:assessment/agri-order-id (first path) :assessment/payload (enc payload)}])

      :order/mark-dispatched
      (let [agri-order-id (first path)
            {:keys [result agri-order-patch]} (deliver-order! s agri-order-id)
            jurisdiction (:jurisdiction (agri-order s agri-order-id))
            next-n (inc (next-delivery-sequence s jurisdiction))]
        (d/transact! conn
                     [(agri-order->tx (assoc agri-order-patch :id agri-order-id))
                      {:delivery-sequence/jurisdiction jurisdiction :delivery-sequence/next next-n}
                      {:delivery/seq (count (delivery-history s)) :delivery/record (enc (get result "record"))}])
        result)

      :order/mark-invoiced
      (let [agri-order-id (first path)
            {:keys [result agri-order-patch]} (invoice-order! s agri-order-id)
            jurisdiction (:jurisdiction (agri-order s agri-order-id))
            next-n (inc (next-invoice-sequence s jurisdiction))]
        (d/transact! conn
                     [(agri-order->tx (assoc agri-order-patch :id agri-order-id))
                      {:invoice-sequence/jurisdiction jurisdiction :invoice-sequence/next next-n}
                      {:invoice/seq (count (invoice-history s)) :invoice/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-agri-orders [s agri-orders]
    (when (seq agri-orders) (d/transact! conn (mapv agri-order->tx (vals agri-orders)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:agri-orders ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [agri-orders]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-agri-orders s agri-orders))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo agri-order set -- the Datomic-backed
  analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
