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

1. **Dedicated-server validation of factory-sourced Frogport shipments**: real package movement, exact destination receipt, order progress, and one-time payout.
2. **Exercise restart recovery in a disposable world** at RESERVED, DISPATCHED, and DESTINATION_RECEIVED. Unit tests cover manifest persistence and recovery guards; they cannot simulate the full Create transport lifecycle.
3. **Test interrupted multi-link collection** with a partial marked stack at the source. Recovery must use the saved manifest and current authorization, and must never recollect stock if a matching package already exists.
4. Investigate any RESERVED transaction where neither marked source cargo nor a matching source package exists. This state is deliberately left pending because the physical shipment state is ambiguous; automatic replacement collection could duplicate cargo.
5. Continue live-world validation for train/PSI, Aeronautics docking, Redstone Requester behavior, Display Link registration, and Terminal Logger chunk loading. Belt delivery remains unsupported until an independently verified receipt path exists.

## Current recovery hardening

- The durable journal stores the exact factory allocation manifest (Stock Link position, associated Packager position, and allocated count) before source inventory is mutated.
- Added regression tests for manifest save/load, exact allocation totals, preventing manifest replacement, immutable manifest access, and cleanup on abandoned/completed transactions.
- Fixed a dangerous recovery edge: if a transaction-marked package still exists at its source Packager, recovery no longer tops up the source logger from factory stock.
- Recovery now only tops up **partial transaction-marked cargo already present in the source logger**, and only when no matching package exists at the source. An absent source marker is ambiguous and fails closed.
- Added unit tests for the recovery guard covering partial cargo, zero cargo, complete/over-counted cargo, and an existing package.

## Completion estimates

These are planning estimates based on implemented code and the remaining feature/test list, not measured test results:

- **Backend implementation: ~85–90%.** Core order/economy logic, GUI actions, durable shipment stages, idempotency, and three transport adapter implementations exist. Remaining work is chiefly live-world validation, lifecycle/restart test coverage, Stock Link authorization/binding, and belt-to-order delivery.
- **Whole mod: ~65%.** The core backend and most current block/item models and recipes are in place. The remaining share includes polishing and verifying models in-game, completing missing transport/Stock Link behavior, testing permissions and recovery on a dedicated server, and release cleanup. The estimate assumes the current model work is substantially complete; it does not treat model work as wholly outstanding.

## Latest verified CI and prerelease

- Local build name reported by the project owner: `prerelease-1` (successful).
- Latest code build before the validation-doc update: [GitHub Actions run #38932320598](https://github.com/Innygr/iCreate-Order-Networking/actions/runs/38932320598) passed on commit `13a21f9`, including the new manifest and recovery-guard tests.
- The build verifies compilation and automated tests, not physical Create package movement or restart behavior.

## Next implementation order

1. Run [the prerelease-1 factory shipment validation checklist](PRERELEASE_1_FACTORY_SHIPMENT_VALIDATION.md) in a disposable dedicated-server world.
2. Capture results for exact multi-link allocation, interrupted collection recovery, package movement, destination receipt, and payout retry.
3. Fix any failures before treating factory-sourced Frogport delivery as verified.
4. Continue live-world validation of train/PSI, Aeronautics docking, Redstone Requester, Display Links, and Terminal Logger chunk loading. Keep belt routes unavailable until destination receipt is independently verified.
