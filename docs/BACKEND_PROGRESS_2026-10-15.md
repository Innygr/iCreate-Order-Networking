# Backend Progress — refreshed 2026-10-16

This snapshot describes the current `main` branch. A successful Java/Gradle CI run proves compilation and automated tests only; it does not prove live Minecraft/Create behavior.

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

## Merged implementation

[PR #15 — durable shipment dispatch and receipt journal](https://github.com/Innygr/iCreate-Order-Networking/pull/15) was merged into `main`. The durable journal, startup payout recovery hook, transaction-marked PSI/Aeronautics/Frogport adapters, missile/component recipes, and unloaded-endpoint guard are now in the main branch. CI confirms compilation and automated tests only; live transport behavior still needs a dedicated-server session.

## Completion estimates

These are planning estimates based on implemented code and the remaining feature/test list, not measured test results:

- **Backend implementation: ~85–90%.** Core order/economy logic, GUI actions, durable shipment stages, idempotency, and three transport adapter implementations exist. Remaining work is chiefly live-world validation, lifecycle/restart test coverage, Stock Link authorization/binding, and belt-to-order delivery.
- **Whole mod: ~65%.** The core backend and most current block/item models and recipes are in place. The remaining share includes polishing and verifying models in-game, completing missing transport/Stock Link behavior, testing permissions and recovery on a dedicated server, and release cleanup. The estimate assumes the current model work is substantially complete; it does not treat model work as wholly outstanding.

## Latest verified CI

The latest `main` build, run [#38907087141](https://github.com/Innygr/iCreate-Order-Networking/actions/runs/38907087141), passed after correcting the Minecraft 1.21.1 `ServerLevel#setChunkForced(int, int, boolean)` call used by Terminal Logger chunk loading. This confirms compilation and automated tests on that commit; it does not validate forced-chunk lifecycle behavior in a running world.

## Next implementation order

1. Add bounded/configurable chunk-loading ownership and lifecycle tests so multiple loggers in one chunk cannot accidentally unforce one another.
2. Add dedicated-server recovery tests across RESERVED, DISPATCHED, and DESTINATION_RECEIVED milestones.
3. Implement Stock Link factory binding and explicit access checks.
4. Validate PSI train and Packager/Frogport shipment flow live; keep belt routes unavailable until independently verified receipt exists.
5. Update the completion gate only after those tests pass.
