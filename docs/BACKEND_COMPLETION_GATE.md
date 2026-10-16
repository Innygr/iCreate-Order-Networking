# Backend Completion Gate

This checklist separates code implementation from the dedicated-server test session. A green Gradle build is necessary, but does not prove transport compatibility or end-to-end operation in the target modpack.

## Implemented in code

- [x] Server-authoritative order creation, claim, fulfilment, cancellation, and team visibility.
- [x] Escrow-backed rewards and transaction-keyed idempotent credits/refunds.
- [x] Persistent order data, automated-delivery reservations, and durable shipment journal stages.
- [x] Recovery-safe order-progress markers and payout retry after a destination receipt is durably recorded.
- [x] Common automated-delivery preparation API with server-thread, order, claimant, item, quantity, route, and adapter authorization checks.
- [x] Server-authoritative automated-shipment packet with terminal reachability/ownership checks, bounded fields, server-resolved order item/claimant, durable dispatch, and recovery-safe failure messaging.
- [x] Create Redstone Requester bridge for no-reward restock orders; active same-owner/item orders suppress repeats.
- [x] Create Portable Storage Interface train adapter: transaction-marked cargo reservation, guarded dispatch, and independent destination receipt verification.
- [x] Aeronautics Docking Connector adapter with the same durable transaction and destination-receipt boundary.
- [x] Terminal Logger sided inventory capability with filtering, input/output faces, bounded per-tick movement, and protection against external extraction of shipment-reserved cargo.
- [x] Player-facing Order Terminal screen wired to server-authoritative create, claim, manual fulfilment, cancel, and automated shipment actions.
- [x] Native unit tests for order data, shipment journal transitions/save-load, delivery receipt validation, adapter registry, preparation/request validation, and economy persistence.

## Remaining verification before calling the backend complete

- [ ] Verify Create train PSI and Aeronautics docking behavior against the exact mod versions in the target modpack. A registered adapter is implemented code, not proof that its live endpoint API works in-game.
- [x] Implement the Packager-mediated Frogport adapter: a correctly oriented Create Packager sits against each Terminal Logger, with the Frogport above the Packager; reserved cargo remains transaction-marked inside Create packages and cannot be fulfilled without a matching destination receipt.
- [ ] Verify the Packager/Frogport address configuration, physical package movement, marker preservation, and receipt/payout lifecycle in the exact target modpack.
- [ ] Confirm that belt/funnel routes remain unavailable unless a real adapter is implemented and verified for those transports.
- [ ] Run the full Gradle test/build suite on the final branch head.
- [ ] Test on a dedicated server: create and claim an order, reserve cargo, dispatch, receive the exact marked cargo, finalize order progress and payout, then repeat the same transaction to verify idempotency.
- [ ] Restart the dedicated server with shipments at RESERVED, DISPATCHED, and DESTINATION_RECEIVED; verify reconciliation never invents a receipt or duplicates cargo/payout.
- [ ] Test denied terminal access, mismatched owner/team, invalid route references, unloaded endpoints, incomplete cargo, and attempts to extract reserved cargo through other item-handler consumers.
- [ ] Verify the terminal screen and both adapters in the target modpack; confirm the client cannot bypass server-side permissions or shipment validation.

## Factory stock sourcing boundary

- [x] Persist explicit Stock Link-to-factory bindings and revalidate owner/team access.
- [x] Build order-bound sourcing plans only for the current claimant and filter each stock offer by its own binding.
- [x] Connect the authorization-filtered sourcing plan to real Create Packager-mediated Frogport requests. The factory dispatch service rechecks every stock-link binding, collects the requested item from multiple distinct authorized packager inventories, marks cargo with the durable transaction identity, and hands the reserved cargo to the Frogport adapter.
- [ ] Persist the exact multi-stock-link allocation manifest in the shipment journal and recover partially collected factory cargo across a crash. Current recovery handles a complete marked source-logger reservation and a complete already-created source package idempotently; partial collection/package states deliberately remain pending rather than being guessed or sourced again.
- [ ] Add a dedicated-server test proving a shared Create frequency cannot expose or consume another factory's stock.

Do not describe factory sourcing as end-to-end until the unchecked items above are implemented and exercised in-game.

## Test handoff

Keep these states distinct during testing:
1. adapter registration vs. an operational route;
2. cargo preparation vs. committed dispatch;
3. transport movement vs. independently verified destination receipt;
4. receipt vs. finalized order progress and payout;
5. recovery of persisted state vs. a fresh-process-only success.

Do not mark a transport complete merely because the compatibility class detects a block or the project compiles.
