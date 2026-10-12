# Create and Create: Aeronautics compatibility

Target platform: Minecraft 1.21.1, NeoForge 21.1.251, Java 21.

## Compatibility policy

- Create is a required dependency for the intended Create-focused release.
- Create: Aeronautics is optional. The mod must remain loadable without Aeronautics.
- Aeronautics-specific classes must not be referenced from common code unless they are isolated behind a verified optional integration.
- The core marketplace stores vanilla registry item IDs and uses server-authoritative order/economy services. It must not assume an order terminal is stationary; Aeronautics vehicles can move contraptions through the world.
- Order persistence is server-wide (overworld SavedData), not per dimension or vehicle.
- Client requests are untrusted. Validate item IDs, counts, ownership, claimant identity, and balances on the server.

## Runtime detection

`compat/CreateCompat.java` detects Create, Aeronautics, and Sable by mod ID without importing their classes. `isAeronauticsStackAvailable()` returns true only when all three are loaded.

## Delivery routes (required design constraint)

Every order must support all four delivery paths:

1. **Manual delivery** — a player physically brings the requested items to the order handoff/terminal and submits them for server validation.
2. **Train delivery** — items arrive through a Create train logistics workflow.
3. **Airship delivery** — items arrive aboard an Aeronautics airship or moving contraption.
4. **Frogport delivery** — items arrive through Create's package/Frogport logistics network. Frogport is part of Create's logistics ecosystem; it should not be treated as an Aeronautics API or assumed to require a separate Frogport mod.

The order lifecycle and escrow ledger must be transport-agnostic. Each route should submit a delivery request to one shared server-side fulfilment service; that service validates the order, claimant/route permissions, item identity and count, consumes or transfers the actual items, updates order progress, and pays from escrow exactly once. Route adapters must never directly edit order counts or issue currency.

Delivery requests should carry an order ID, item ID, quantity, route type, and a server-verifiable source/transfer context. Do not trust client-supplied counts, vehicle IDs, or claimed arrival events. Make repeated callbacks idempotent so a train/airship/frogport event cannot pay twice after retries or chunk unloads.

Manual delivery must always remain available even when optional Create-family logistics integrations are missing. Train, airship, and Frogport adapters should be optional, isolated, and activated only when their actual Create/Aeronautics hardware and server-side API are detected. Train route detection targets Create's Portable Storage Interface; airship route detection targets Aeronautics Docking Connectors; belt infrastructure detection is isolated in `CreateBeltCompat`. For Frogports, target Create's package/Frogport workflow and verify its package contents/address APIs against the exact Create 6.0.x build before writing the adapter. For train and airship delivery, verify the server-side inventory/cargo access path for the installed Create/Aeronautics/Sable builds before writing adapters.

## Aeronautics support boundary

This detection layer prevents optional-mod class-loading failures, but it is not yet a custom Aeronautics contraption capability or moving-terminal implementation. No Aeronautics API is called until its public API and lifecycle are verified. A terminal block attached to a moving contraption may need a dedicated interaction/storage design rather than assuming ordinary block-entity ticking and world positions remain stable.

## Delivery abstraction added

The `delivery` package now defines `DeliveryRoute`, `DeliveryRequest`, `DeliveryResult`, `AutomatedDeliveryAdapter`, and `DeliveryService`. Manual delivery delegates to the existing server-side inventory fulfilment. Train, airship, and Frogport requests are deliberately fail-closed and return `NOT_IMPLEMENTED` until their adapters can participate in one atomic cargo-consumption + order-progress + payout + persistent-receipt transaction. This avoids consuming cargo without paying, or paying twice on retries.

Frogport is treated as Create's built-in package Frogport, not a separate mod. Detection uses the Create registry item ID `create:package_frogport`; the actual package-address/routing API still needs validation against the selected Create release. The adapter interface is only a boundary, not evidence of completed train/airship/Frogport support.

## Implemented backend pieces (not yet build-verified)

- `delivery/DeliveryRoute` defines MANUAL, TRAIN, AIRSHIP, and FROGPORT.
- `delivery/DeliveryRequest` carries transport-neutral request metadata. It is not proof that cargo arrived.
- `delivery/AutomatedDeliveryAdapter` defines the server-side adapter boundary and requires route authorization before cargo removal.
- `delivery/DeliveryService` is the shared route entry point. Manual delivery currently delegates to `OrderService.fulfil`; automated routes deliberately fail closed with `NOT_IMPLEMENTED` until a safe transaction can consume real cargo, progress the order, pay escrow, and persist an idempotency receipt.
- `orders/OrderData` persists delivery receipt IDs, order IDs, and route names for idempotency tracking.

The transport-specific adapters for trains, airships, and Frogports are not implemented yet. The current generic service is not proof that those integrations already work. The receipt ledger is not yet connected to an atomic automated-delivery transaction, and crash consistency/payout rollback need explicit testing.

## Verification checklist

- [ ] Build against NeoForge 21.1.251 / Java 21.
- [ ] Launch with Create only.
- [ ] Launch with Create + Sable + Create: Aeronautics 1.21.1.
- [ ] Launch without Aeronautics to verify optional classes are never loaded.
- [ ] Create/claim/fulfil/cancel an order from normal terrain.
- [ ] Repeat order interactions while riding/moving in an Aeronautics contraption once terminal interaction support is implemented.

## Delivery backend implementation status

- Manual handoff uses the shared order service and accepts a transaction UUID for retry deduplication.
- The automated adapter contract is reservation-based: adapters must prepare real cargo, commit it idempotently by transaction ID, and support rollback.
- Automated delivery validates the claimed order, expected item registry ID, route availability, actor/claimant, quantity, and payout capacity on the server.
- Economy payouts have persistent transaction receipts, so a replay of the same transaction cannot grant the same reward twice.
- Train, Aeronautics airship, and Create package-Frogport adapters are not implemented yet. Until a verified adapter is registered, automated routes fail closed and leave cargo untouched.
- The cross-mod adapter must be tested against the exact Create/Aeronautics APIs before being marked available. The current branch has not yet had a verified successful build or in-game test.

## Create belts and Terminal Logger

- **Frogport:** treat it as Create's built-in package Frogport, not a separate mod dependency. The current compatibility helper checks the Create registry ID and must not be interpreted as a working package-routing adapter.
- **Belts:** Create belt infrastructure is detected separately from order delivery. Belt support must validate cargo at a server-owned belt endpoint; merely seeing a belt connector block is not proof that a shipment arrived.
- **Terminal Logger:** planned as a six-sided funnel/router block. Each of the six faces can be enabled as an input, one face is selected as the configurable output, and the output face is never simultaneously an input. The block entity owns and persists this configuration.
- Terminal Logger routing is not allowed to change order counts or pay rewards directly. It hands a verified cargo transfer to the shared delivery service and records a transaction UUID so repeated transfer callbacks cannot duplicate fulfilment.
- The first backend step is the serializable routing configuration. A registered block, block entity, menu/screen, sided inventory transfer logic, and Create belt/frogport adapters are still required before the in-game block works.

## Terminal Logger block

The initial Terminal Logger block is registered as `icreate_order_networking:terminal_logger`. Its block entity persists:
- a configurable output direction (six faces);
- accepted/rejected delivery counters;
- a bounded last-status message for diagnostics.

Right-clicking cycles the output through all six faces. This first backend step provides the persistent settings/logging surface; it does **not** yet implement the full six-sided item funnel, menu/config screen, Create belt attachment, or a physical inventory transfer. Those need to be wired to verified server-side transfer endpoints.

Compatibility checks are separated by hardware:
- `CreateBeltCompat`: Create belt connector registry detection.
- `CreateTrainCompat`: Create Portable Storage Interface registry detection.
- `AeronauticsDockingCompat`: optional Aeronautics Docking Connector detection.
- `CreateFrogportCompat`: Create package Frogport detection.

These are registry-presence checks only, not proof that an adapter is operational. Automated delivery remains unavailable until a real adapter is registered and validated.
