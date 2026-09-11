(ns agritrade.sim
  "Demo driver -- `clojure -M:dev:run`. Walks TWO clean agri-orders (one
  `:plant` grain consignment, one `:animal` livestock consignment)
  through intake -> biosecurity verification -> delivery (escalate/
  approve/commit) -> invoice settlement (escalate/approve/commit), then
  shows HARD-hold scenarios: a jurisdiction with no spec-basis, a
  counterparty whose credit has not been cleared, an order with no
  contract-terms on file, a grain consignment with no phytosanitary
  certificate on file, a livestock consignment with no animal-health
  certificate on file, a counterparty that has not passed sanctions
  screening, a double delivery, and a double invoice.

  Like every sibling actor's domain checks, this actor's checks
  (`credit-uncleared`, `contract-missing`,
  `phytosanitary-certificate-missing`, `animal-health-certificate-
  missing`, `counterparty-sanctions-flag-unresolved`) are evaluated
  directly at `:delivery/dispatch` (and sanctions at `:invoice/settle`
  too) rather than via a separate screening op -- a real delivery
  decision validates counterparty credit, contract-on-file, biosecurity
  certification and sanctions screening at the point of the act itself,
  not as a discrete pre-screening ceremony. Each check is still
  exercised directly and independently below, one order per HARD-hold
  scenario, following the SAME 'exercise the failure mode directly,
  never only via a happy-path actuation' discipline `parksafety`'s
  ADR-2607071922 Decision 5 and every sibling since establish."
  (:require [langgraph.graph :as g]
            [agritrade.store :as store]
            [agritrade.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :trading-supervisor :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== order/intake ao-1 (JPN, grain, clean) ==")
    (println (exec-op actor "t1" {:op :order/intake :subject "ao-1"
                                  :patch {:id "ao-1" :counterparty "Akita Grain Traders Co"}} operator))

    (println "== biosecurity/verify ao-1 (escalates -- human approves) ==")
    (println (exec-op actor "t2" {:op :biosecurity/verify :subject "ao-1"} operator))
    (println (approve! actor "t2"))

    (println "== delivery/dispatch ao-1 (always escalates -- :delivery/dispatch) ==")
    (let [r (exec-op actor "t3" {:op :delivery/dispatch :subject "ao-1"} operator)]
      (println r)
      (println "-- human trading supervisor approves --")
      (println (approve! actor "t3")))

    (println "== invoice/settle ao-1 (always escalates -- :invoice/settle) ==")
    (let [r (exec-op actor "t4" {:op :invoice/settle :subject "ao-1"} operator)]
      (println r)
      (println "-- human trading supervisor approves --")
      (println (approve! actor "t4")))

    (println "== biosecurity/verify ao-2 (no spec-basis -> HARD hold) ==")
    (println (exec-op actor "t5" {:op :biosecurity/verify :subject "ao-2"} operator))

    (println "== biosecurity/verify ao-3 (escalates -- human approves; sets up the credit-uncleared test) ==")
    (println (exec-op actor "t6" {:op :biosecurity/verify :subject "ao-3"} operator))
    (println (approve! actor "t6"))

    (println "== delivery/dispatch ao-3 (credit not cleared -> HARD hold) ==")
    (println (exec-op actor "t7" {:op :delivery/dispatch :subject "ao-3"} operator))

    (println "== biosecurity/verify ao-4 (escalates -- human approves; sets up the contract-missing test) ==")
    (println (exec-op actor "t8" {:op :biosecurity/verify :subject "ao-4"} operator))
    (println (approve! actor "t8"))

    (println "== delivery/dispatch ao-4 (no contract-terms on file -> HARD hold) ==")
    (println (exec-op actor "t9" {:op :delivery/dispatch :subject "ao-4"} operator))

    (println "== biosecurity/verify ao-5 (escalates -- human approves; sets up the sanctions test) ==")
    (println (exec-op actor "t10" {:op :biosecurity/verify :subject "ao-5"} operator))
    (println (approve! actor "t10"))

    (println "== delivery/dispatch ao-5 (sanctions screening not passed -> HARD hold) ==")
    (println (exec-op actor "t11" {:op :delivery/dispatch :subject "ao-5"} operator))

    (println "== biosecurity/verify ao-6 (grain; escalates -- human approves; sets up the phytosanitary-certificate-missing test) ==")
    (println (exec-op actor "t12" {:op :biosecurity/verify :subject "ao-6"} operator))
    (println (approve! actor "t12"))

    (println "== delivery/dispatch ao-6 (no phytosanitary certificate on file -> HARD hold) ==")
    (println (exec-op actor "t13" {:op :delivery/dispatch :subject "ao-6"} operator))

    (println "== order/intake ao-7 (JPN, livestock, clean) ==")
    (println (exec-op actor "t14" {:op :order/intake :subject "ao-7"
                                   :patch {:id "ao-7" :counterparty "Golden Hills Livestock Co"}} operator))

    (println "== biosecurity/verify ao-7 (livestock; escalates -- human approves) ==")
    (println (exec-op actor "t15" {:op :biosecurity/verify :subject "ao-7"} operator))
    (println (approve! actor "t15"))

    (println "== delivery/dispatch ao-7 (livestock, clean -- always escalates) ==")
    (let [r (exec-op actor "t16" {:op :delivery/dispatch :subject "ao-7"} operator)]
      (println r)
      (println "-- human trading supervisor approves --")
      (println (approve! actor "t16")))

    (println "== invoice/settle ao-7 (livestock, clean -- always escalates) ==")
    (let [r (exec-op actor "t17" {:op :invoice/settle :subject "ao-7"} operator)]
      (println r)
      (println "-- human trading supervisor approves --")
      (println (approve! actor "t17")))

    (println "== biosecurity/verify ao-8 (livestock; escalates -- human approves; sets up the animal-health-certificate-missing test) ==")
    (println (exec-op actor "t18" {:op :biosecurity/verify :subject "ao-8"} operator))
    (println (approve! actor "t18"))

    (println "== delivery/dispatch ao-8 (no animal-health certificate on file -> HARD hold) ==")
    (println (exec-op actor "t19" {:op :delivery/dispatch :subject "ao-8"} operator))

    (println "== delivery/dispatch ao-1 AGAIN (double-dispatch -> HARD hold) ==")
    (println (exec-op actor "t20" {:op :delivery/dispatch :subject "ao-1"} operator))

    (println "== invoice/settle ao-1 AGAIN (double-invoice -> HARD hold) ==")
    (println (exec-op actor "t21" {:op :invoice/settle :subject "ao-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft agri-delivery records ==")
    (doseq [r (store/delivery-history db)] (println r))

    (println "== draft agri-invoice records ==")
    (doseq [r (store/invoice-history db)] (println r))))
