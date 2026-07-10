# ADR-0001: AgriTradeAdvisor ⊣ Agri Trading Governor architecture

## Status

Accepted. `cloud-itonami-isic-4620` published directly as `:implemented`
in the `kotoba-lang/industry` registry.

## Context

`cloud-itonami-isic-4620` publishes an OSS business blueprint for
wholesale of agricultural raw materials and live animals (agri-order
intake, per-jurisdiction, per-consignment-kind contract / biosecurity /
sanctions regulatory verification, delivery, and invoice settlement).
Like every prior actor in this fleet, the blueprint alone is not an
implementation: this ADR records the governed-actor architecture that
establishes it as real, tested code, following the same langgraph
StateGraph + independent Governor + Phase 0->3 rollout pattern
established by `cloud-itonami-isic-6511` (life insurance) and applied
across many prior siblings, most directly the three PRINCIPAL/AGENCY
wholesale-trading siblings: `cloud-itonami-isic-4671` (fuel wholesale,
PRINCIPAL, single-commodity excise/sanctions focus),
`cloud-itonami-isic-4690` (general/diversified wholesale trading,
PRINCIPAL, multi-commodity export-control/sanctions focus), and
`cloud-itonami-isic-4610` (commission brokerage, AGENCY, never takes
title, dual-agency conflict-of-interest focus).

Like those three siblings, this vertical has NO bespoke domain
capability library in `kotoba-lang` to wrap (verified: no
`kotoba-lang/agritrade`-style repo exists, and `kotoba-lang/robotics`
is the generic cross-cutting robotics contract every cloud-itonami
vertical already uses, not a domain-specific library for this
vertical). This build therefore uses self-contained domain logic. The
agri-trading checks (credit-clearance, contract-on-file, biosecurity
certification, sanctions-screening) are direct entity boolean reads in
`agritrade.governor`, off dedicated `:credit-cleared?` /
`:contract-terms` / `:phytosanitary-certificate?` / `:animal-health-
certificate?` / `:sanctions-screened?` facts on the `agri-order` record
-- NO pure range-check functions are needed (contrast the crude
sibling, whose registry hosts its reservoir/annular/water-cut/H2S range
checks).

This blueprint's own `:itonami.blueprint/governor` keyword,
`:agri-trading-governor`, is grep-verified UNIQUE fleet-wide -- no
naming-collision precedent question, a fresh independent build.

## Decision

### Decision 1: fresh governor identity, no reuse precedent needed

`:agri-trading-governor` is grep-verified unique across every
`blueprint.edn` in this fleet. This build follows the SAME
governed-actor architecture as every prior actor, but with its own
distinct governor identity.

### Decision 2: self-contained domain logic, direct entity booleans (no `kotoba-lang/agritrade` to wrap, and no range-check functions to host)

Like the fuel-wholesale, general-trading and commission-brokerage
siblings (and unlike the crude-extraction sibling, which hosts pure
physical range-check functions in its registry because its governor
re-verifies measured physical values), this agri-wholesale vertical
needs no range-check functions: there is no pre-existing agri-trading
capability library to delegate to, AND the governor's domain checks
(credit-clearance, contract-on-file, biosecurity certification,
sanctions-screening) are direct entity boolean reads off the
`agri-order` record's own dedicated facts -- not measured-value-vs-limit
range comparisons. So `agritrade.registry` is RECORD CONSTRUCTION ONLY
(no range-check functions), and `agritrade.governor` reads the order's
booleans directly.

### Decision 3: dual-actuation shape, SEQUENTIAL on the SAME `agri-order` entity

Like the fuel-wholesale sibling's `fuel-order` entity, this vertical's
`dispatch` and `settle` actuation events apply SEQUENTIALLY to the SAME
`agri-order` -- a delivery happens first (grain/feed/seed/fibre or live
animals leave the elevator/store/yard), invoice settlement happens
later (the money side of the trade, custody / financial transfer), on
the same order record. `high-stakes` is
`#{:delivery/dispatch :invoice/settle}`; neither ever auto-commits at
any phase.

### Decision 4: TWO biosecurity-certificate checks, kind-gated, not one generic 'certificate-missing' check -- the defining design decision of this build

This is the decision that most distinguishes this vertical from its
three PRINCIPAL/AGENCY wholesale-trading siblings, and the one most
worth documenting the reasoning for.

ISIC 4620, unlike ISIC 4671 (a single commodity class), ISIC 4690 (many
UNRELATED commodity classes governed by ONE uniform export-control/
sanctions regime regardless of which good is in the order), or ISIC
4610 (a fee-basis intermediary that never takes title at all), spans
TWO consignment kinds under ONE classification code that are governed
by GENUINELY DIFFERENT statutes -- and, in some jurisdictions, even
different administering agencies:

- agricultural raw materials (grain/feed/seed/fibre; `:consignment-kind
  :plant`) are governed by phytosanitary/plant-health law (quarantine
  pests, seed-borne pathogens, invasive weeds).
- live animals (livestock; `:consignment-kind :animal`) are governed by
  animal/veterinary-health law (transmissible animal disease control:
  foot-and-mouth disease, avian influenza, African swine fever).

Two design options were considered:

- **Option A (rejected): one generic `:certificate-missing` check**,
  reading a single `:biosecurity-certificate?` fact regardless of
  consignment kind. Rejected because it would blur which regulatory
  regime actually failed on the audit ledger -- a `:certificate-
  missing` hold on a grain order and a `:certificate-missing` hold on a
  livestock order would look identical in the ledger even though they
  are governed by different statutes, inspected by (sometimes)
  different agencies, and remediated by completely different processes
  (a phytosanitary inspection vs. a veterinary inspection). This would
  make the audit trail less useful to exactly the regulator or
  counterparty it exists to serve, and would misrepresent the domain:
  real biosecurity law does NOT treat plant quarantine and animal
  quarantine as the same regime with different paperwork.
- **Option B (chosen): two separate checks**,
  `phytosanitary-certificate-missing-violations` (reads
  `:phytosanitary-certificate?`, fires only when `:consignment-kind
  :plant`) and `animal-health-certificate-missing-violations` (reads
  `:animal-health-certificate?`, fires only when `:consignment-kind
  :animal`). Each produces its own distinctly-named rule keyword on the
  audit ledger. This mirrors real biosecurity practice honestly (see
  `agritrade.facts` catalog, which is likewise keyed by BOTH
  jurisdiction AND kind, since e.g. Japan's 植物防疫法 and 家畜伝染病予防法
  are two different statutes even though both sit under MAFF), and it
  is the SAME kind of domain-specific check the commission-brokerage
  sibling's `conflict-of-interest-undisclosed-violations` establishes
  as precedent: when a domain has a genuinely NEW regulatory concern
  with no analog in a sibling, model it as its own named check rather
  than force-fitting an existing pattern or a single ambiguous generic
  rule.

This makes ISIC 4620 the first vertical in this fleet's wholesale-
trading cluster whose governor runs SEVEN HARD checks instead of five
(fuel-wholesale) or six (general-trading), and whose `agri-order`
record's biosecurity fields are consignment-kind-conditional rather
than uniform.

### Decision 5: `counterparty-sanctions-flag-unresolved?` -- the open-flag-unresolved discipline (reapplied, not new)

An unresolved sanctions-screening flag -- the counterparty has not
passed OFAC / equivalent sanctions screening -- is a HARD,
un-overridable hold. This reuses the SAME open-flag-unresolved
discipline the freight sibling's `delivery-exception-unresolved?` check
(and the fuel-wholesale/general-trading/commission-brokerage siblings'
own sanctions checks) establish -- an open concern cannot be silently
suppressed to force a delivery or invoice through. Evaluated
UNCONDITIONALLY at both `:delivery/dispatch` and `:invoice/settle`, and
UNCONDITIONALLY regardless of consignment kind (unlike the certificate
checks in Decision 4, sanctions screening applies uniformly to both
`:plant` and `:animal` consignments -- there is no biosecurity reason
to differentiate it by kind, only the certificate itself differs).

### Decision 6: dedicated double-actuation-guard booleans

`:dispatched?` / `:invoiced?` are dedicated booleans on the `agri-order`
record, never a single `:status` value -- the same discipline every
prior governor's guards establish, informed by `cloud-itonami-isic-
6492`'s real status-lifecycle bug (ADR-2607071320).

### Decision 7: Store protocol, MemStore + DatomicStore parity

`agritrade.store/Store` is implemented by both `MemStore` (atom-backed,
default for dev/tests/demo) and `DatomicStore` (`langchain.db`-backed),
proven to satisfy the same contract in
`test/agritrade/store_contract_test.clj`. The ledger stays append-only
on every backend: which agri-order was verified for a jurisdiction/kind
with no official spec-basis, which counterparty had credit-uncleared /
no contract / a missing phytosanitary or animal-health certificate / an
unresolved sanctions-screening flag, which order was dispatched, which
invoice was settled, on what jurisdictional and biosecurity basis,
approved by whom -- always a query over an immutable log.

### Decision 8: Phase 0->3 with `:delivery/dispatch`/`:invoice/settle` NEVER auto

`agritrade.phase`'s phase table puts `:order/intake` (no direct capital
risk) in phase 3's `:auto` set as its only member; `:delivery/dispatch`
and `:invoice/settle` are deliberately ABSENT from every phase's `:auto`
set, including phase 3 -- a permanent structural fact.
`agritrade.governor`'s high-stakes gate enforces the same invariant
independently: two layers agree that actuation is always a human
trading supervisor's call.

### Decision 9: mock + LLM advisor pair

`agritrade.agritradeadvisor` provides a deterministic `mock-advisor`
(default, runs offline) and an `llm-advisor` backed by a
`langchain.model/ChatModel`. The LLM advisor's EDN proposal is parsed
defensively: any parse/shape failure yields a safe low-confidence noop
so the governor escalates/holds -- an LLM hiccup can never auto-dispatch
a consignment or auto-settle an invoice.

### Decision 10: `:robotics true`, reasoned per consignment kind (not a default carry-over)

`:itonami.blueprint/robotics` is `true`, but this was a deliberate,
kind-differentiated call rather than a default inherited from a recent
sibling (the general-trading and commission-brokerage siblings both set
`:robotics false`, being non-physical intermediation/brokerage
verticals with no analogous physical dispatch act -- this vertical's
`:delivery/dispatch` is a genuine physical-domain act, closer in kind to
the fuel-wholesale sibling's rack dispatch than to either of the more
recent two siblings). For `:plant`-kind traffic, real grain-elevator
terminals already run automated conveyance (augers, belt conveyors,
weighbridge-integrated loadout), so an autonomous elevator-loadout
robot performing the physical loadout is a strong, directly-precedented
claim (the same structural claim the fuel-wholesale sibling's loading-
rack/valve robot makes). For `:animal`-kind traffic, the claim is
narrower: real animal-welfare law requires a competent handler's direct
involvement in live-animal loading, so this build documents livestock-
loadout automation (chute/ramp control, sorting gates) as real but
always human-attended, never a substitute for a stockperson. See README
`Robotics premise` and `docs/business-model.md` `Robotics Premise` for
the full reasoning.

## Alternatives considered

- **Wrapping a bespoke `kotoba-lang/agritrade` capability library.**
  Considered and explicitly ruled out: no such library exists, and
  `kotoba-lang/robotics` is generic, not agri-trading-specific. Forcing
  a false capability-library integration would be dishonest; this build
  correctly uses self-contained domain logic instead.
- **Hosting pure range-check functions in the registry (as the crude
  sibling does).** Considered and ruled out: the agri-trading domain
  checks are direct entity booleans (credit cleared? contract on file?
  biosecurity certificate on file? sanctions screened?), not measured-
  value-vs-limit range comparisons, so there are no range checks to
  host. `agritrade.registry` is record construction only.
- **One generic `:certificate-missing` check instead of two kind-gated
  checks.** Considered and rejected -- see Decision 4 above for the
  full reasoning: it would blur two genuinely different regulatory
  regimes on the audit ledger.
- **A `:kind`-distinguished entity for delivery vs. invoice** (matching
  the retail sibling's `order` shape). Rejected: delivery and invoice
  settlement happen SEQUENTIALLY on the SAME agri-order in this domain,
  not as alternative actions -- the fuel-wholesale sibling's sequential
  shape is the honest match here. (Note this is a DIFFERENT `:kind`
  question from `:consignment-kind`, which distinguishes plant vs.
  animal consignments and is or­thogonal to the dispatch/invoice
  sequencing question.)
- **Defaulting `:robotics` to `false`** (matching the two most recent
  siblings, general-trading and commission-brokerage). Considered and
  rejected: those two verticals are non-physical (general trade
  intermediation; fee-basis brokerage that never takes title), so
  `false` was the honest call for them. This vertical's `:delivery/
  dispatch` is a genuine physical act (grain/feed/seed/fibre or live
  animals actually leaving an elevator or yard), so defaulting to
  `false` here would have been pattern-matching the two most recent
  siblings rather than reasoning about this domain on its own terms --
  see Decision 10.
- **Building elevator/route and trading-book optimization in this
  R0.** Rejected in favor of a scoped R0 slice (the `:optimization`
  capability is correctly marked required, the integration is a
  follow-up), consistent with this fleet's 'extending coverage is
  additive' convention.

## Consequences

- Fresh independent actor in this fleet, following the SAME
  governed-actor architecture as every prior sibling.
- Establishes the agri-trading checks as direct entity boolean reads
  (no pure range-check functions needed), an honest structural
  differentiator from the crude-extraction sibling's registry-hosted
  physical range checks.
- Establishes the fleet's first per-consignment-kind biosecurity-
  certificate split (Decision 4) -- a template for any future vertical
  whose ISIC code spans more than one genuinely distinct regulatory
  regime under one classification.
- `MemStore` || `DatomicStore` parity is proven by
  `test/agritrade/store_contract_test.clj`.
- The demo (`clojure -M:dev:run`) walks two clean lifecycles (one grain
  consignment, one livestock consignment) end-to-end, plus every
  HARD-hold scenario (no spec-basis, credit-uncleared, contract-missing,
  phytosanitary-certificate-missing, animal-health-certificate-missing,
  sanctions, double delivery, double invoice).
- `blueprint.edn`'s `:robotics true` is a reasoned, kind-differentiated
  call, documented in README and `docs/business-model.md`, not a
  default carried over from the two most recent (non-physical)
  siblings.

## References

- `cloud-itonami-isic-6511/docs/adr/0001-architecture.md` (origin of the
  general governed-actor architecture pattern)
- `cloud-itonami-isic-4671/docs/adr/0001-architecture.md` (fuel-
  wholesale sibling; origin of the sequential dual-actuation shape and
  the self-contained-domain-logic pattern this build follows most
  closely)
- `cloud-itonami-isic-4690/docs/adr/0001-architecture.md` (general-
  trading sibling; contrast: one uniform export-control check across
  UNRELATED commodity categories, vs. this vertical's two kind-gated
  certificate checks across ONE ISIC code)
- `cloud-itonami-isic-4610/docs/adr/0001-architecture.md` (commission-
  brokerage sibling; origin of the 'a genuinely new regulatory concern
  gets its own named check' precedent this build's Decision 4 follows)
- `cloud-itonami-isic-0610/docs/adr/0001-architecture.md` (crude-
  extraction sibling; contrast: hosts pure physical range-check
  functions in its registry, which this vertical does NOT need)
- 植物防疫法 (Plant Protection Act); 家畜伝染病予防法 (Act on Domestic
  Animal Infectious Disease Control) (Japan, MAFF)
- Plant Protection Act (7 U.S.C. §7701 et seq.); Animal Health
  Protection Act (7 U.S.C. §8301 et seq.) (US, APHIS/USDA)
- Plant Health etc. (Amendment) (England) Regulations 2020; Animal
  Health Act 1981; Trade in Animals and Related Products Regulations
  2011 (UK, APHA)
- Regulation (EU) 2016/2031 (Plant Health Law); Regulation (EU)
  2016/429 (Animal Health Law) (EU; Germany, JKI/FLI/BMEL)
