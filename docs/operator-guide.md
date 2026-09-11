# Operator Guide

## First Deployment
1. Register traders, elevators/yards, agri-orders, and trading
   supervisors.
2. Import agri-order, counterparty, credit, sanctions and trade
   history, tagging each consignment's `:consignment-kind`
   (`:plant`/`:animal`) correctly -- this is what routes which
   biosecurity certificate check applies.
3. Seed the per-jurisdiction, per-kind spec-basis catalog
   (`agritrade.facts`) for the jurisdictions you actually trade in,
   citing real official sources only.
4. Run read-only spec-basis validation per jurisdiction and kind.
5. Configure sanctions / credit escalation and accounts-receivable
   accounts.
6. Publish a dry-run delivery/invoice and audit export.

## Minimum Trading Controls
- spec-basis validation before any verification, delivery, or invoice
- full biosecurity evidence (credit-clearance record, contract/PO,
  sanctions-screening record, phytosanitary or animal-health
  certificate) before any delivery
- credit-clearance, contract-on-file and kind-appropriate biosecurity-
  certificate checks before any delivery; sanctions-screening before
  any delivery AND any invoice
- sanctions / credit escalation gate
- audit export for every delivery, invoice, and hold
- backup manual dispatch and invoicing process

## A Day in the Life: Intake → Verify → Dispatch → Settle → Audit

Wholesale of Agricultural Raw Materials and Live Animals (ISIC 4620,
`cloud-itonami-isic-4620`) runs on the same intake / advise / govern /
decide / commit-or-hold loop as every itonami blueprint, but here the
loop is concrete -- and it forks on WHAT is being traded. Walking
through two orders end to end, one grain and one livestock:

1. **Intake.** The trader books the agri-order through `:forms`:
   order-id, `:consignment-kind` (`:plant` or `:animal`), commodity,
   quantity, unit, counterparty, price, contract-terms, jurisdiction,
   and the order's own diligence record (credit-cleared?,
   sanctions-screened?). This creates an agri-order record at
   `:order/intake` status. The AgriTradeAdvisor only normalizes the
   patch; it does not invent the order-id, counterparty, jurisdiction,
   consignment kind, or any commercial/diligence value.
2. **Verify.** The AgriTradeAdvisor drafts a per-jurisdiction, per-kind
   biosecurity / sanctions evidence checklist (`:biosecurity/verify`)
   from `agritrade.facts`, citing the jurisdiction's official
   spec-basis (owner authority, legal basis, provenance) FOR THIS
   CONSIGNMENT'S KIND -- a grain order gets the phytosanitary
   requirement set, a livestock order gets the animal-health
   requirement set, never the wrong one. The `:agri-trading-governor`
   sign-off gate must clear: it checks the jurisdiction actually has an
   official spec-basis on file for that kind (never invent one). A
   jurisdiction (or kind) with no spec-basis is a HARD hold at the
   governor node -- it never even reaches a human. This verification
   always escalates to a human for approval; it is never auto.
3. **Dispatch.** Before a consignment can leave the elevator or yard,
   the `:agri-trading-governor` sign-off gate runs the full HARD check
   set against the order's own ground truth: the spec-basis exists, the
   evidence checklist is complete, the counterparty's credit has been
   cleared, contract-terms are on file, the KIND-APPROPRIATE
   biosecurity certificate is on file (phytosanitary for grain,
   animal-health for livestock -- never the other one), the
   counterparty has passed sanctions screening, and the order has not
   already been dispatched. Any failure is a HARD hold that a human
   cannot override. If every check is clean, the proposal STILL always
   escalates to a human trading supervisor -- a `:delivery/dispatch`
   never auto-commits at any phase. On approval, the delivery record is
   drafted (`<JURISDICTION>-DELIVERY-000001`) and the order's
   `:dispatched?` flag is set.
4. **Settle.** Once the consignment has actually been dispatched, the
   invoice is settled (`:invoice/settle`): the money side of the trade,
   custody / financial transfer. The governor re-checks the spec-basis,
   the evidence completeness, the sanctions screening, and that this
   order's invoice has not already been settled. As with the delivery,
   a clean invoice STILL always escalates to a human trading supervisor
   -- `:invoice/settle` never auto-commits. On approval the invoice
   record is drafted (`<JURISDICTION>-INVOICE-000001`) and the order's
   `:invoiced?` flag is set.
5. **Audit.** The verification, the delivery sign-off, the delivery
   record, the invoice sign-off, and the invoice record are all
   appended to the `:audit-ledger` -- immutable and exportable, so a
   counterparty, quarantine authority or regulatory dispute can be
   traced back to the exact spec-basis citation, evidence checklist,
   and supervisor sign-off that authorized the delivery and invoice. If
   something is wrong with the counterparty or the consignment (a
   credit deterioration, a sanctions hit, an expired biosecurity
   certificate), that gets raised as a flag and routed through the
   escalation gate instead of being silently suppressed -- a delivery
   for that order then waits on governor sign-off of the flag's
   resolution.

Any deviation from this loop is exactly what the Trust Controls in
`docs/business-model.md` exist to catch: an order verified against a
fabricated spec-basis, a delivery started with incomplete evidence, an
uncleared counterparty credit or a contract gap, a missing or
wrong-kind biosecurity certificate, a sanctions screening suppressed to
force a delivery through, or an invoice posted without a human sign-off.

## Feel the Decision Gate: `kbb -M:dev:run`

This vertical has no companion playable prototype. The fastest hands-on
way to feel why the `:agri-trading-governor` gate exists is the bundled
demo, which walks one clean grain order AND one clean livestock order
through intake → verify → dispatch → settle (each dispatch/settle
pausing for human approval) and then exercises every HARD-hold failure
mode in isolation:

- a jurisdiction with no official spec-basis → HOLD (`:no-spec-basis`),
- a counterparty whose credit has not been cleared → HOLD
  (`:credit-uncleared`),
- an order with no contract-terms on file → HOLD (`:contract-missing`),
- a grain consignment with no phytosanitary certificate on file → HOLD
  (`:phytosanitary-certificate-missing`),
- a livestock consignment with no animal-health certificate on file →
  HOLD (`:animal-health-certificate-missing`),
- a counterparty that has not passed sanctions screening → HOLD
  (`:counterparty-sanctions-flag-unresolved`),
- a double delivery of the same order → HOLD (`:already-dispatched`),
- a double invoice of the same order → HOLD (`:already-invoiced`).

Each HOLD settles at the governor node and never reaches a human
approver -- the same failure mode the audit ledger is built to catch and
the minimum trading controls above are built to prevent. It is not a
substitute for those controls, but it is the fastest way for a new
operator (or a reviewer) to feel, hands-on, why the gate exists before
touching a real deployment.

## Certification
Certified operators must prove spec-basis-grounded verification,
evidence-backed delivery readiness (credit-clearance, contract-on-file,
the correct kind-appropriate biosecurity certificate, sanctions-
screening), and human review for every delivery- and invoice-affecting
action.
