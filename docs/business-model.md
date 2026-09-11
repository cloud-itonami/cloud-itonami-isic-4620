# Business Model: Wholesale of Agricultural Raw Materials and Live Animals

## Classification
- Repository: `cloud-itonami-isic-4620`
- ISIC Rev.5: `4620` — wholesale of agricultural raw materials and live animals
- Domain: `upstream/agri-wholesale`
- Social impact: biosecurity, animal welfare, food safety, transparency
- Governor: `:agri-trading-governor`
- License: AGPL-3.0-or-later

## Scope
This actor covers agri-order intake through per-jurisdiction, per-
consignment-kind contract / biosecurity (phytosanitary / animal-health)
/ sanctions regulatory verification, delivery dispatch (grain, feed,
seed, fibre or live animals leaving the elevator/store/yard for a
counterparty), and invoice settlement (the money side of the trade,
custody / financial transfer) for a wholesaler of agricultural raw
materials and live animals. It does **not**, by itself, hold any
agri-wholesale licence, quarantine-inspection authority or operating
authority required to run an agri-wholesale business in a given
jurisdiction, perform the actual physical elevator or livestock
loadout, or judge trading-book economics (route optimization and
trading-book optimization is a follow-up slice, not this R0). Whoever
deploys a live instance supplies the jurisdiction-specific operating
authority, the real elevator-conveyance/livestock-handling equipment
and ERP / accounts-receivable integrations, and bears that
jurisdiction's liability -- the software supplies the governed,
spec-cited, audited execution scaffold so the operator does not have to
build the compliance layer from scratch.

## Customer
- regional grain, feed, seed and fibre wholesalers and elevator operators
- livestock wholesalers, stockyards and auction-adjacent trading desks
- cooperatives and distributors leaving closed agri-trading / ERP SaaS
- counterparties, banks, quarantine authorities and regulators who need
  an auditable, spec-cited trade record

## Offer
- agri-order intake and directory management, across both consignment
  kinds (plant/grain and animal/livestock) in one system
- per-jurisdiction, per-kind contract / biosecurity / sanctions
  regulatory verification with an official spec-basis citation
- delivery (elevator/yard dispatch) gated on full evidence, a credit-
  cleared counterparty, contract-terms on file, the kind-appropriate
  biosecurity certificate (phytosanitary for plant, animal-health for
  animal) and a passed sanctions screen
- invoice settlement (custody / financial transfer) with double-invoice
  prevention
- evidence checklisting (credit-clearance record, contract/PO,
  sanctions-screening record, biosecurity certificate)
- sanctions and credit exception workflows
- role-based access and immutable audit ledger

## Revenue
- self-host setup fee
- managed hosting subscription per trader / elevator / yard
- support retainer with SLA
- ERP and accounts-receivable integration

## The `:agri-trading-governor` Decision Rule

This blueprint's `:itonami.blueprint/governor` is `:agri-trading-
governor`. It is the single authority that stands between "a
consignment could be dispatched to a counterparty" and "it is allowed
to leave the elevator or yard," and between "an invoice could be
settled" and "it is allowed to settle." Every rule it enforces is
traceable to the domain (Wholesale of Agricultural Raw Materials and
Live Animals, ISIC 4620) and to the four `:social-impact` tags in
`blueprint.edn` (`:biosecurity`, `:animal-welfare`, `:food-safety`,
`:transparency`).

This is the rule the companion contract test
(`test/agritrade/governor_contract_test.cljk`) encodes end-to-end: the
AgriTradeAdvisor never dispatches a consignment to a counterparty or
settles an invoice the Agri Trading Governor would reject,
`:delivery/dispatch` and `:invoice/settle` NEVER auto-commit at any
phase, `:order/intake` (no direct capital risk) MAY auto-commit when
clean, and every decision (commit OR hold) leaves exactly one ledger
fact.

**Authorizes a delivery (`:delivery/dispatch`) or invoice settlement
(`:invoice/settle`) only when ALL of the following hold:**

1. **An official spec-basis citation exists for the jurisdiction AND
   consignment kind** -- the governor will not authorize any
   `:biosecurity/verify`, `:delivery/dispatch`, or `:invoice/settle`
   proposal whose jurisdiction has no entry in the `agritrade.facts`
   catalog (`:no-spec-basis`). This is the direct enforcement of
   `:transparency`: a jurisdiction whose biosecurity/sanctions
   requirements cannot be traced to an OFFICIAL public source is never
   guessed. The advisor must not fabricate a jurisdiction's
   requirements.
2. **The jurisdiction's required evidence is fully on file, for THIS
   consignment's kind** -- for a delivery or invoice the order's
   jurisdiction must have been verified with a complete biosecurity
   evidence checklist on record: the credit-clearance record, the
   contract / purchase order, the sanctions-screening (OFAC /
   equivalent) record, and the kind-specific biosecurity certificate
   (`:evidence-incomplete`). This protects `:biosecurity` and
   `:food-safety`: a consignment that cannot prove biosecurity
   diligence never dispatches.
3. **The counterparty's credit has been cleared** -- the governor reads
   the dedicated `:credit-cleared?` fact on the order and refuses to
   dispatch when credit has NOT been cleared (the leasing collateral-
   coverage discipline, applied to counterparty credit)
   (`:credit-uncleared`). Evaluated at `:delivery/dispatch`.
4. **Contract-terms are on file** -- the governor refuses to dispatch
   when no `:contract-terms` are recorded for the order
   (`:contract-missing`). No agricultural or live-animal consignment
   leaves the elevator or yard against an undocumented trade. Evaluated
   at `:delivery/dispatch`.
5. **A phytosanitary certificate is on file, for `:plant`
   consignments** -- the governor reads the dedicated
   `:phytosanitary-certificate?` fact and refuses to dispatch a
   grain/feed/seed/fibre consignment with no plant-health clearance
   (`:phytosanitary-certificate-missing`). This check is a NO-OP for
   `:animal` consignments -- it is specific to the plant-health regime.
   Evaluated at `:delivery/dispatch`.
6. **An animal-health certificate is on file, for `:animal`
   consignments** -- the governor reads the dedicated `:animal-health-
   certificate?` fact and refuses to dispatch a livestock consignment
   with no veterinary clearance (`:animal-health-certificate-missing`).
   This check is a NO-OP for `:plant` consignments -- it is specific to
   the animal-health regime, and deliberately a SEPARATE check from #5
   rather than one generic 'certificate-missing' rule (see `docs/adr/
   0001-architecture.md` Decision 4). Evaluated at `:delivery/dispatch`.
7. **The counterparty has passed OFAC / equivalent sanctions screening**
   -- the governor reads the dedicated `:sanctions-screened?` fact and
   treats an unresolved sanctions-screening flag as a HARD, un-
   overridable hold (`:counterparty-sanctions-flag-unresolved`). Neither
   product/animals nor money moves against an unscreened counterparty.
   Evaluated UNCONDITIONALLY at both `:delivery/dispatch` and
   `:invoice/settle`.
8. **The order has not already been dispatched, and the invoice has not
   already been settled** -- a double delivery of the same order is
   refused off a dedicated `:dispatched?` fact, and a double invoice off
   a dedicated `:invoiced?` fact (never a `:status` value), the
   double-actuation guard every sibling actor in this fleet enforces
   (`:already-dispatched` / `:already-invoiced`).

**Rejects (HOLD, un-overridable, never even reaches a human) when any of
the above fail.** A proposal with no spec-basis, incomplete evidence, an
uncleared counterparty credit, no contract-terms on file, a missing
kind-appropriate biosecurity certificate, an unresolved
sanctions-screening flag, or a double delivery/invoice is held at the
governor node -- a human approver cannot override these, by
construction.

**Always escalates to a human (never auto-commits) for `:delivery/
dispatch` and `:invoice/settle`**, even when every check above is clean.
Dispatching a real consignment to a counterparty and settling a real
invoice (real money moving between counterparty and trader) are the two
real-world actuation events this actor performs; both are always a
human trading supervisor's call. This is enforced by TWO independent
layers that agree on purpose: the governor's confidence / actuation
SOFT gate (a `:delivery/dispatch` / `:invoice/settle` stake always
escalates) and `agritrade.phase`'s phase table, which never puts either
op in any phase's `:auto` set. The `:animal-welfare` tag is enforced
upstream of the governor, in the biosecurity-verification evidence step
and the Robotics Premise below -- the governor's job is delivery/
invoice authorization integrity, not trading-book optimization.

## Required Technologies

`blueprint.edn`'s `:itonami.blueprint/required-technologies` for this business,
and what each one is actually load-bearing for here (not a generic capability
list):

| Technology | What it is FOR in Wholesale of Agricultural Raw Materials and Live Animals |
|---|---|
| `:robotics` | The autonomous elevator-conveyance robot (auger/belt/weighbridge-integrated loadout) that performs the physical grain/feed/seed/fibre loadout, and the automated (always human-attended) chute/gate control that assists livestock loadout at the yard. The governor never dispatches hardware itself: a delivery-clearing action must have cleared the same sign-off a human trading supervisor would need (see README Robotics Premise). |
| `:identity` | Trader, trading-supervisor, elevator/yard-operator and counterparty identity plus role-based access, so the governor's sign-off is tied to *who* authorized a delivery or invoice, not just *that* someone did. |
| `:forms` | Structured intake for agri-order booking, per-jurisdiction/per-kind evidence capture (credit-clearance record, contract/PO, sanctions-screening record, phytosanitary or animal-health certificate), and sanctions / credit exception submission -- the data the Decision Rule above actually evaluates comes in through these forms. |
| `:dmn` | Encodes the `:agri-trading-governor` Decision Rule itself (spec-basis, evidence completeness, credit-clearance, contract-on-file, phytosanitary-certificate, animal-health-certificate, sanctions-screening, the double-actuation guards, the actuation gate) as an evaluable decision table rather than code buried in application logic -- this is what makes the governor auditable and swappable per-deployment. |
| `:bpmn` | Orchestrates the intake -> verify -> dispatch -> settle -> audit loop end-to-end (see `docs/operator-guide.md`) across agri-order intake, biosecurity verification, delivery, and invoice settlement, including the sanctions / credit escalation gate. |
| `:audit-ledger` | The immutable record of every verification, delivery, invoice, sanctions flag, and hold -- this is what "an auditable, spec-cited trade record for every delivery and invoice" (Trust Controls, below) actually means in practice, and the evidence an operator needs if a delivery or an invoice is later disputed by a counterparty, quarantine authority or regulator. |
| `:optimization` | Elevator/route and trading-book optimization -- selects the profitable fulfillment strategy for an elevator or yard. This R0 build deliberately scopes optimization OUT (see README `Business-process coverage`); the capability is correctly marked required, the integration is a follow-up slice. |

There is NO bespoke `:agritrade` capability library in this stack
(unlike the freight sibling's `:logistics`): the agri-trading checks
(credit-clearance, contract-on-file, phytosanitary certification,
animal-health certification, sanctions-screening) are direct entity
boolean reads in `agritrade.governor`, on top of the generic
robotics/identity/forms/dmn/bpmn/audit-ledger stack (see Capability
layer).

## Trust Controls
- a jurisdiction (or consignment kind) with no official spec-basis can
  never be verified, dispatched, or invoiced against
- a delivery never starts with incomplete biosecurity-diligence evidence
- a delivery never starts with an uncleared counterparty credit, no
  contract-terms on file, or a missing kind-appropriate biosecurity
  certificate (phytosanitary for plant, animal-health for animal)
- a delivery or invoice never settles against an unresolved
  sanctions-screening flag
- sanctions / credit / biosecurity flags cannot be silently suppressed
- the same order can never be delivered or invoiced twice
- a delivery or invoice never auto-commits; both always need a human
  trading supervisor
- every delivery and invoice (commit OR hold) leaves exactly one
  immutable ledger fact
- counterparty, credit, biosecurity-certificate and sanctions data stays
  outside Git

## Implementation notes (`:implemented`)

The Decision Rule above is implemented faithfully by `agritrade.governor`
as nine HARD checks (a human approver cannot override them) plus one
SOFT gate:

- `spec-basis-violations` -- the spec-basis check above, evaluated on
  every `:biosecurity/verify`, `:delivery/dispatch`, and `:invoice/settle`.
- `evidence-incomplete-violations` -- the evidence-completeness check
  above, for `:delivery/dispatch` / `:invoice/settle`.
- `credit-uncleared-violations` -- the counterparty-credit check above
  (the leasing collateral-coverage discipline applied to counterparty
  credit); evaluated on every `:delivery/dispatch`.
- `contract-missing-violations` -- the contract-on-file check above;
  evaluated on every `:delivery/dispatch`.
- `phytosanitary-certificate-missing-violations` -- the plant-health
  certificate check above, gated on `:consignment-kind :plant`;
  evaluated on every `:delivery/dispatch`. NO analog in the fuel-
  wholesale, general-trading or commission-brokerage siblings.
- `animal-health-certificate-missing-violations` -- the animal-health
  certificate check above, gated on `:consignment-kind :animal`;
  evaluated on every `:delivery/dispatch`. Deliberately a SEPARATE check
  from the one above, not a shared generic rule -- see `docs/adr/
  0001-architecture.md` Decision 4 for why.
- `counterparty-sanctions-flag-unresolved-violations` -- the sanctions-
  screening check above (the same open-flag-unresolved discipline the
  freight sibling's delivery-exception-unresolved check establishes);
  evaluated unconditionally on both `:delivery/dispatch` and
  `:invoice/settle`.
- `already-dispatched-violations` / `already-invoiced-violations` -- the
  double-actuation guards above, off dedicated `:dispatched?` /
  `:invoiced?` booleans (never a `:status` value), the same discipline
  every sibling governor's guards establish.
- the confidence floor / actuation SOFT gate -- low confidence, OR a
  `:delivery/dispatch` / `:invoice/settle` stake, escalates to a human;
  and `agritrade.phase` independently never auto-commits either op at
  any phase.

Unlike the crude-extraction sibling's governor (which calls pure
physical range-check functions in its registry), this governor needs no
range-check functions at all: its domain checks read the `agri-order`
record's own dedicated booleans directly. `:delivery/dispatch` and
`:invoice/settle` are the two real-world actuation events
(`#{:delivery/dispatch :invoice/settle}`), applied SEQUENTIALLY to the
SAME agri-order (delivery first, invoice settlement later), the same
sequential dual-actuation shape the fuel-wholesale, repair-shop,
quarrying and crude-extraction clusters use. Neither ever auto-commits
at any phase. Elevator/route and trading-book optimization (the
`:optimization` line above) is a follow-up slice, not in this R0 build
-- see README `Business-process coverage`.

## Capability layer

Like the fuel-wholesale (`cloud-itonami-isic-4671`), general-trading
(`cloud-itonami-isic-4690`) and commission-brokerage
(`cloud-itonami-isic-4610`) siblings, this vertical is SELF-CONTAINED:
there is no `kotoba-lang/agritrade` to delegate agri-trading validation
to. The credit-clearance / contract-on-file / phytosanitary-certificate
/ animal-health-certificate / sanctions-screening checks live as direct
entity boolean reads in `agritrade.governor` (off dedicated
`:credit-cleared?` / `:contract-terms` / `:phytosanitary-certificate?`
/ `:animal-health-certificate?` / `:sanctions-screened?` facts on the
`agri-order` record) -- this vertical's governor needs no pure
range-check functions at all, because its domain checks ARE direct
boolean reads.

## Jurisdiction coverage (honest)

`agritrade.facts/catalog` currently seeds 4 jurisdictions with an
official spec-basis FOR BOTH consignment kinds, each a REAL regime:

- **Japan (JPN)** -- 植物防疫法 (Plant Protection Act) for plant
  consignments and 家畜伝染病予防法 (Act on Domestic Animal Infectious
  Disease Control) for animal consignments, both under 農林水産省
  (MAFF), administered on the ground by different field-office networks
  (植物防疫所 for plant, 動物検疫所/家畜保健衛生所 for animal).
- **United States (USA)** -- the Plant Protection Act (7 U.S.C. §7701
  et seq.) for plant consignments and the Animal Health Protection Act
  (7 U.S.C. §8301 et seq.) for animal consignments, both administered
  by APHIS (Animal and Plant Health Inspection Service, USDA).
- **United Kingdom (GBR)** -- plant health legislation (Plant Health
  etc. (Amendment) (England) Regulations 2020 and devolved equivalents)
  for plant consignments and animal health legislation (Animal Health
  Act 1981; Trade in Animals and Related Products Regulations 2011 /
  TARP) for animal consignments, both administered by APHA (the Animal
  and Plant Health Agency).
- **Germany (DEU)**, representing the EU regime -- Regulation (EU)
  2016/2031 (Plant Health Law) for plant consignments and Regulation
  (EU) 2016/429 (Animal Health Law) for animal consignments, with
  federal scientific/coordinating roles held by the Julius Kühn-Institut
  (plant) and the Friedrich-Loeffler-Institut (animal) under BMEL.

This is a starting catalog to prove the governor contract end-to-end,
not a claim of global coverage (4 of ~194 jurisdictions worldwide, each
covering 2 consignment kinds). I do not have live web access; the
headline statute/regulation and agency names above are cited from
training-time knowledge with reasonable confidence, but in particular
the exact Länder-level German implementing legislation (as distinct
from the EU regulations themselves, which I am confident about) should
be independently verified before this catalog is relied on
operationally. Adding a jurisdiction, or a missing kind for an
already-seeded jurisdiction, is additive: one map entry in
`agritrade.facts/catalog`, citing a real official source -- never
fabricate a jurisdiction's requirements to make coverage look bigger.

## Maturity

`:implemented` -- `AgriTradeAdvisor` + `Agri Trading Governor` run as
real, tested code, promoted from the originally-published `:blueprint`-
tier scaffold, following the SAME governed-actor architecture as the
other prior actors across this fleet, with its own distinct,
independently-named governor and its own direct-entity-boolean
agri-trading checks -- including the fleet's first split of a single
domain-defining check into two kind-gated variants. See
`docs/adr/0001-architecture.md` for the history and design.

## Robotics Premise

`blueprint.edn` sets `:itonami.blueprint/robotics true`. This is a
reasoned, kind-differentiated call, not a default carried over from a
sibling:

- For `:plant`-kind consignments (grain/feed/seed/fibre), the physical-
  domain claim is strong and well precedented: modern grain-elevator
  terminals already run automated conveyance -- augers, belt conveyors
  and weighbridge-integrated loadout systems -- and an autonomous
  elevator-loadout robot performs the physical loadout at the elevator,
  under the actor, gated by the independent Agri Trading Governor. This
  is directly analogous to the fuel-wholesale sibling's loading-rack/
  valve robot.
- For `:animal`-kind consignments (livestock), the claim is deliberately
  narrower: real animal-welfare law (e.g. EU Regulation (EC) No. 1/2005
  on the protection of animals during transport, and its jurisdictional
  analogues) requires a competent handler's direct involvement in
  live-animal loading. Modern yards do use automated chute/ramp control
  and sorting gates, but this actor treats livestock-loadout automation
  as present and real, yet always human-attended -- never a substitute
  for a stockperson's judgment and welfare duty.

Either way, the governor never dispatches hardware itself: a delivery-
clearing action must have cleared the same sign-off a human trading
supervisor would need. A robot may run the elevator auger or open a
loadout gate, but only after the governor (every HARD check clean) and
a human supervisor both agree it is safe to -- the same
operating-state-machine-gated-by-governor premise every cloud-itonami
vertical restates (ADR-2607011000): the blueprint declares `:robotics
true`, the README names the robot(s) that perform the physical act, and
the Agri Trading Governor is the independent gate that robot's command
must pass.
