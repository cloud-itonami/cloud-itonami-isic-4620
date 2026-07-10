# Governance

`cloud-itonami-isic-4620` is an OSS open-business blueprint for a wholesaler
of agricultural raw materials and live animals -- grain, feed, seed and fibre
consignments AND livestock consignments, traded and dispatched under one
counterparty-diligence + biosecurity governance loop (ISIC 4620, "wholesale of
agricultural raw materials and live animals").

## Maintainers
Maintainers may merge changes that preserve these invariants:
- an agri-order whose jurisdiction (and consignment kind) has no official
  phytosanitary/animal-health/sanctions spec-basis can never be verified,
  dispatched or invoiced.
- the Agri Trading Governor remains independent of the advisor.
- hard governor violations (a fabricated spec-basis, incomplete biosecurity
  evidence, an uncleared counterparty credit, a missing contract, a missing
  phytosanitary certificate on a plant consignment, a missing animal-health
  certificate on an animal consignment, an unresolved sanctions-screening
  flag, a double delivery or a double invoice) cannot be overridden by human
  approval.
- every intake, verification, delivery, settlement and hold is auditable.
- counterparty, credit, biosecurity-certificate and sanctions data stays
  outside Git.
- the plant-health and animal-health certificate checks stay two distinct,
  independently-named governor rules -- code must not collapse them into one
  generic 'certificate-missing' check that would blur which regime failed.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or license
should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is a
separate trust mark and should require security, audit and data-flow review.

Certified operators can lose certification for:
- bypassing delivery-dispatch or invoice-settlement policy checks
- mishandling counterparty, credit, biosecurity-certificate or
  sanctions-screening data
- misrepresenting certification status
- failing to respond to security incidents
