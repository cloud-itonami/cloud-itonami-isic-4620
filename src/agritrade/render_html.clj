(ns agritrade.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2: this repo previously shipped a
  HAND-WRITTEN `docs/samples/operator-console.html` (raw hex colours, no
  generator anywhere in `src/`), so nothing in it was traceable to a
  real run. This namespace replaces it with a page driven end-to-end by
  the REAL actor stack -- `agritrade.operation` -> `agritrade.governor`
  -> `agritrade.store` -- through `langgraph.graph/run*`.

  PROVENANCE OF EVERY SUBJECT ID. The scenario below uses only
  `ao-1`..`ao-8`, which are exactly the eight agri-orders seeded by
  `agritrade.store/demo-data` (via `store/seed-db`). No id is invented.
  This repo's own `agritrade.sim` demo driver (`clojure -M:dev:run`) was
  run BEFORE this file was written to confirm its ids are the seeded
  ones and to read the real ledger shape off it; the scenario here is a
  superset of `sim`'s (it adds the two dispositions `sim` never
  reaches -- see below), not a copy of it.

  WHAT EACH SUBJECT EXERCISES (all eight seeded orders, every one of the
  Agri Trading Governor's nine HARD rules, and all three fact types that
  actually reach the ledger):

    ao-1  plant/grain, JPN, clean -- FULL CLEAN LIFECYCLE:
          `:order/intake` (phase-3 auto-commit, the only auto op) ->
          `:biosecurity/verify` (escalates, approved) ->
          `:delivery/dispatch` (escalates, approved) ->
          `:invoice/settle` (escalates, approved). Then re-attempted:
          a second dispatch -> HARD `:already-dispatched`, a second
          settle -> HARD `:already-invoiced`.
    ao-7  live-animal/livestock, JPN, clean -- SECOND FULL CLEAN
          LIFECYCLE, on the animal biosecurity regime (家畜伝染病予防法)
          rather than the plant one, which is this vertical's defining
          structural split.
    ao-3  credit not cleared -- also carries the HUMAN-REJECTION path:
          its first `:biosecurity/verify` is REJECTED by the approver
          (ledger fact `:approval-rejected`, basis `:approver-rejected`
          -- a human decision, NOT a governor HARD hold), the actor
          re-proposes, the approver accepts, and the subsequent
          dispatch then HARD-holds on `:credit-uncleared`.
    ao-2  ATL, a deliberately unregistered jurisdiction -- HARD
          `:no-spec-basis` on verify, and then the cascade that follows
          from it: with no assessment on file the dispatch HARD-holds on
          `:evidence-incomplete`. (`agritrade.sim` exercises neither of
          these two together; `:evidence-incomplete` is not exercised by
          `sim` at all.)
    ao-4  no contract-terms on file -- HARD `:contract-missing`.
    ao-5  sanctions screening not passed -- HARD
          `:counterparty-sanctions-flag-unresolved`.
    ao-6  grain with no phytosanitary certificate -- HARD
          `:phytosanitary-certificate-missing`.
    ao-8  livestock with no animal-health certificate -- HARD
          `:animal-health-certificate-missing`.

  DETERMINISM. Nothing in this actor is time- or random-dependent:
  `agritrade.agritradeadvisor/mock-advisor` is a pure function of the
  store, `agritrade.registry` numbers records off a jurisdiction-scoped
  sequence, and no fact carries a timestamp. `store/all-agri-orders`
  sorts by `:id` and the ledger is append-ordered, so two consecutive
  runs are byte-identical (verify by diffing two runs).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [agritrade.store :as store]
            [agritrade.operation :as op]
            [langgraph.graph :as g]))

;; The operator identity `agritrade.sim` uses -- a phase-3 trading
;; supervisor. Phase 3 is `agritrade.phase/default-phase`.
(def ^:private operator
  {:actor-id "op-1" :actor-role :trading-supervisor :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn- reject! [actor tid]
  (g/run* actor {:approval {:status :rejected :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Drives a fresh `store/seed-db` through the scenario documented in the
  namespace docstring and returns the resulting store. Every field the
  renderer below reads is real governor/store output -- nothing is
  hand-typed into the page."
  []
  (let [db (store/seed-db)
        actor (op/build db)]

    ;; --- ao-1: full clean lifecycle (plant/grain, JPN) ---------------
    (exec! actor "t01" {:op :order/intake :subject "ao-1"
                        :patch {:id "ao-1" :counterparty "Akita Grain Traders Co"}})

    (exec! actor "t02" {:op :biosecurity/verify :subject "ao-1"})
    (approve! actor "t02")

    (exec! actor "t03" {:op :delivery/dispatch :subject "ao-1"})
    (approve! actor "t03")

    (exec! actor "t04" {:op :invoice/settle :subject "ao-1"})
    (approve! actor "t04")

    ;; --- ao-7: full clean lifecycle (live animals, JPN) --------------
    (exec! actor "t05" {:op :order/intake :subject "ao-7"
                        :patch {:id "ao-7" :counterparty "Golden Hills Livestock Co"}})

    (exec! actor "t06" {:op :biosecurity/verify :subject "ao-7"})
    (approve! actor "t06")

    (exec! actor "t07" {:op :delivery/dispatch :subject "ao-7"})
    (approve! actor "t07")

    (exec! actor "t08" {:op :invoice/settle :subject "ao-7"})
    (approve! actor "t08")

    ;; --- ao-3: human rejection, re-proposal, then a HARD hold --------
    ;; The approver declines the first checklist draft -> the ONLY
    ;; ledger fact type this actor writes that is not a commit and not a
    ;; governor hold. The actor re-proposes on a fresh thread and the
    ;; approver accepts.
    (exec! actor "t09" {:op :biosecurity/verify :subject "ao-3"})
    (reject! actor "t09")

    (exec! actor "t10" {:op :biosecurity/verify :subject "ao-3"})
    (approve! actor "t10")

    (exec! actor "t11" {:op :delivery/dispatch :subject "ao-3"})

    ;; --- ao-2: unregistered jurisdiction, and the cascade ------------
    (exec! actor "t12" {:op :biosecurity/verify :subject "ao-2"})
    (exec! actor "t13" {:op :delivery/dispatch :subject "ao-2"})

    ;; --- ao-4 / ao-5 / ao-6 / ao-8: one HARD rule each ---------------
    (exec! actor "t14" {:op :biosecurity/verify :subject "ao-4"})
    (approve! actor "t14")
    (exec! actor "t15" {:op :delivery/dispatch :subject "ao-4"})

    (exec! actor "t16" {:op :biosecurity/verify :subject "ao-5"})
    (approve! actor "t16")
    (exec! actor "t17" {:op :delivery/dispatch :subject "ao-5"})

    (exec! actor "t18" {:op :biosecurity/verify :subject "ao-6"})
    (approve! actor "t18")
    (exec! actor "t19" {:op :delivery/dispatch :subject "ao-6"})

    (exec! actor "t20" {:op :biosecurity/verify :subject "ao-8"})
    (approve! actor "t20")
    (exec! actor "t21" {:op :delivery/dispatch :subject "ao-8"})

    ;; --- ao-1 again: the two double-actuation guards -----------------
    (exec! actor "t22" {:op :delivery/dispatch :subject "ao-1"})
    (exec! actor "t23" {:op :invoice/settle :subject "ao-1"})

    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- kw->s
  "Basis entries are a mix of keywords (`:id`, `:credit-uncleared`) and
  strings (a legal-basis citation, a provenance URL)."
  [v]
  (if (keyword? v) (name v) (str v)))

(defn- basis-str [basis]
  (str/join " · " (map kw->s basis)))

(defn- bool-cell [v]
  (if (true? v)
    "<span class=\"ok\">true</span>"
    "<span class=\"critical\">false</span>"))

(defn- present-cell [v]
  (if (and (some? v) (not= "" v))
    (str "<span class=\"ok\">" (esc v) "</span>")
    "<span class=\"critical\">none on file</span>"))

(defn- last-fact-for [ledger id]
  (last (filter #(= (:subject %) id) ledger)))

(defn- status-cell
  "Branches ONLY on the fact types `agritrade.operation` actually
  APPENDS TO THE LEDGER. The `:commit` node writes `:committed`; the
  `:hold` node writes whichever of `:governor-hold` / `:approval-
  rejected` the run produced. `:approval-requested` and
  `:approval-granted` are written to the in-memory `:audit` channel and
  NEVER reach `store/ledger`, so branching on them here would be dead
  code -- do not add them."
  [ledger id]
  (let [f (last-fact-for ledger id)]
    (case (:t f)
      nil "<span class=\"muted\">no activity</span>"
      :committed (str "<span class=\"ok\">committed · " (esc (kw->s (:op f))) "</span>")
      :governor-hold
      (str "<span class=\"critical\">HARD hold · "
           (esc (kw->s (or (-> f :violations first :rule) :unknown))) "</span>")
      :approval-rejected
      (str "<span class=\"warn\">rejected by approver · " (esc (kw->s (:op f))) "</span>")
      (str "<span class=\"muted\">" (esc (kw->s (:t f))) "</span>"))))

(defn- order-row [{:keys [id order-id consignment-kind commodity quantity unit
                          price counterparty jurisdiction]}]
  (str "        <tr><td><code>" (esc id) "</code></td><td>" (esc order-id) "</td>"
       "<td>" (esc (kw->s consignment-kind)) "</td><td>" (esc commodity) "</td>"
       "<td class=\"num\">" (esc quantity) " " (esc unit) "</td>"
       "<td class=\"amt\">" (esc price) "</td>"
       "<td>" (esc counterparty) "</td><td>" (esc jurisdiction) "</td></tr>"))

(defn- actuation-row [ledger {:keys [id dispatched? dispatch-number invoiced? invoice-number]}]
  (str "        <tr><td><code>" (esc id) "</code></td>"
       "<td>" (bool-cell dispatched?) "</td>"
       "<td>" (if dispatch-number (str "<code>" (esc dispatch-number) "</code>") "<span class=\"muted\">—</span>") "</td>"
       "<td>" (bool-cell invoiced?) "</td>"
       "<td>" (if invoice-number (str "<code>" (esc invoice-number) "</code>") "<span class=\"muted\">—</span>") "</td>"
       "<td>" (status-cell ledger id) "</td></tr>"))

(defn- ground-truth-row
  "The exact entity booleans `agritrade.governor` reads -- rendered raw,
  as stored. Which of the two certificate booleans actually gates a
  dispatch is decided by the governor off `:consignment-kind`; this
  table does not second-guess it, it just shows the ground truth."
  [{:keys [id consignment-kind credit-cleared? contract-terms sanctions-screened?
           phytosanitary-certificate? animal-health-certificate?]}]
  (str "        <tr><td><code>" (esc id) "</code></td>"
       "<td>" (esc (kw->s consignment-kind)) "</td>"
       "<td>" (bool-cell credit-cleared?) "</td>"
       "<td>" (present-cell contract-terms) "</td>"
       "<td>" (bool-cell sanctions-screened?) "</td>"
       "<td>" (bool-cell phytosanitary-certificate?) "</td>"
       "<td>" (bool-cell animal-health-certificate?) "</td></tr>"))

(defn- assessment-row
  "One committed `:biosecurity-assessment/set` payload, straight out of
  `store/assessment-of`. `:approved-by` is on the payload because the
  `:request-approval` node stamps it there before the commit."
  [db {:keys [id]}]
  (let [a (store/assessment-of db id)]
    (str "        <tr><td><code>" (esc id) "</code></td>"
         (if (nil? a)
           (str "<td colspan=\"5\"><span class=\"critical\">no assessment on file</span>"
                " <span class=\"muted\">— cannot pass the evidence-completeness check</span></td>")
           (str "<td>" (esc (:jurisdiction a)) " / " (esc (kw->s (:consignment-kind a))) "</td>"
                "<td>" (esc (:legal-basis a)) "</td>"
                "<td>" (esc (:spec-basis a)) "</td>"
                "<td>" (esc (count (:checklist a))) " · "
                (esc (str/join " · " (:checklist a))) "</td>"
                "<td>" (if-let [by (:approved-by a)]
                         (str "<span class=\"ok\">" (esc by) "</span>")
                         "<span class=\"muted\">—</span>") "</td>"))
         "</tr>")))

(defn- hold-row [{:keys [op subject violations confidence]}]
  (let [{:keys [rule detail]} (first violations)]
    (str "        <tr><td><code>" (esc (kw->s op)) "</code></td>"
         "<td><code>" (esc subject) "</code></td>"
         "<td><span class=\"critical\">HARD hold</span></td>"
         "<td><code>" (esc (kw->s rule)) "</code></td>"
         "<td>" (esc detail) "</td>"
         "<td class=\"num\">" (esc confidence) "</td></tr>")))

(defn- ledger-row [i {:keys [t op subject disposition basis]}]
  (str "        <tr><td class=\"num\">" (esc (inc i)) "</td>"
       "<td>" (esc (kw->s t)) "</td>"
       "<td><code>" (esc (kw->s op)) "</code></td>"
       "<td><code>" (esc subject) "</code></td>"
       "<td>" (esc (kw->s disposition)) "</td>"
       "<td>" (esc (basis-str basis)) "</td></tr>"))

(defn- record-row [r]
  (str "        <tr><td><code>" (esc (get r "record_id")) "</code></td>"
       "<td>" (esc (get r "kind")) "</td>"
       "<td><code>" (esc (get r "agri_order_id")) "</code></td>"
       "<td>" (esc (get r "jurisdiction")) "</td>"
       "<td>" (esc (get r "immutable")) "</td></tr>"))

(def ^:private action-gate-rows
  ;; Static description of this actor's own CLOSED op contract, read off
  ;; `agritrade.phase/phases` (phase 3 `:auto` = #{:order/intake}) and
  ;; the numbered HARD checks in `agritrade.governor`'s docstring. This
  ;; is documentation of fixed code, not runtime telemetry, so it is
  ;; legitimately hand-described rather than derived from a live run --
  ;; every OTHER table on this page is derived from the run.
  ["        <tr><td><code>:order/intake</code></td><td><span class=\"ok\">phase-3 auto-commit when governor-clean — the only auto-eligible op (no capital risk yet)</span></td></tr>"
   "        <tr><td><code>:biosecurity/verify</code></td><td><span class=\"warn\">phase-3: human approval (not in any phase's <code>:auto</code> set) · spec-basis citation required</span></td></tr>"
   "        <tr><td><code>:delivery/dispatch</code></td><td><span class=\"warn\">ALWAYS human approval · never auto at any phase · gated on credit-clearance, contract-on-file, the kind-specific biosecurity certificate, sanctions screening, evidence completeness and a double-dispatch guard</span></td></tr>"
   "        <tr><td><code>:invoice/settle</code></td><td><span class=\"warn\">ALWAYS human approval · never auto at any phase · gated on sanctions screening, evidence completeness and a double-invoice guard</span></td></tr>"])

(defn render
  "Renders the whole operator-console document from a store `db` that has
  already been driven by `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        orders (store/all-agri-orders db)
        holds (filter #(= :governor-hold (:t %)) ledger)
        rules (distinct (map #(-> % :violations first :rule) holds))]
    (str
     "<html lang=\"ja\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
     "<title>cloud-itonami-isic-4620 · agri-wholesale operator console</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Wholesale of agricultural raw materials &amp; live animals (ISIC 4620) — Operator Console</h1>\n"
     "</header>\n"
     "<p><span class=\"badge\">read-only sample · governor-gated · delivery dispatch and invoice settlement are always human-approved</span></p>\n"
     "<p class=\"subtitle\">Build-time snapshot generated by <code>agritrade.render-html</code> (<code>clojure -M:dev:render-html</code>) by driving the real actor stack — <code>agritrade.operation</code> → <code>agritrade.governor</code> → <code>agritrade.store</code> — over the eight agri-orders seeded by <code>agritrade.store/demo-data</code>. Every row below is output of that run; nothing on this page is hand-typed except the fixed op/gate contract table.</p>\n"
     "<main>\n"

     "  <section class=\"card\">\n"
     "    <h2>Agri-orders</h2>\n"
     "    <table>\n"
     "      <thead><tr><th>Order</th><th>Reference</th><th>Consignment kind</th><th>Commodity</th><th>Quantity</th><th>Price</th><th>Counterparty</th><th>Jurisdiction</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map order-row orders)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Actuation state &amp; last decision</h2>\n"
     "    <p class=\"muted\">Double-actuation is guarded off the dedicated <code>:dispatched?</code> / <code>:invoiced?</code> booleans, never a <code>:status</code> value. Delivery and invoice reference numbers are assigned by <code>agritrade.registry</code> off a jurisdiction-scoped sequence.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Order</th><th>dispatched?</th><th>Delivery record</th><th>invoiced?</th><th>Invoice record</th><th>Last ledger decision</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map (partial actuation-row ledger) orders)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Governor ground truth</h2>\n"
     "    <p class=\"muted\">The entity facts the Agri Trading Governor reads directly off each <code>agri-order</code> record — it never trusts the advisor's self-report. ISIC 4620 spans two genuinely different biosecurity regimes, so the phytosanitary and animal-health certificates are separate facts and separate HARD rules; which one gates a dispatch follows from the consignment kind.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Order</th><th>Kind</th><th>credit-cleared?</th><th>contract-terms</th><th>sanctions-screened?</th><th>phytosanitary-certificate?</th><th>animal-health-certificate?</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map ground-truth-row orders)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Biosecurity assessments on file</h2>\n"
     "    <p class=\"muted\">Committed <code>:biosecurity-assessment/set</code> payloads, per jurisdiction and consignment kind, each carrying the official citation the proposal was required to ground itself in (<code>agritrade.facts</code>). An order with no assessment on file cannot clear the evidence-completeness check.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Order</th><th>Jurisdiction / kind</th><th>Legal basis</th><th>Spec-basis (official source)</th><th>Evidence checklist</th><th>Approved by</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map (partial assessment-row db) orders)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Action gate (Agri Trading Governor + rollout phase)</h2>\n"
     "    <p class=\"muted\">HARD violations cannot be overridden by an approver. Two independent layers agree that a real dispatch or a real invoice settlement is always a human call: they are absent from every phase's <code>:auto</code> set, and the governor independently marks them high-stakes.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>HARD holds this run</h2>\n"
     "    <p class=\"muted\">Every <code>:governor-hold</code> fact this scenario produced — "
     (esc (count holds)) " holds covering " (esc (count rules))
     " distinct rules. None of these ever reached a human approver.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Order</th><th>Disposition</th><th>Rule</th><th>Detail</th><th>Advisor confidence</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map hold-row holds)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">The append-only decision-fact log — every commit, every governor hold and every approver rejection, in order. This is the whole trail a regulator or a disputed-delivery investigation queries.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>#</th><th>Fact</th><th>Op</th><th>Order</th><th>Disposition</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map-indexed ledger-row ledger)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Draft agri-delivery records</h2>\n"
     "    <p class=\"muted\">Unsigned drafts — the record an operator keeps, not the act itself. Signature is the operator's act, never this actor's.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Record</th><th>Kind</th><th>Order</th><th>Jurisdiction</th><th>Immutable</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map record-row (store/delivery-history db))) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Draft agri-invoice records</h2>\n"
     "    <table>\n"
     "      <thead><tr><th>Record</th><th>Kind</th><th>Order</th><th>Jurisdiction</th><th>Immutable</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map record-row (store/invoice-history db))) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "<footer>\n"
     "  <p>" (esc (count ledger)) " ledger facts · " (esc (count orders))
     " seeded agri-orders · " (esc (count (store/delivery-history db)))
     " delivery drafts · " (esc (count (store/invoice-history db)))
     " invoice drafts. Regenerate with <code>clojure -M:dev:render-html</code>;"
     " the run is deterministic, so the output is byte-identical between runs.</p>\n"
     "</footer>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)]
    (spit out (render db))
    (println "wrote" out
             (str "(" (count (store/ledger db)) " ledger facts, "
                  (count (filter #(= :governor-hold (:t %)) (store/ledger db))) " HARD holds, "
                  (count (store/delivery-history db)) " delivery drafts, "
                  (count (store/invoice-history db)) " invoice drafts)"))))
