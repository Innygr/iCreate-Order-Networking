# Factory Stock Dispatch Design

This document records the Create 6.0.x API boundary for turning an authorized Stock Link offer into a real, durable order shipment. It is not a claim that factory sourcing is implemented end-to-end.

## Verified Create API shape

The target Create 1.21.1 source exposes:

- `PackagerLinkBlockEntity.processRequest(ItemStack stack, int amount, String address, int linkIndex, MutableBoolean finalLink, int orderId, PackageOrderWithCrafts context, IdentifiedInventory ignoredHandler)`, which returns a packager/request pair or `null`.
- `PackagerBlockEntity.attemptToSend(List<PackagingRequest> queuedRequests)`, which consumes item-specific `PackagingRequest` entries from the linked packager's target inventory.
- `PackagingRequest.create(...)` creates the request, and the request tracks a mutable remaining count.

The Stock Link summary exposed by `CreateStockLinkCompat` is read-only. A summary is a discovery snapshot, not a stock reservation and not proof that any package moved.

## Required transaction order

A factory-source dispatch implementation must preserve this order:

1. Validate the current order, claimant, item, quantity, Stock Link binding, owner/team permissions, loaded source chunk, Create frequency, and source endpoint binding.
2. Atomically reserve the order quantity in `OrderData` and persist a shipment journal row before asking Create to move items.
3. Reserve the physical source quantity. The current endpoint interface only reports `availableCount`; it does not provide an atomic reservation contract yet. Do not treat a second summary read as a reservation.
4. Submit an item-specific Create `PackagingRequest` through the linked Packager. Do not call `attemptToSend(null)` for order sourcing: that path performs generic inventory packing rather than a transaction-bound item request.
5. Associate the transaction ID with the exact package/cargo before it can be accepted as a valid delivery. Never mutate the requested stack's components in a way that makes Create's source-stack match fail.
6. Persist dispatch only after the package is verifiably held/queued with the expected item, quantity, route, and transaction marker.
7. Apply order progress and payout only after an independent destination receipt matches the durable transaction.
8. On restart, reconcile source reservation, Create-held/queued packages, in-flight transport, and destination receipt. An ambiguous state must remain reserved for recovery; it must not be retried as a fresh shipment.

## Security invariants

- A shared Create frequency is not an iCreate permission boundary. The root Stock Link and every source endpoint must each have an authorized persisted factory binding.
- Revalidate bindings, network ID, source position, item identity, and available quantity immediately before physical reservation.
- Never dispatch against an unloaded source chunk or a source whose owner/team access has changed.
- Duplicate transaction IDs must reconcile to the existing journal row, not submit a second Create request.
- A null/failed Create request may release a reservation only when the adapter can prove that no cargo was extracted and no package was created.
- Partial package creation must retain the durable reservation until recovery can account for every extracted item.

## Completion gate

The implementation is not complete until the endpoint exposes a real reservation mechanism, request submission is wired into the order/shipment lifecycle, and tests cover shared-frequency isolation, partial dispatch, duplicate retries, and restart recovery. Dedicated-server verification must confirm actual Create package movement and marker preservation in the target modpack.
