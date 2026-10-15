# Backend Progress — 2026-10-15

This snapshot separates code on `main` from work proposed in an open pull request. A successful Java/Gradle CI run proves compilation and automated tests only; it does not prove live Minecraft/Create behavior.

## Current `main` foundations

- Persistent orders, server-side order validation, claim/fulfil/cancel flows, and economy-ledger safeguards.
- Delivery receipt matching, transaction reservations, idempotency checks, and recovery paths for progress already durably applied.
- A shared automated-delivery preparation boundary that fails closed when a verified route adapter is absent.
- Terminal Logger sided item-handler routing, persistent Network Statistics, factory identity binding, and scoped Display Link statistics foundations.
- Create Redstone Requester bridge for zero-reward restock orders, with duplicate-active-order suppression; live-world event timing remains unverified.
- GitHub Actions Java 21 / Gradle CI and JUnit regression coverage.

## Work still required

1. Complete and test durable shipment dispatch, destination receipt, payout retry, and restart recovery as one connected lifecycle.
2. Add broader manual-handoff success, partial-transfer, invalid-request, authorization, and duplicate-transaction tests.
3. Implement independently verified cargo adapters for Create trains/Portable Storage Interfaces, Aeronautics Docking Connectors, Create Frogports, and belts/Terminal Loggers. Do not register placeholder adapters: an adapter must reserve cargo, verify actual destination receipt, and recover safely.
4. Implement server-authoritative Order Terminal menu actions and then connect the player-facing GUI.
5. Verify Redstone Requester behavior, Display Link registration, and any Stock Link integration in a live world against the pinned Create version.

## Open proposed patch

[PR #15 — durable shipment dispatch and receipt journal](https://github.com/Innygr/iCreate-Order-Networking/pull/15) proposes a persistent shipment journal with `RESERVED → DISPATCHED → DESTINATION_RECEIVED → COMPLETED` milestones and idempotent payout retry. The PR description explicitly notes that an automatic startup recovery hook and concrete transport adapter are not included. Treat this as proposed work until merged and CI-verified; do not describe it as part of `main` yet.

## Completion estimates

- **Backend:** previously tracked at 85%; this is a planning estimate, not a test result. The durable shipment lifecycle and real transport adapters remain meaningful gaps.
- **Whole mod:** previously tracked at 45%; the order GUI, route adapters, and end-to-end/live-world verification remain incomplete.

## Next implementation order

1. Review/CI-verify the durable shipment journal patch.
2. Add startup reconciliation for pending journal entries, ensuring only destination-received transactions can retry payout and that replay never re-dispatches cargo blindly.
3. Add lifecycle tests at each persisted boundary, including save/reload and duplicate calls.
4. Implement one route adapter end-to-end and keep the other routes unavailable until individually verified.
5. Update the README only when merged code and CI results justify a status change.
