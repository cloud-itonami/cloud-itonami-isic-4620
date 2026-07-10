# cloud-itonami-isic-4620

Open Business Blueprint for **ISIC Rev.5 4620**: Wholesale of
Agricultural Raw Materials and Live Animals -- agri-order intake
(grain, feed, seed and fibre consignments AND live-animal/livestock
consignments), per-jurisdiction counterparty-diligence / biosecurity /
sanctions regulatory verification, delivery dispatch, and invoice
settlement for a wholesaler of agricultural raw materials and live
animals.

This repository publishes an agri-wholesale actor -- agri-order intake,
per-jurisdiction contract / biosecurity (phytosanitary / animal-health)
/ sanctions regulatory verification, delivery and invoice settlement --
as an OSS business that any qualified operator can fork, deploy, run,
improve and sell, so a regional grain, feed or livestock wholesaler
never surrenders counterparty, credit, biosecurity-certification and
trade data to a closed agri-trading / ERP SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet -- here it is **AgriTradeAdvisor ⊣
Agri Trading Governor**. This blueprint's own
`:itonami.blueprint/governor` keyword, `:agri-trading-governor`, is a
UNIQUE keyword fleet-wide (grep-verified: no other blueprint declares
it) -- a fresh, independent build.

**Like the fuel-wholesale (`cloud-itonami-isic-4671`), general-trading
(`cloud-itonami-isic-4690`) and commission-brokerage
(`cloud-itonami-isic-4610`) siblings, this vertical is SELF-CONTAINED**:
there is no `kotoba-lang/agritrade` to delegate agri-trading validation
to, so the credit-clearance / contract-on-file / biosecurity-
certificate / sanctions-screening checks live as direct entity boolean
reads in `agritrade.governor` (off dedicated `:credit-cleared?` /
`:contract-terms` / `:phytosanitary-certificate?` / `:animal-health-
certificate?` / `:sanctions-screened?` facts on the `agri-order`
record), rather than wrapping an external capability library's own
validated function.

**What makes ISIC 4620 structurally different from every wholesale
sibling above**: it is the only one of the four whose defining
regulatory exposure is BIOSECURITY, not export-control or excise, AND
it is the only one where a single ISIC code spans TWO genuinely
different biosecurity regimes -- agricultural raw materials (grain,
feed, seed, fibre; phytosanitary/plant-health law) and live animals
(livestock; animal/veterinary-health law) -- governed by different
statutes, sometimes different agencies, even within the same country.
See `src/agritrade/facts.cljc` and `src/agritrade/governor.cljc` for
how this is modeled: a `:consignment-kind` (`:plant` | `:animal`) on
every `agri-order`, a jurisdiction catalog keyed by BOTH jurisdiction
and kind, and TWO separate certificate-missing HARD checks
(`phytosanitary-certificate-missing` for `:plant`, `animal-health-
certificate-missing` for `:animal`) rather than one generic check.

> **Why an actor layer at all?** An LLM is great at drafting an order
> summary, normalizing records, and reading a credit file -- but it
> has **no notion of which jurisdiction's phytosanitary / animal-health
> / sanctions law is official, no license to dispatch real grain, feed,
> seed, fibre or live animals to a counterparty or settle a real
> invoice, and no way to know on its own whether the counterparty's
> credit has actually been cleared, whether contract terms are actually
> on file, or whether a REAL phytosanitary or animal-health certificate
> has actually been issued for THIS consignment**. Letting it dispatch
> a consignment or settle an invoice directly invites fabricated
> regulatory citations, grain or livestock leaving the elevator/yard to
> an uncreditworthy or unscreened counterparty without a real quarantine
> clearance, and an invoice settling against a sanctioned party --
> exposing the operator to real biosecurity-enforcement and financial
> liability, for whoever runs it. This project seals the AgriTradeAdvisor
> into a single node and wraps it with an independent **Agri Trading
> Governor**, a human **approval workflow**, and an immutable **audit
> ledger**.

## Scope: what this actor does and does not do

This actor covers agri-order intake through contract / biosecurity /
sanctions regulatory verification, delivery dispatch (grain, feed,
seed, fibre or live animals leaving the elevator/store/yard for a
counterparty) and invoice settlement (the money side of the trade,
custody / financial transfer) for a wholesaler of agricultural raw
materials and live animals. It does **not**, by itself, hold any
agri-wholesale licence, quarantine-inspection authority or operating
authority required to run an agri-wholesale business in a given
jurisdiction, and it does not claim to. It also does not perform the
actual physical elevator loadout, livestock loadout, or route
optimization itself, or judge trading-book economics -- logistics /
route optimization (the blueprint's own `:optimization` technology) is
a follow-up slice, not in this R0. Whoever deploys and operates a live
instance (a qualified trading supervisor / elevator or yard operator)
supplies any jurisdiction-specific operating authority, the real
elevator-conveyance/livestock-handling equipment integration and the
real ERP / accounts-receivable integrations, and bears that
jurisdiction's liability -- the software supplies the governed,
spec-cited, audited execution scaffold so that operator does not have
to build the compliance layer from scratch.

### Actuation

**Dispatching a real grain/feed/seed/fibre or live-animal consignment
to a counterparty and settling a real invoice are never autonomous, at
any phase, by construction.** Two independent layers enforce this
(`agritrade.governor`'s `:delivery/dispatch`/`:invoice/settle`
high-stakes gate and `agritrade.phase`'s phase table, which never puts
either op in any phase's `:auto` set) -- see `agritrade.phase`'s
docstring and `test/agritrade/phase_test.clj`'s
`delivery-dispatch-never-auto-at-any-phase`/
`invoice-settle-never-auto-at-any-phase`. The actor may draft, check
and recommend; a human trading supervisor is always the one who
actually dispatches a delivery or settles an invoice. Grounded in
agri-trading and biosecurity doctrine (the same discipline every
regulator in `agritrade.facts` codifies: a real delivery and a real
invoice settlement are human sign-off acts) -- a genuine DUAL-actuation
shape, applied SEQUENTIALLY to the SAME agri-order (delivery first,
invoice settlement later), the same sequential shape the fuel-wholesale
sibling uses.

## The core contract

```
agri-order intake + jurisdiction/kind facts (agritrade.facts, spec-cited)
        |
        v
   ┌───────────────────────┐   proposal      ┌───────────────────────┐
   │ AgriTradeAdvisor       │ ─────────────▶ │ Agri Trading Governor  │  (independent system)
   │ (sealed)              │  + citations    │ spec-basis · evidence- │
   └───────────────────────┘                 │ incomplete · credit-   │
          │                 commit ◀┼ uncleared · contract-missing ·│
          │                         │ phytosanitary-certificate-     │
    record + ledger        escalate ┼ missing · animal-health-       │
          │              (ALWAYS for│ certificate-missing ·          │
          │       :delivery/        │ counterparty-sanctions-flag-   │
          │       dispatch/         │ unresolved · already-dispatched│
          │       :invoice/         │ · already-invoiced             │
          │       settle)           └───────────────────────┘
          ▼
      human approval
```

**The AgriTradeAdvisor never dispatches a consignment to a counterparty
or settles an invoice the Agri Trading Governor would reject.** Hard
violations (fabricated regulatory requirements; unsupported evidence;
an uncleared counterparty credit; no contract-terms on file; a missing
phytosanitary certificate on a plant consignment; a missing
animal-health certificate on an animal consignment; an unresolved
sanctions-screening flag; a double delivery/invoice) force **hold** and
*cannot* be approved past; a clean delivery/invoice proposal still
always routes to a human.

## Run

```bash
clojure -M:dev:run     # walk two clean lifecycles (grain + livestock) plus every HARD-hold case, through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here that premise is exercised for
the `:plant`-kind half of this vertical's traffic in the strongest and
most literal sense in the fleet so far: real grain-elevator terminals
already run automated conveyance -- augers, belt conveyors and
weighbridge-integrated loadout systems -- and an autonomous
elevator-loadout robot performs the physical grain/feed/seed/fibre
loadout at the elevator, under the actor, gated by the independent
**Agri Trading Governor**. For the `:animal`-kind half (livestock), the
claim is deliberately narrower and more welfare-constrained: modern
yards do use automated chute/ramp control and sorting gates, but real
animal-welfare law (e.g. EU Regulation (EC) No. 1/2005 on the
protection of animals during transport, and its jurisdictional
analogues) requires a competent handler's direct involvement in live-
animal loading, so this actor treats livestock-loadout automation as
present but always human-attended, never a substitute for a
stockperson. Either way, the governor never dispatches hardware itself:
a delivery-clearing action must have cleared the same sign-off a human
trading supervisor would need. This restates the fleet-wide robotics
premise three ways (ADR-2607011000): the blueprint declares `:robotics
true`, the README names the robot that performs the physical act, and
the Agri Trading Governor is the independent gate that robot's command
must pass -- a robot may run the elevator auger or open a loadout gate,
but only after the governor and a human supervisor both agree it is
safe to.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Agri Trading Governor, delivery/invoice draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`4620`). Like the fuel-wholesale sibling, this vertical is NOT backed by a
separate bespoke domain capability lib: the agri-trading checks
(credit-clearance, contract-on-file, phytosanitary certification,
animal-health certification, sanctions-screening) are direct entity
boolean reads in `agritrade.governor`, on top of the generic
robotics/identity/forms/dmn/bpmn/audit-ledger stack.

## Layout

| File | Role |
|---|---|
| `src/agritrade/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + delivery AND invoice history (dual history). The double-actuation guard checks dedicated `:dispatched?`/`:invoiced?` booleans rather than a `:status` value |
| `src/agritrade/registry.cljc` | Delivery/invoice draft records (record construction only -- the Agri Trading Governor's checks are direct entity booleans, so there are no pure range-check functions to host here) |
| `src/agritrade/facts.cljc` | Per-jurisdiction, per-consignment-kind (`:plant`/`:animal`) phytosanitary / animal-health / sanctions catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/agritrade/agritradeadvisor.cljc` | **AgriTradeAdvisor** -- `mock-advisor` ‖ `llm-advisor`; intake/biosecurity-verification/delivery/invoice proposals |
| `src/agritrade/governor.cljc` | **Agri Trading Governor** -- 7 HARD checks (spec-basis · evidence-incomplete · credit-uncleared · contract-missing · phytosanitary-certificate-missing · animal-health-certificate-missing · counterparty-sanctions-flag-unresolved) + 2 double-actuation guards + 1 soft (confidence/actuation gate) |
| `src/agritrade/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted verify → supervised (delivery/invoice always human; order intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/agritrade/operation.cljc` | **OperationActor** -- langgraph StateGraph |
| `src/agritrade/sim.cljc` | demo driver |
| `test/agritrade/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers agri-order intake through contract / biosecurity /
sanctions regulatory verification, delivery and invoice settlement --
the core governed lifecycle:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Agri-order intake + per-jurisdiction, per-kind evidence checklisting, HARD-gated on an official spec-basis citation (`:order/intake`/`:biosecurity/verify`) | Real elevator/livestock-yard/ERP integration, routing and trading-book economics |
| Delivery, HARD-gated on full evidence, a credit-cleared counterparty, contract-terms on file, the kind-appropriate biosecurity certificate on file and a passed sanctions screen (`:delivery/dispatch`) | |
| Invoice settlement, HARD-gated on full evidence, a passed sanctions screen and no double-invoice (`:invoice/settle`) | |
| Immutable audit ledger for every intake/verification/delivery/invoice decision | |

Extending coverage is additive: add the next gate (e.g. a residue/
contaminant-testing reconciliation check) as its own governed op with
its own HARD checks and tests, following the SAME "an independent
governor re-verifies against the actor's own records before any
real-world act" pattern this repo's flagship ops already establish.

## Jurisdiction coverage (honest)

`agritrade.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis (for BOTH consignment kinds) in
`agritrade.facts/catalog` -- currently 4 seeded (JPN, USA, GBR, DEU) out
of ~194 jurisdictions worldwide. This is a starting catalog to prove the
governor contract end-to-end, not a claim of global coverage. I do not
have live web access; the headline statute/regulation names and owner-
authority names (植物防疫法 / 家畜伝染病予防法 / MAFF for Japan; the
Plant Protection Act / Animal Health Protection Act / APHIS for the
US; Regulation (EU) 2016/2031 / Regulation (EU) 2016/429 / APHA-style
plant-and-animal-health agencies for the UK; the same two EU
regulations for Germany) are cited from training-time knowledge with
reasonable confidence, but the exact German Länder-level implementing
legislation in particular should be independently verified before this
catalog is relied on operationally. Adding a jurisdiction (or a missing
kind for an already-seeded jurisdiction) is additive: one map entry in
`agritrade.facts/catalog`, citing a real official source -- never
fabricate a jurisdiction's requirements to make coverage look bigger.

## Maturity

`:implemented` -- `AgriTradeAdvisor` + `Agri Trading Governor` run as
real, tested code (see `Run` above), following the SAME governed-actor
architecture as the other prior actors across this fleet, with its own
distinct, independently-named governor and its own direct-entity-
boolean agri-trading checks, including the fleet's first per-
consignment-kind biosecurity-certificate split. See
`docs/adr/0001-architecture.md` for the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
