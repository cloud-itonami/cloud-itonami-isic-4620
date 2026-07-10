(ns agritrade.registry
  "Pure-function delivery + invoice record construction -- an
  append-only agri-wholesale book-of-record draft.

  Like the fuel-wholesale sibling's own registry, this vertical's Agri
  Trading Governor needs NO registry range-check functions at all: its
  domain checks (credit-uncleared, contract-missing, phytosanitary-
  certificate-missing, animal-health-certificate-missing,
  counterparty-sanctions-flag-unresolved) are direct entity boolean
  reads in `agritrade.governor`, off dedicated `:credit-cleared?` /
  `:contract-terms` / `:phytosanitary-certificate?` /
  `:animal-health-certificate?` / `:sanctions-screened?` facts on the
  `agri-order` record. So this namespace is RECORD CONSTRUCTION ONLY --
  no pure range checks to host here.

  Like every sibling actor's registry, there is no single international
  reference-number standard for a grain/feed/livestock delivery or
  invoice record -- every operator/jurisdiction assigns its own
  reference format. This namespace does NOT invent one beyond a
  jurisdiction-scoped sequence number; it validates the record's
  required fields, the same honest, non-fabricating discipline
  `agritrade.facts` uses.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real grain-elevator/livestock-yard/ERP system. It builds
  the RECORD an operator would keep, not the act of dispatching real
  grain, feed, seed, fibre or live animals, or settling a real invoice
  itself (that is `agritrade.operation`'s `:delivery/dispatch`/
  `:invoice/settle`, always human-gated -- see README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is
  the operator's act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

;; ----------------------------- record construction -----------------------------

(defn register-delivery-record
  "Validate + construct the AGRI-DELIVERY registration DRAFT -- the
  operator's own legal act of dispatching real grain, feed, seed,
  fibre or live animals to a counterparty (out of the elevator/store
  or off the livestock yard). Pure function -- does not touch any real
  elevator/yard/ERP system; it builds the RECORD an operator would
  keep. `agritrade.governor` independently re-verifies the
  counterparty's credit-clearance, contract-on-file, biosecurity-
  certificate and sanctions-screening ground truth, and blocks a
  double-delivery of the same agri-order, before this is ever allowed
  to commit."
  [agri-order-id jurisdiction sequence]
  (when-not (and agri-order-id (not= agri-order-id ""))
    (throw (ex-info "agri-delivery: agri_order_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "agri-delivery: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "agri-delivery: sequence must be >= 0" {})))
  (let [delivery-number (str (str/upper-case jurisdiction) "-DELIVERY-" (zero-pad sequence 6))
        record {"record_id" delivery-number
                "kind" "agri-delivery-draft"
                "agri_order_id" agri-order-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "delivery_number" delivery-number
     "certificate" (unsigned-certificate "AgriDelivery" delivery-number delivery-number)}))

(defn register-invoice-record
  "Validate + construct the AGRI-INVOICE registration DRAFT -- the
  operator's own legal act of settling a real agri-wholesale invoice
  (the money side of the trade, custody/financial transfer). Pure
  function -- does not touch any real billing or accounts-receivable
  system; it builds the RECORD an operator would keep. `agritrade.
  governor` independently re-verifies the sanctions-screening and
  evidence-completeness ground truth, and blocks a double-invoice of
  the same agri-order, before this is ever allowed to commit."
  [agri-order-id jurisdiction sequence]
  (when-not (and agri-order-id (not= agri-order-id ""))
    (throw (ex-info "agri-invoice: agri_order_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "agri-invoice: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "agri-invoice: sequence must be >= 0" {})))
  (let [invoice-number (str (str/upper-case jurisdiction) "-INVOICE-" (zero-pad sequence 6))
        record {"record_id" invoice-number
                "kind" "agri-invoice-draft"
                "agri_order_id" agri-order-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "invoice_number" invoice-number
     "certificate" (unsigned-certificate "AgriInvoice" invoice-number invoice-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
