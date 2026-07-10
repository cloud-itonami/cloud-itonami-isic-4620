(ns agritrade.governor
  "Agri Trading Governor -- the independent compliance layer that earns
  the AgriTradeAdvisor the right to commit. The LLM has no notion of
  jurisdictional phytosanitary / animal-health / sanctions law, whether
  a counterparty's credit has actually been cleared, whether contract
  terms are actually on file, whether a REAL phytosanitary certificate
  or animal-health certificate has actually been issued for THIS
  consignment, whether OFAC / equivalent sanctions screening has
  actually been passed, or when an act stops being a draft and becomes
  a real dispatch of grain/feed/seed/fibre/live animals or a real
  invoice settlement, so this MUST be a separate system able to
  *reject* a proposal and fall back to HOLD.

  Like the fuel-wholesale sibling's own governor, this agricultural-
  raw-materials / live-animal wholesale vertical has NO pre-existing
  agri-trading capability library to delegate to -- so the domain
  checks (credit-clearance, contract-on-file, biosecurity-certificate,
  sanctions-screening) are direct entity boolean reads off the
  `agri-order` record, evaluated directly here, NOT delegated to a
  separate library's validated function.

  `:itonami.blueprint/governor` is `:agri-trading-governor`, grep-
  verified UNIQUE fleet-wide -- no naming-collision precedent
  question, a fresh independent build following the SAME governed-
  actor architecture (langgraph StateGraph + independent Governor +
  Phase 0->3 rollout) established by `cloud-itonami-isic-6511` and
  applied by the fuel-wholesale (`cloud-itonami-isic-4671`),
  general-trading (`cloud-itonami-isic-4690`) and commission-brokerage
  (`cloud-itonami-isic-4610`) siblings.

  CRITICAL STRUCTURAL DIFFERENCE from every sibling above: ISIC 4620
  covers TWO consignment kinds under ONE classification code --
  agricultural raw materials (grain/feed/seed/fibre, `:consignment-kind
  :plant`) AND live animals (livestock, `:consignment-kind :animal`) --
  governed by GENUINELY DIFFERENT biosecurity statutes (phytosanitary
  law vs. animal/veterinary health law), sometimes even different
  agencies within the same country (contrast the general-trading
  sibling, whose export-control check applies UNIFORMLY regardless of
  which unrelated commodity category an order happens to hold). This is
  why the certificate check below is modeled as TWO separate HARD
  checks, `phytosanitary-certificate-missing-violations` and
  `animal-health-certificate-missing-violations`, each gated on
  `:consignment-kind` rather than one generic 'certificate-missing'
  check -- a single generic rule keyword would be ambiguous on the
  audit ledger about WHICH regime actually failed, and would blur two
  regulatory regimes real biosecurity law keeps genuinely separate. See
  `agritrade.facts` for the per-jurisdiction, per-kind spec-basis
  catalog this pair of checks is grounded in.

  Seven checks, in priority order, ALL HARD violations: a human
  approver CANNOT override them. The confidence/actuation gate is
  SOFT: it asks a human to look (low confidence / actuation), and the
  human may approve -- but see `agritrade.phase`: for `:stake
  :delivery/dispatch`/`:invoice/settle` (a real dispatch or invoice
  settlement) NO phase ever allows auto-commit either. Two independent
  layers agree that actuation is always a human call.

    1. Spec-basis                  -- did the jurisdiction proposal cite
                                       an OFFICIAL source
                                       (`agritrade.facts`), or invent one?
    2. Evidence incomplete         -- for `:delivery/dispatch`/
                                       `:invoice/settle`, has the
                                       jurisdiction actually been
                                       verified with a full biosecurity
                                       evidence checklist on file, FOR
                                       THIS CONSIGNMENT'S KIND?
    3. Credit uncleared            -- for `:delivery/dispatch`, the
                                       counterparty's credit has NOT been
                                       cleared (the leasing collateral-
                                       coverage discipline, applied to
                                       counterparty credit). Evaluated
                                       before dispatch.
    4. Contract missing            -- for `:delivery/dispatch`, no
                                       contract-terms are on file for the
                                       order. Evaluated before dispatch.
    5. Phytosanitary certificate
       missing                       -- for `:delivery/dispatch`, WHEN
                                       `:consignment-kind :plant`, no
                                       phytosanitary certificate is on
                                       file for the consignment -- a
                                       grain/feed/seed/fibre cargo never
                                       leaves the elevator/store without
                                       plant-health clearance. THIS check
                                       has no analog in the fuel-
                                       wholesale, general-trading or
                                       commission-brokerage siblings: it
                                       is this vertical's own defining
                                       regulatory content, not a rename
                                       of an existing check.
    6. Animal-health certificate
       missing                       -- for `:delivery/dispatch`, WHEN
                                       `:consignment-kind :animal`, no
                                       animal-health/veterinary
                                       certificate is on file for the
                                       consignment -- a livestock
                                       consignment never leaves the yard
                                       without veterinary health
                                       clearance. Deliberately a SEPARATE
                                       check from #5, not a shared
                                       generic 'certificate-missing'
                                       rule -- see namespace docstring.
    7. Counterparty sanctions flag
       unresolved                    -- for `:delivery/dispatch` and
                                       `:invoice/settle`, the counterparty
                                       has NOT passed OFAC / equivalent
                                       sanctions screening -- a HARD,
                                       un-overridable hold. Evaluated
                                       UNCONDITIONALLY at both actuation
                                       ops.
    8. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:delivery/dispatch`/
                                       `:invoice/settle` (REAL acts)
                                       -> escalate.

  Two more guards, double-delivery/double-invoice prevention, are
  enforced but NOT listed as numbered HARD checks above because they
  need no upstream comparison at all -- `already-dispatched-violations`/
  `already-invoiced-violations` refuse to dispatch/invoice the SAME
  agri-order twice, off dedicated `:dispatched?`/`:invoiced?` facts
  (never a `:status` value) -- the SAME 'check a dedicated boolean, not
  status' discipline every prior governor's guards establish, informed
  by `cloud-itonami-isic-6492`'s status-lifecycle bug (ADR-2607071320)."
  (:require [agritrade.facts :as facts]
            [agritrade.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Dispatching real grain/feed/seed/fibre or live animals to a
  counterparty (product/animals leaving the elevator, store or yard)
  and settling a real agri-wholesale invoice (real money moving between
  counterparty and trader) are the two real-world actuation events this
  actor performs -- a two-member set, matching every sibling's own
  dual-actuation shape."
  #{:delivery/dispatch :invoice/settle})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:biosecurity/verify` (or `:delivery/dispatch`/`:invoice/settle`)
  proposal with no spec-basis citation is a HARD violation -- never
  invent a jurisdiction's phytosanitary / animal-health / sanctions
  requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:biosecurity/verify :delivery/dispatch :invoice/settle} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:delivery/dispatch`/`:invoice/settle`, the jurisdiction's
  required biosecurity evidence (credit-clearance record, contract/PO,
  sanctions-screening record, PLUS the kind-specific certificate --
  phytosanitary for `:plant`, animal-health for `:animal`) must
  actually be satisfied -- do not trust the advisor's self-reported
  confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:delivery/dispatch :invoice/settle} op)
    (let [ao (store/agri-order st subject)
          assessment (store/assessment-of st subject)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      (:jurisdiction ao) (:consignment-kind ao) (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail "法域・品目区分の必要書類(信用審査記録/契約書またはPO/制裁スクリーニング記録/検疫証明書)が充足していない状態での提案"}]))))

(defn- credit-uncleared-violations
  "For `:delivery/dispatch`, refuses to dispatch to a counterparty
  whose credit has NOT been cleared -- counterparty credit not cleared
  (the leasing collateral-coverage discipline, applied to counterparty
  credit). Evaluated ahead of any physical loadout."
  [{:keys [op subject]} st]
  (when (= op :delivery/dispatch)
    (let [ao (store/agri-order st subject)]
      (when (not (true? (:credit-cleared? ao)))
        [{:rule :credit-uncleared
          :detail (str subject " の取引先信用審査(credit-clearance)が未了 -- 出荷提案は進められない")}]))))

(defn- contract-missing-violations
  "For `:delivery/dispatch`, refuses to dispatch when no contract-terms
  are on file for the order."
  [{:keys [op subject]} st]
  (when (= op :delivery/dispatch)
    (let [ao (store/agri-order st subject)]
      (when (or (nil? (:contract-terms ao)) (= "" (:contract-terms ao)))
        [{:rule :contract-missing
          :detail (str subject " に契約条項(contract-terms)の記録が無い -- 出荷提案は進められない")}]))))

(defn- phytosanitary-certificate-missing-violations
  "For `:delivery/dispatch`, WHEN `:consignment-kind :plant`, refuses to
  dispatch a grain/feed/seed/fibre consignment with no phytosanitary
  certificate on file. This is the check with NO analog in the fuel-
  wholesale, general-trading or commission-brokerage siblings' governors
  -- it is this vertical's own defining biosecurity content for plant
  consignments, deliberately SEPARATE from
  `animal-health-certificate-missing-violations` (see namespace
  docstring): grain and livestock are genuinely different regulatory
  regimes, and a single generic 'certificate-missing' rule keyword
  would blur which regime actually failed on the audit ledger."
  [{:keys [op subject]} st]
  (when (= op :delivery/dispatch)
    (let [ao (store/agri-order st subject)]
      (when (and (= :plant (:consignment-kind ao))
                 (not (true? (:phytosanitary-certificate? ao))))
        [{:rule :phytosanitary-certificate-missing
          :detail (str subject " (植物/穀物系荷口)の植物検疫証明書(phytosanitary certificate)が未取得 -- 出荷提案は進められない")}]))))

(defn- animal-health-certificate-missing-violations
  "For `:delivery/dispatch`, WHEN `:consignment-kind :animal`, refuses
  to dispatch a livestock consignment with no animal-health/veterinary
  certificate on file. This is the check with NO analog in the fuel-
  wholesale, general-trading or commission-brokerage siblings' governors
  -- it is this vertical's own defining biosecurity content for live-
  animal consignments, deliberately SEPARATE from
  `phytosanitary-certificate-missing-violations` (see namespace
  docstring): a live-animal consignment's regulatory gate is
  transmissible-disease control (foot-and-mouth, avian influenza,
  African swine fever), not plant-pest quarantine, even though both
  arrive at the same ISIC 4620 wholesale order."
  [{:keys [op subject]} st]
  (when (= op :delivery/dispatch)
    (let [ao (store/agri-order st subject)]
      (when (and (= :animal (:consignment-kind ao))
                 (not (true? (:animal-health-certificate? ao))))
        [{:rule :animal-health-certificate-missing
          :detail (str subject " (家畜荷口)の家畜衛生証明書(animal health certificate)が未取得 -- 出荷提案は進められない")}]))))

(defn- counterparty-sanctions-flag-unresolved-violations
  "For `:delivery/dispatch` and `:invoice/settle`, an unresolved
  sanctions-screening flag -- the counterparty has NOT passed OFAC /
  equivalent sanctions screening -- is a HARD, un-overridable hold.
  Evaluated UNCONDITIONALLY at both actuation ops: neither product nor
  animals ship, nor does money settle, against an unscreened
  counterparty."
  [{:keys [op subject]} st]
  (when (contains? #{:delivery/dispatch :invoice/settle} op)
    (let [ao (store/agri-order st subject)]
      (when (not (true? (:sanctions-screened? ao)))
        [{:rule :counterparty-sanctions-flag-unresolved
          :detail (str subject " の取引先制裁スクリーニング(OFAC等)が未了 -- 出荷・請求提案は進められない")}]))))

(defn- already-dispatched-violations
  "For `:delivery/dispatch`, refuses to dispatch the SAME agri-order
  twice, off a dedicated `:dispatched?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :delivery/dispatch)
    (when (store/agri-order-already-dispatched? st subject)
      [{:rule :already-dispatched
        :detail (str subject " は既に出荷済み")}])))

(defn- already-invoiced-violations
  "For `:invoice/settle`, refuses to settle the SAME agri-order's
  invoice twice, off a dedicated `:invoiced?` fact (never a `:status`
  value)."
  [{:keys [op subject]} st]
  (when (= op :invoice/settle)
    (when (store/agri-order-already-invoiced? st subject)
      [{:rule :already-invoiced
        :detail (str subject " は既に請求済み")}])))

(defn check
  "Censors an AgriTradeAdvisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (credit-uncleared-violations request st)
                           (contract-missing-violations request st)
                           (phytosanitary-certificate-missing-violations request st)
                           (animal-health-certificate-missing-violations request st)
                           (counterparty-sanctions-flag-unresolved-violations request st)
                           (already-dispatched-violations request st)
                           (already-invoiced-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
