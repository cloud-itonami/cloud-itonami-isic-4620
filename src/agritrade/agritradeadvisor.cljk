(ns agritrade.agritradeadvisor
  "AgriTradeAdvisor client -- the *contained intelligence node* for the
  agri-wholesale actor.

  It normalizes agri-order intake, drafts a per-jurisdiction, per-
  consignment-kind biosecurity evidence checklist (phytosanitary for
  `:plant`, animal-health for `:animal`), drafts the delivery action,
  and drafts the invoice-settlement action. CRITICAL: it is a smart-but-
  untrusted advisor. It returns a *proposal* (with a rationale + the
  fields it cited), never a committed record or a real dispatch/
  settlement. Every output is censored downstream by `agritrade.governor`
  before anything touches the SSoT, and `:delivery/dispatch`/
  `:invoice/settle` proposals NEVER auto-commit at any phase -- see
  README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :delivery/dispatch | :invoice/settle | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [kotoba.lang.text :as str]
            [agritrade.facts :as facts]
            [agritrade.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the order-id, counterparty, jurisdiction, consignment
  kind or any physical/commercial value. High confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "農畜産物卸売オーダー記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :order/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- verify-biosecurity
  "Per-jurisdiction, per-consignment-kind biosecurity evidence checklist
  draft. `:no-spec?` injects the failure mode we must defend against:
  proposing a checklist for a jurisdiction with NO official spec-basis
  in `agritrade.facts` -- the Agri Trading Governor must reject this
  (never invent a jurisdiction's requirements). The kind (`:plant` |
  `:animal`) comes from the order itself, never from advisor judgment:
  the advisor does not get to decide which regulatory regime applies to
  a consignment."
  [db {:keys [subject no-spec?]}]
  (let [ao (store/agri-order db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction ao))
        kind (:consignment-kind ao)
        sb (facts/spec-basis iso3 kind)]
    (if (nil? sb)
      {:summary    (str iso3 "/" kind " の公式spec-basisが見つかりません")
       :rationale  "agritrade.facts に未登録の法域/品目区分。要件を推測で作らない。"
       :cites      []
       :effect     :biosecurity-assessment/set
       :value      {:jurisdiction iso3 :consignment-kind kind :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 "/" (name kind) " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :biosecurity-assessment/set
       :value      {:jurisdiction iso3
                    :consignment-kind kind
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- propose-delivery
  "Draft the actual DELIVERY action -- dispatching real grain, feed,
  seed, fibre or live animals to a counterparty out of the elevator/
  store or off the livestock yard. ALWAYS `:stake :delivery/dispatch`
  -- this is a REAL-WORLD act (an autonomous elevator conveyance robot
  or livestock loadout gate physically performs the loadout, or an
  operator does), never a draft the actor may auto-run. See README
  `Actuation`: no phase ever adds this op to a phase's `:auto` set
  (`agritrade.phase`); the governor also always escalates on
  `:delivery/dispatch`. Two independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [ao (store/agri-order db subject)
        kind (:consignment-kind ao)
        credit-ok? (and ao (true? (:credit-cleared? ao)))
        contract-ok? (and ao (some? (:contract-terms ao))
                          (not= "" (:contract-terms ao)))
        cert-ok? (and ao (case kind
                            :plant (true? (:phytosanitary-certificate? ao))
                            :animal (true? (:animal-health-certificate? ao))
                            false))
        sanctions-ok? (and ao (true? (:sanctions-screened? ao)))]
    {:summary    (str subject " 向け出荷提案"
                      (when ao (str " (counterparty=" (:counterparty ao) ", kind=" (name kind) ")")))
     :rationale  (if ao
                   (str "credit-cleared?=" credit-ok?
                        " contract-on-file?=" contract-ok?
                        " biosecurity-certificate-on-file?=" cert-ok?
                        " sanctions-screened?=" sanctions-ok?)
                   "agri-orderが見つかりません")
     :cites      (if ao [subject] [])
     :effect     :order/mark-dispatched
     :value      {:agri-order-id subject}
     :stake      :delivery/dispatch
     :confidence (if (and credit-ok? contract-ok? cert-ok? sanctions-ok?) 0.9 0.3)}))

(defn- propose-invoice
  "Draft the actual INVOICE-SETTLEMENT action -- settling a real agri-
  wholesale invoice (the money side of the trade, custody/financial
  transfer). ALWAYS `:stake :invoice/settle` -- this is a REAL-WORLD act
  (real money moves between counterparty and trader), never a draft the
  actor may auto-run. See README `Actuation`: no phase ever adds this
  op to a phase's `:auto` set (`agritrade.phase`); the governor also
  always escalates on `:invoice/settle`. Two independent layers agree,
  deliberately."
  [db {:keys [subject]}]
  (let [ao (store/agri-order db subject)
        dispatched? (and ao (:dispatched? ao))
        sanctions-ok? (and ao (true? (:sanctions-screened? ao)))]
    {:summary    (str subject " 向け請求提案"
                      (when ao (str " (counterparty=" (:counterparty ao) ")")))
     :rationale  (if ao
                   (str "dispatched?=" dispatched?
                        " sanctions-screened?=" sanctions-ok?)
                   "agri-orderが見つかりません")
     :cites      (if ao [subject] [])
     :effect     :order/mark-invoiced
     :value      {:agri-order-id subject}
     :stake      :invoice/settle
     :confidence (if (and dispatched? sanctions-ok?) 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :order/intake       (normalize-intake db request)
    :biosecurity/verify  (verify-biosecurity db request)
    :delivery/dispatch  (propose-delivery db request)
    :invoice/settle     (propose-invoice db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは農畜産物卸売事業者の出荷・請求エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。"
       "説明や前置きは一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:order/upsert|:biosecurity-assessment/set|:order/mark-dispatched|"
       ":order/mark-invoiced) "
       ":stake(:delivery/dispatch か :invoice/settle か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域・品目区分の植物検疫・家畜衛生・制裁要件を絶対に創作"
       "してはいけません。spec-basisが無い場合は :cites を空にし confidence を上げない"
       "こと。植物検疫証明書と家畜衛生証明書は別物であり、荷口の consignment-kind"
       "(:plant/:animal) が要求するものと違う証明書を代わりに使ってはいけません。"
       "取引先信用審査・契約有無・制裁スクリーニングの状態を偽って報告してはいけません。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :biosecurity/verify {:agri-order (store/agri-order st subject)}
    :delivery/dispatch  {:agri-order (store/agri-order st subject)}
    :invoice/settle     {:agri-order (store/agri-order st subject)}
    {:agri-order (store/agri-order st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Agri Trading Governor
  escalates/holds -- an LLM hiccup can never auto-dispatch a
  consignment or auto-settle an invoice."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :agritradeadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
