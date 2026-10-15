# Backend Completion Gate

This checklist is the handoff between backend implementation and the planned block-model / in-game test session. A green Gradle build is necessary, but it does not mean the order network is end-to-end operational.

## Implemented foundations

- [x] Server-authoritative order creation, claim, fulfilment, cancellation, and team visibility.
- [x] Escrow-backed rewards and transaction-keyed idempotent credits/refunds.
- [x] Persistent order data and automated-delivery reservation receipts.
- [x] Recovery-safe progress markers for automated deliveries and cancellation protection while shipments are pending.
- [x] Common automated-delivery preparation API with server-thread, order, claimant, item, quantity, route, and adapter authorization checks.
- [x] Create Redstone Requester bridge: a powered requester adjacent to a configured Order Terminal can create no-reward restock orders; active same-owner/item orders suppress repeats.
- [x] Terminal Logger sided inventory capability with filtering, input/output faces, and bounded per-tick movement.
- [x] Native unit tests for order data, delivery receipt validation, adapter registry, preparation/request validation, and economy persistence.

## Required before calling the backend complete

- [ ] Implement at least one real automated transport adapter end to end. Registry/compatibility detection alone is not a transport implementation.
- [ ] A shipment must persist its transaction before dispatch, reserve cargo exactly once, commit idempotently, and only fulfil/pay after the destination independently confirms receipt.
- [ ] Recover pending shipments after a server restart; adapters must reconcile their real transport state instead of guessing whether cargo arrived.
- [ ] Wire the Order Terminal's server-side actions to the authoritative order/network services, including permission checks and useful success/failure results.
- [ ] Connect Terminal Logger transfers to shipment/job accounting; generic inventory movement and statistics are not proof of an order delivery.
- [ ] Verify Create train PSI, belt/funnel, Frogport, and Aeronautics docking behavior against the exact supported mod versions. Keep routes unavailable unless the corresponding adapter is genuinely verified.
- [ ] Confirm the Mythic backend boundary: dedicated-server only, no privileged action trusted from client packets, and no secrets or server-only services exposed to clients.
- [ ] Run the complete Gradle test/build suite and then test restart recovery and real item movement in a dedicated server with the target modpack.

## Tomorrow's modeling/testing handoff

Modeling can proceed independently, but the in-game test should explicitly distinguish:
1. registry detection from an operational route;
2. cargo preparation from cargo commit;
3. transport movement from destination receipt;
4. destination receipt from finalized order progress and payout;
5. successful restart recovery from a fresh-process-only success.

Do not mark a transport as complete merely because its compatibility class detects a block/item ID or the project compiles.
