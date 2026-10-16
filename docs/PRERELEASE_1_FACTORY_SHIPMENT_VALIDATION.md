# prerelease-1: Factory-Sourced Frogport Shipment Validation

Status: **ready for dedicated-server testing; not yet live-world verified**.

The local build is reported as successful under the name `prerelease-1`. The corresponding current GitHub Actions build for code commit `13a21f9` also passed: [run #38932320598](https://github.com/Innygr/iCreate-Order-Networking/actions/runs/38932320598). Compilation and unit tests do not prove Create package movement or restart behavior.

## Test-world setup

- Use a disposable copy of the world and the exact prerelease-1 mod list, including the pinned Create version and any optional logistics mods.
- Use one player/account as the order claimant, factory owner, Stock Link binder, and owner of both Terminal Loggers. This avoids mixing permission failures with transport failures during the first pass.
- Set up a source Terminal Logger beside a Create Packager with a Frogport above it. The logger output must face the source Packager.
- Set up a destination Terminal Logger beside its destination Packager/Frogport. Configure the logger face adjacent to the Packager as an input.
- Bind the root Stock Link beside the Order Terminal to the authorized factory. Bind each source Stock Link to that same factory. Confirm the links use the intended Create frequency.
- Put the requested item in at least two distinct source Packager inventories so the shipment must allocate across multiple links. Use identical item components on all stacks.
- Create and claim an order for exactly **8 iron ingots**, with 3 available from one authorized source and 5 from another. Keep unrelated stock in the same network to confirm it is not collected.
- Trigger a Frogport automated shipment from the Order Terminal using the supported UI/action for this build. Record the transaction ID, source counts, destination count, and claimant balance before starting.

## Pass/fail checks

### 1. Exact allocation manifest and collection

- [ ] Shipment journal records one RESERVED transaction and a manifest whose allocation totals exactly 8.
- [ ] The manifest names the intended Stock Links and their associated Packagers.
- [ ] Exactly 3 and 5 ingots are collected from the intended sources; unrelated factory stock is untouched.
- [ ] All collected source stacks carry the same transaction ID.
- [ ] A repeated request using the same transaction ID does not collect cargo a second time.

**Fail immediately** if a different factory's stock is taken, a source allocation changes after authorization, or the transaction count exceeds 8.

### 2. Restart/interruption recovery

- [ ] Save/stop and restart with a RESERVED transaction containing partial transaction-marked cargo at the source logger.
- [ ] On restart, the saved allocation manifest is still identical and recovery tops up only the missing quantity from the same still-authorized source links.
- [ ] If a matching transaction-marked package exists at the source Packager, recovery does not collect replacement stock.
- [ ] If no marked cargo remains at the source and there is no source package to prove its state, the transaction stays pending instead of guessing that new cargo is safe to collect.
- [ ] Recovery is repeatable: running the same recovery path twice does not increase the transaction-marked quantity beyond 8.

The no-source-cargo/no-package case intentionally fails closed because the physical state is ambiguous. It requires investigation rather than automatic re-sourcing.

### 3. Physical movement, destination receipt, and payout

- [ ] Create routes one transaction-marked package through the normal Frogport/Packager transport path.
- [ ] Confirm the destination logger receives exactly 8 matching ingots carrying the same transaction ID.
- [ ] Confirm the journal advances through RESERVED → DISPATCHED → DESTINATION_RECEIVED → COMPLETED.
- [ ] Confirm order progress changes only after the destination receipt is verified.
- [ ] Confirm the reward is credited exactly once.
- [ ] Restart after a durable DESTINATION_RECEIVED record but before payout completion, then confirm payout recovery finishes once without another package being sent.
- [ ] Repeating the same destination receipt is idempotent; a receipt for another destination is rejected.

## Evidence to record

Attach the Minecraft version, Create version, mod list, server log around the transaction, before/after source inventory counts, destination inventory count, transaction UUID, journal stage, order progress, and balance before/after. Mark each check pass/fail; do not describe the route as verified until these checks pass in the running world.
