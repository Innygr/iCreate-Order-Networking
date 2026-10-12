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

`compat/CreateCompat.java` detects Create, Aeronautics, and Sable by mod ID without importing their classes. `isAeronauticsStackAvailable()` returns true only when all three are loaded. Belt/funnel and Frogport checks belong to base Create compatibility; neither should require Aeronautics or Sable. `CreateBeltCompat` checks Create belt/funnel registry entries, while `CreateFrogportCompat` checks the built-in package Frogport registry entry.

## Delivery routes (required design constraint)

Every order must support the four core delivery paths plus belt-based local routing:

1. **Manual delivery** — a player physically brings the requested items to the order handoff/terminal and submits them for server validation.
2. **Train delivery** — items arrive through a Create train logistics workflow.
3. **Airship delivery** — items arrive aboard an Aeronautics airship or moving contraption.
4. **Frogport delivery** — items arrive through Create's built-in package/Frogport logistics network. Frogport is base Create, not Aeronautics and not a separate mod.
5. **Belt delivery** — items arrive through Create belts into a Terminal Logger input face; the logger records the actual item transfer before the shared order service fulfils the order.

The order lifecycle and escrow ledger must be transport-agnostic. Each route should submit a delivery request to one shared server-side fulfilment service; that service validates the order, claimant/route permissions, item identity and count, consumes or transfers the actual items, updates order progress, and pays from escrow exactly once. Route adapters must never directly edit order counts or issue currency.

Delivery requests should carry an order ID, item ID, quantity, route type, and a server-verifiable source/transfer context. Do not trust client-supplied counts, vehicle IDs, or claimed arrival events. Make repeated callbacks idempotent so a train/airship/frogport event cannot pay twice after retries or chunk unloads.

Manual delivery must always remain available even when optional Create-family logistics integrations are missing. Train, airship, and Frogport adapters should be optional, isolated, and activated only when their actual Create/Aeronautics hardware and server-side API are detected. Train adapters should use Create Portable Storage Interfaces (`create:portable_storage_interface`) as the train handoff boundary; airship adapters should use Aeronautics Docking Connectors as the airship handoff boundary. Belt infrastructure detection is isolated in `CreateBeltCompat`. Belt detection is only a capability check, not proof that cargo reached the logger. Frogport targets Create's built-in package/Frogport workflow; verify its package contents/address APIs against the exact Create 6.x build before writing the adapter. For train and airship delivery, verify the server-side inventory/cargo access path for the installed Create/Aeronautics/Sable builds before writing adapters.

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

## Terminal Logger

The Andesite Terminal Logger is intended as an early-game six-face logistics junction. Right-click cycles the selected output face through all six directions; the other five faces are input faces. It pulls from neighboring NeoForge item-handler capabilities into an 18-slot buffer, then pushes buffered stacks out through the selected face. This supports PSI and Docking Connector endpoints only when their docked state exposes a standard item-handler capability. A direct Create API adapter is still required if a specific endpoint does not expose that capability.

The Brass Terminal Logger upgrades the same block with an optional item-ID filter. Right-clicking with an item sets the filter; sneaking with an empty hand clears it. Its recipe uses the base logger, brass sheet, Create filter, and brass funnel. The base recipe uses iron nuggets, redstone, an andesite funnel, and andesite alloy; it does not require a shaft. Both recipes are intended to stay early-game accessible.

The buffer and output direction are saved with the block entity. The logger's accepted/rejected counters are diagnostics only: a transfer log entry does not by itself fulfil an order or issue a payout.

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


## Terminal Logger block variants

- **Andesite Terminal Logger** is the low-cost early-game six-port routing block. Its output face cycles through all six directions; the other faces are available for route inputs.
- **Brass Terminal Logger** uses brass casing and adds a persistent item filter. Right-click with an item to sample it as the filter; sneak-right-click with an empty hand to clear it.
- Both variants share the same block entity, saved output direction, and accepted/rejected delivery diagnostics. The filter predicate is exposed to transport adapters; it is not a replacement for server-side cargo verification.
- Create belt compatibility detection is isolated in `CreateBeltCompat`. Registry detection only reports that belt/funnel infrastructure exists; actual belt handoff must be wired through a server-side adapter and verified against the target Create API.
- The recipes are intended to be early-game: the andesite variant uses andesite casing instead of a shaft; the brass variant uses brass casing. The optional portable device is not required for crafting either logger.

## Terminal Logger block design

The Terminal Logger is the physical logistics handoff/diagnostic block:

- **Andesite Terminal Logger:** inexpensive early-game recipe, no item filter, six configurable faces with one selected output face, and emits light.
- **Brass Terminal Logger:** still approachable in the early Create progression, supports an item filter, the same six-way output selection, and emits light.
- The configured output can be any of the six directions; the other five faces are candidate inputs. A future GUI should expose a six-direction selector, filter slot (brass only), accepted/rejected counters, last status, and a resettable diagnostic view.
- Output direction and filter must persist in the block entity. Route handlers should report accepted/rejected shipments to its logger counters only after server-side validation.
- Create belt compatibility is detected separately from actual belt transfer. The live adapter must validate the real item transfer into the logger; block-ID detection alone is not proof of delivery.
- Train delivery uses a Create Portable Storage Interface handoff. Airship delivery uses an Aeronautics Docking Connector. These are the intended integration points, but actual API adapters remain unavailable until verified against the pinned mod versions.
- Keep recipes affordable and craftable early: prefer common Create/vanilla ingredients and avoid expensive progression-gated components unless the brass filter variant genuinely needs brass. Recipe balance must be checked in a real recipe viewer/in-game.

## Terminal Logger and Scrapper block specification

These are distinct blocks with distinct responsibilities. Do not add a light-emission property to the Terminal Logger.

### Terminal Logger
- **Purpose:** server-side delivery checkpoint and six-way item router/observer. It records actual item transfer into its inventory/connected logistics endpoint and associates that transfer with an order; it must never accept a client-only “arrived” message as proof.
- **Shape:** a six-direction funnel-style block with a configurable output direction. The selected output direction must be persisted by the block entity and restored after chunk/world reloads.
- **Output configuration:** use the current server-authoritative interaction to cycle through all six faces; a future wrench/UI can provide direct selection. The brass tier additionally supports Create-style item filters (allow/deny item predicates); the andesite tier uses simple unfiltered routing.
- **Transfer safety:** only route items accepted by the active order/filter. Keep unaccepted items in the input buffer or return them to the source; never void them. Each transfer gets a stable shipment/transaction UUID, and a repeated callback for the same shipment cannot advance progress or pay twice.
- **No light emission:** Terminal Logger has no glow/light emission. It should use ordinary block lighting.
- **Early-game crafting:** andesite tier should be craftable from early-game materials, using andesite alloy/andesite casing and basic Create components rather than brass-only ingredients. Brass tier may require brass but should remain a modest, reachable upgrade. Keep recipes cheap; avoid recipes requiring late-game or addon-only parts.
- **Backend boundary:** block entity may observe/reserve cargo and report a verified transfer to DeliveryService; it must not edit order progress or balances directly. The logger is a routing/verification point, not a source of currency.

### Logger tiers
- **Andesite Terminal Logger:** basic six-way output selection, one configurable output, no filter slots, no emitted light.
- **Brass Terminal Logger:** same six-way output selection plus filter slots/predicates and more precise routing, no emitted light.
- Do not use a shaft as the defining body/center ingredient for the andesite logger; use an andesite component instead. The brass version should be a deliberate upgrade, not a recipe that requires an expensive late-game chain.

### Scrapper
- The Scrapper is the block that emits light. Keep light emission on the Scrapper only, and do not accidentally copy that light property to either Terminal Logger tier.
- The Scrapper's recipe should also be accessible early-game, using basic Create/andesite materials; its processing behavior must be separate from delivery verification so scrap processing cannot masquerade as a delivery receipt.

### Recipes and progression
- All new block recipes must be cheap and obtainable early game, either as standard crafting recipes or basic Create processing recipes.
- The portable device is the only planned exception that may be more expensive or require a later progression step.
- Do not require Aeronautics, Sable, or another optional addon merely to craft/use the andesite or brass logger, or Scrapper.
- The brass logger can require brass because brass is a normal Create progression milestone, but avoid rare/late-game materials beyond that.
- Include recipe JSONs and test each recipe in a clean survival world before marking the feature complete.

## Delivery route hardware constraints
- **Train:** use Create Portable Storage Interfaces as the transfer boundary. A valid delivery is an actual server-observed PSI transfer into a logger/verified receiving inventory, not simply a train arriving near a station.
- **Airship:** use Aeronautics Docking Connectors as the transfer boundary. A valid delivery is an actual server-observed dock/transfer event and confirmed cargo at the receiving endpoint; connector detection alone is insufficient.
- **Belt:** belt compatibility is a separate Create route capability. A belt/logger interaction must verify actual server-side item transfer and must not treat a belt's presence as a shipment.
- **Frogport:** use Create's built-in package Frogport workflow. Verify actual package contents and routing metadata before fulfilling the order.
- All four user-facing delivery paths remain required: manual, train, airship, Frogport. Belt/logger infrastructure supports these routes and is not a replacement for any of them.

## Compatibility and recipe updates

- Frogport is Create's built-in package Frogport; it does not require Aeronautics. `CreateFrogportCompat` checks Create and the registered `create:package_frogport` item.
- `CreateBeltCompat` separately detects Create belt infrastructure and both funnel tiers. This is capability detection only; the actual transfer still must be observed server-side.
- The andesite Terminal Logger recipe uses an andesite casing and andesite funnels rather than a shaft as its central component.
- The brass Terminal Logger is an upgrade recipe using the andesite logger, brass sheet, Create filter, and brass funnel instead of requiring four filters and four iron ingots.
- The logger persists its selected output face and transfer counters in block-entity NBT. It currently exposes the face/filter/diagnostic backend, but does not yet provide a live item-handler or real Create transfer hook; do not describe belt/train/airship/Frogport delivery as end-to-end working until those adapters are implemented and tested.

## Release requirements: teams and early-game logistics

Team support is a release requirement, not a post-launch update. Before the first public server launch, the release must include server-authoritative team identity and permissions, shared team order visibility/claiming, and payout rules that do not allow non-members to act on a team's orders. Prefer vanilla scoreboard teams as the initial source of team membership so this does not require another client mod; an optional FTB Teams bridge can be added only if its API/version is verified. Team identity must be stored with each order at creation time so renaming a scoreboard team cannot silently reassign old orders. If no team exists, personal orders continue to work.

## Terminal Logger hardware and recipes

- Andesite Terminal Logger: early-game, craftable with Create components and vanilla redstone/iron nuggets. It replaces the old shaft ingredient with an andesite-tier Create component; no brass or precision mechanism is required.
- Brass Terminal Logger: shapeless upgrade from the Andesite Logger using brass, a filter, and a brass funnel. Brass supports an item filter; Andesite accepts any registered item.
- The Logger exposes six faces, with one configurable output and the remaining five input faces. The selected output must be persisted and synchronized. A real transfer must be logged only after the server observes it; diagnostics alone are not delivery proof.
- Recipes and item IDs must be checked against the exact pinned Create 6.x build before release. Do not use a shaft as a recipe ingredient for the base Logger.
- Delivery route support is mandatory for manual, train (Portable Storage Interface), airship (Aeronautics Docking Connector), Create package Frogport, and belt transfers. A route that lacks a verified server-side adapter must visibly report unavailable rather than pretending the delivery succeeded.

## Currency/reward configuration and external economy compatibility

- Each order stores an explicit integer `rewardPerItem`; order creation validates the configured amount and escrows `requestedCount × rewardPerItem` up front. A zero reward is allowed for non-paid/community orders.
- The in-game order-creation UI should expose the reward-per-item field and show the calculated total escrow before confirmation. Never accept a client-calculated total as authoritative; recalculate and validate on the server.
- Currency operations now go through the public `EconomyService` / `EconomyProvider` boundary. The built-in persistent integer ledger remains the default implementation; a verified third-party adapter can register a provider during initialization. Provider choice is first-registration-wins to avoid silently switching currencies while orders hold escrow.
- Other economy mods can be supported through a small integration/bridge implementing `EconomyProvider`, mapping player UUIDs and integer currency units to that mod's server-side account API. No specific external economy mod is wired in yet; do not hard-depend on one until its API, licensing, and target Minecraft version are verified.
- An external provider must support debit/refund and an idempotent `creditOnce(transactionId, playerId, amount)` operation, or provide an equivalent durable transaction key. Without idempotent payout support, automated routes must stay disabled rather than risk double-paying after retries.
- Never silently switch currency providers for a world with active escrow. Provider choice must be stable per world, and the UI should display the currency/provider name so players understand what their reward represents.
- Compatibility detection alone is not integration: no third-party economy provider is considered supported until an adapter is implemented and tested against that mod's real API.
