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
4. **Frogport delivery** — items arrive through the frogport logistics system exposed by the installed mod stack.

The order lifecycle and escrow ledger must be transport-agnostic. Each route should submit a delivery request to one shared server-side fulfilment service; that service validates the order, claimant/route permissions, item identity and count, consumes or transfers the actual items, updates order progress, and pays from escrow exactly once. Route adapters must never directly edit order counts or issue currency.

Delivery requests should carry an order ID, item ID, quantity, route type, and a server-verifiable source/transfer context. Do not trust client-supplied counts, vehicle IDs, or claimed arrival events. Make repeated callbacks idempotent so a train/airship/frogport event cannot pay twice after retries or chunk unloads.

Manual delivery must always remain available even when optional Create-family logistics integrations are missing. Train, airship, and frogport adapters should be optional, isolated, and only activated when their actual mod/API is detected. Do not assume Frogport is an Aeronautics API; identify its owning mod and supported version before writing an adapter.

## Aeronautics support boundary

This detection layer prevents optional-mod class-loading failures, but it is not yet a custom Aeronautics contraption capability or moving-terminal implementation. No Aeronautics API is called until its public API and lifecycle are verified. A terminal block attached to a moving contraption may need a dedicated interaction/storage design rather than assuming ordinary block-entity ticking and world positions remain stable.

## Verification checklist

- [ ] Build against NeoForge 21.1.251 / Java 21.
- [ ] Launch with Create only.
- [ ] Launch with Create + Sable + Create: Aeronautics 1.21.1.
- [ ] Launch without Aeronautics to verify optional classes are never loaded.
- [ ] Create/claim/fulfil/cancel an order from normal terrain.
- [ ] Repeat order interactions while riding/moving in an Aeronautics contraption once terminal interaction support is implemented.
