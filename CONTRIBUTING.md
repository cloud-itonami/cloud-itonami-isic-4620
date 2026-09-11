# Contributing

`cloud-itonami-isic-4620` accepts contributions to the OSS blueprint, the
Agri Trading Governor, decision-rule tests, documentation and operator
model.

## Development
The capability layer is SELF-CONTAINED. There is no pre-existing bespoke
agri-wholesale capability library to wrap; the counterparty-credit /
contract-on-file / phytosanitary-certificate / animal-health-certificate /
sanctions-screening checks live directly in `agritrade.governor`. This repo
holds the business blueprint, the langgraph-clj actor and the operator
contracts.

```bash
kbb -M:dev:test
kbb -M:lint
```

## Rules
- Do not commit real counterparty, credit, biosecurity-certificate or
  sanctions-screening data.
- Keep delivery dispatch and invoice settlement behind the Agri Trading
  Governor.
- Treat agri-wholesale workflows as high-risk: add tests for spec-basis,
  evidence completeness, credit clearance, contract-on-file, phytosanitary-
  certificate verification, animal-health-certificate verification,
  sanctions screening and audit logging.
- Never fabricate a jurisdiction's phytosanitary, animal-health or sanctions
  requirements in `agritrade.facts` -- cite a real official source or leave
  the jurisdiction (or the consignment kind) out of the catalog.
- Never blur the plant-health / animal-health distinction: a grain, feed,
  seed or fibre consignment's `:phytosanitary-certificate?` and a livestock
  consignment's `:animal-health-certificate?` are two different regulatory
  regimes, not two names for the same check. New code and docs must keep
  `agritrade.governor`'s `phytosanitary-certificate-missing-violations` and
  `animal-health-certificate-missing-violations` as separate checks, gated
  on `:consignment-kind`.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests
PRs should describe: what behavior changed, which governor invariant is
affected, how it was tested, whether operator or certification docs need
updates.
