# Logistics Architecture

This document records the initial design for the factory logistics layer in iCreate: Order Networking. It is a design target, not a claim that every feature below is already implemented.

## Edition and distribution model: Standard vs Mythic Server

Mythic Edition is the **server edition** of iCreate: Order Networking, not a client-side feature pack. The intended distribution and trust boundary is:

- **Standard edition (players):** player-facing menus, rendering, and the client networking needed to communicate with a compatible server. It must not contain or execute Mythic backend services, Discord bot credentials, or server-only integrations.
- **Mythic Server edition (dedicated server):** authoritative order/economy/delivery processing, server administration and diagnostics, Mythic-only systems, and server-side Discord/FTB Teams integrations.
- **Dedicated-server-only activation:** Mythic services must not start in single-player or an integrated/LAN host. Gate activation on the dedicated-server environment, not a client-visible toggle.
- **Server authority:** validate permissions, orders, inventory changes, delivery receipts, and Mythic actions on the server. Client packets are requests, never proof of authorization or completion.
- **Secrets:** Discord tokens and other credentials must remain in server-side configuration/environment variables and must never be synced to clients or committed to source control.
- **Protocol compatibility:** if Mythic features affect client-visible gameplay or screens, expose only the minimum required network messages and client presentation code. The client does not need the Mythic backend to display authorized server-provided state.
- **Optional dependencies:** keep Create-family and Discord/FTB integrations isolated behind safe server-side compatibility boundaries so missing optional mods/services fail closed without crashing the standard client.

This is the target architecture, not a claim that a separate Mythic Server artifact or all activation guards already exist. Before release, implement and test the edition boundary: dedicated server activates Mythic services; integrated server and client do not; credentials never enter client packets/logs; and incompatible clients cannot invoke privileged server actions.

## Headless dedicated-server distribution

The Mythic Server distribution should be headless: it must not require client rendering classes, screens, HUDs, or client-only visual assets to run on a dedicated server.

- Keep authoritative gameplay and logistics logic, block/block-entity registration, server-side menu/container logic, packet validation, saved data, permissions, order/economy services, and server integrations on the server side.
- Keep screens, renderers, HUD overlays, key mappings, client-only models, and other presentation code in the Standard client distribution or a clearly isolated client source set.
- Share only the minimum protocol/data definitions needed by both sides. The server returns authorized state; the client is responsible for presenting it.
- Do not make server startup depend on loading net.minecraft.client classes. Verify classloading and mod startup in an actual dedicated-server environment.
- A headless server may still need block/item models and textures in the client resource pack, but those resources must not require server-side rendering code to load.
- If the same mod artifact is temporarily used on both sides, isolate client code using the loader's supported distribution checks and client-only registration mechanisms. The longer-term target is a dedicated server artifact plus the compatible Standard client artifact.

This distribution boundary does not remove server-side menu/container or networking responsibilities. A UI-less server still needs secure handlers for every action requested by a player client, and must reject malformed, unauthorized, stale, or duplicate requests.

Release gate: launch a dedicated server with no client classes available, connect a Standard client, and test order/menu packets, permission failures, disconnect/reconnect, and restart persistence. Confirm that no Discord credentials or Mythic-only backend internals are sent to clients.

## Terminal first-use binding

- **Implemented foundation:** the first server-side right-click on an unconfigured Terminal Logger binds the player's UUID and their current FTB party-team UUID (when available) into the block entity's persistent data. The first click claims the logger rather than also changing its settings.
- The owner always retains access. Other party members are allowed only while both they and the owner currently resolve to the bound FTB party. If the owner leaves or changes parties, teammate access pauses; it can resume if the owner rejoins the original party. Without a usable FTB Teams API or party membership, the logger remains owner-only.
- The terminal must not silently rebind when another player right-clicks it. Current logger setting interactions are server-authorized against this binding; broader network actions still need their own server-side permission checks.
- Access checks should account for the bound owner and team. Team membership grants access only according to the terminal/network permission policy; being near the terminal must not expose private stock or orders.
- Persist binding across chunk unloads and server restarts. Do not store ownership only in a static/in-memory map.
- If the owner changes or leaves a team, re-evaluate team access against current membership rather than trusting a stale team roster. Define a deliberate recovery/admin path for owner departure; do not automatically make a private terminal public.
- Destroying/removing the terminal clears the binding with the block entity. A replacement block starts unconfigured and must be claimed again. Ensure block removal also cleans up any terminal-owned jobs/tickets according to the job recovery policy.
- Handle first-use races on the logical server so two players cannot claim the same terminal simultaneously.

## Core request flow

1. A player submits an item request at a terminal.
2. The terminal verifies the player's access to the terminal and its network.
3. The terminal queries registered factory endpoints before requesting outside supply.
4. Available stock is reserved before a shipment is dispatched.
5. If local/connected factories cannot cover the full amount, the network seeks only the remaining shortfall.
6. A request is complete only after the destination confirms receipt.
7. The Terminal Logger records the job state and manages bounded chunk-loading tickets for active logistics work.

The registry must be explicit. Do not repeatedly scan every loaded or generated chunk to discover factories. Factory endpoints register/unregister as they load, unload, are placed, or are removed; live inventory is revalidated before reserving or extracting items.

## Stock safety and delivery jobs

- Each request and shipment has a persistent unique ID.
- Reservations prevent multiple terminals from promising the same items.
- A reservation expires or is released when dispatch cannot proceed.
- Delivery states should include queued, sourcing, reserved, transporting, delivered, and failed/cancelled.
- A job is not marked delivered until the destination verifies the received item count.
- Retries must be idempotent so a server restart or transport timeout cannot duplicate cargo or rewards.
- Factory stock is distinct from items that a factory could craft later. The first stock lookup uses currently available inventory; production-on-demand can be added as a separate, explicit job type.

## Terminal Logger

The Terminal Logger has two responsibilities:

- Track logistics jobs, their source/destination, status, and delivery results.
- Request chunk tickets for the minimum required endpoints while a valid job is active.

Chunk loading must be bounded by server configuration (maximum tickets/chunks and a job timeout), released on completion/cancellation/failure, and cleaned up safely on block removal or server shutdown. It must not keep every factory loaded indefinitely.

### Direct logistics connections (no funnels required)

The Terminal Logger is itself a logistics connector/controller. Players must not need to attach Create Funnels to its input or output faces just to exchange cargo. It should support direct connections to compatible Create logistics blocks and transport components:

- **Docking Connectors** — hand off/receive cargo through a connected airship docking interface when the connector reports a valid docked/ready state.
- **Packagers** — accept packaged orders/cargo and provide outbound packages to the connected packaging endpoint, respecting package contents and job identity.
- **Belts** — insert/extract cargo through a supported belt interaction point, with direction and item-transfer rules explicit. Avoid treating arbitrary adjacent belt blocks as a valid connection unless the connector geometry/state is verified.
- **Portable Storage Interfaces (PSIs)** — integrate with a train's portable storage interface only while the interface is in a valid connected/transfer-ready state. Use the PSI transfer handshake; do not bypass Create's connection behavior.

Connection discovery must use explicit, validated adapters/capability checks for each supported endpoint type, not broad assumptions based on nearby blocks. Show the detected connection type and ready/disconnected/error status in the logger's UI or diagnostics.

### Current implementation boundary

The Terminal Logger exposes a standard NeoForge `ItemHandler.BLOCK` capability with sided access: five faces accept filtered items and the selected output face permits extraction. Its server tick can pull from adjacent item-handler providers and push buffered items through the configured output face. Create's Portable Storage Interface already exposes its own item handler only while its native transfer state is active, so the logger can exchange cargo through that capability without duplicating the PSI handshake. However, this is not yet an order-aware train delivery adapter: the transfer is not bound to a persistent shipment reservation, order ID, or verified destination receipt. Aeronautics Docking Connector cargo access/lifecycle and verified belt-to-order receipts also remain unfinished. A Packager-mediated Frogport adapter is now implemented for the target Create 6.0.x / Minecraft 1.21.1 API. The physical route is Terminal Logger ↔ correctly oriented Packager, with a Frogport above the Packager; the Frogport does not directly connect to the Logger. Reserved cargo remains transaction-marked inside Create packages, and the adapter only accepts a receipt after the destination Logger contains the exact marked quantity. The code/build still does not prove that the configured Frogport address and physical route work in-game; that must be verified in the target modpack before release. Endpoint discovery is diagnostic only; no route should fulfil an order or pay a reward until receipt verification is connected to the shared fulfilment service.

The logger should define clear input/output behavior per connection. A connection can be input-only, output-only, or bidirectional only when the underlying component safely supports it. Do not allow the same item stack to be dispatched twice if multiple connections are attached. Cargo must be reserved/claimed by a single active job before transfer, and completion must be verified at the destination.

No-funnel operation is the intended user-facing behavior; it does not mean every component exposes the same API. Use Create's supported interfaces where available and isolate optional integrations behind safe adapter classes. Detect absent optional mods/classes without crashing.

The existing logger block's six-face routing/filter settings, if retained, should be independent of job management and direct-connection detection. They may configure allowed directions or item filters, but funnels must not be a prerequisite for the direct adapters above.

## Satellites and range

Initial defaults:

- Standard terminal coverage: 5,000 × 5,000 blocks, configurable.
- Satellite coverage: 100,000 × 100,000 blocks, configurable as a single square width.
- Satellite coverage is centered on its middle/core block.
- The satellite core must be placed at Y >= 150; the same height rule should apply to all three multiblock pieces.
- The satellite is a 3-wide × 1-high × 1-deep structure: solar panel, transmitter core, solar panel.
- Breaking any piece dismantles the structure and disables the satellite.
- A terminal can register/connect to a satellite at any distance within the same dimension. This is a network connection, not item teleportation: cargo still requires a real transport route.
- The first implementation should be dimension-local. Cross-dimensional delivery requires a separate explicit transport mechanic and is not implied by satellite range.
- One satellite per dimension is the expected initial setup where remote logistics are needed; a server may have more if the design later permits it.

A satellite item can be granted with commands, but command-granted items do not bypass the placement-height or multiblock validation.

## Satellite assembly and materials

The satellite should be moderately expensive but available early enough to be useful. Avoid endgame-only ingredients.

- Each solar panel: Daylight Sensor, Glass Pane, Copper Sheets, and Andesite Alloy (exact counts to be finalized against the Create recipe balance).
- Transmitter core: Brass Casing, Transmitter, and Electron Tubes. The Transmitter replaces the previously considered Precision Mechanism.
- Final assembly: Create sequenced assembly starting with the Transmitter Core, applying Solar Panel #1 at a Deployer, then Solar Panel #2 at a second Deployer, producing the Satellite. This must be implemented as a real compatible Create recipe, not described as a normal shaped crafting recipe.

## Transport adapters

- Trains: integrate through Create Portable Storage Interfaces.
- Airships: integrate through Docking Connectors.
- The Terminal Logger's direct-connection adapters should hand off cargo through these interfaces without requiring Funnels attached to the logger.
- Adapters must report readiness, reserve/commit cargo safely, verify delivery, and return a clear failure result when disconnected or unable to complete a route. The shared `AutomatedDeliveryRegistry` now holds only explicitly registered, available adapters, and `AutomatedDeliveryService.prepare` performs request validation, route lookup, authorization, and cargo reservation. Its `PREPARED` result is explicitly not delivery success: transport commit and destination receipt verification must still happen before order fulfillment or payout. No concrete Create/Aeronautics adapter is registered until its API and transfer semantics are verified.
- Keep the core order/network model independent of optional Create-family classes; use safe optional-mod detection.

## Ownership and permissions

- Network membership and private factory access should follow FTB Teams where the integration is available. Detect it by its declared mod ID (`ftbteams`), not by the installed jar filename. `FTBTeamsCompat` resolves a player's live party-team UUID through the public FTB Teams API using reflection, without hard-linking optional classes. Missing API, startup races, personal teams, and incompatible versions fail closed. Terminal Logger first-use ownership now consumes this helper; the wider network, inventory, and shipment authorization paths still need their own policy checks.
- Public/shared endpoints must be an explicit choice. Divine Land may host a deliberately public satellite, but that should not make every nearby private factory public.
- Validate permissions server-side for every inventory reservation, transfer, configuration change, and shared-network connection.

## Implementation milestones

1. Define network identity, factory endpoint registration, and safe stock lookup/reservation APIs.
2. Add terminal first-use binding and server-side owner/team access checks.
3. Add terminal request sourcing that checks connected factory stock first and requests only the shortfall.
4. Connect the Terminal Logger to durable job tracking, direct connection adapters, and bounded chunk loading. The current endpoint detector and shared adapter-preparation pipeline are foundations only; concrete transfer and receipt confirmation remain required.
5. Implement and test local delivery and receipt verification.
6. Add satellite multiblock, coverage/config validation, and sequenced-assembly recipe.
7. Add Portable Storage Interface train and Docking Connector airship adapters.
8. Resolve FTB Teams identities and enforce team permissions through the supported API, then add explicitly shared/public networks. The current `FTBTeamsCompat` is only a safe presence/API gate.
9. Consider cross-dimensional routing only after same-dimension delivery is reliable.

Each milestone should be compiled and tested before the next one is layered on.

## Network Stats

Network Stats is the read-only observability layer for a logistics network. It should be available from a Terminal Logger interaction/menu and summarize the network the player is authorized to view. The initial implementation should reuse the endpoint registry and delivery/job services rather than perform world-wide block scans.

### Overview panel

Show:
- registered Terminal Loggers and discovered endpoints, grouped by endpoint type (local inventory, Create PSI/train, Aeronautics Docking Connector/airship, belt, and package/Frogport);
- endpoint state: ready, idle, disconnected, unavailable, or error, with the reason where known;
- queued, sourcing, reserved, transporting, receipt-pending, delivered, failed, and cancelled jobs;
- outstanding reservations and shipments awaiting destination confirmation.

A registry-presence check must not be shown as a ready/working adapter. For example, an Aeronautics connector whose transfer API is not verified must display as detected-but-unavailable, not operational.

### Throughput and history

Track actual item movement as separate events from order completion:
- items inserted into or extracted from a logger;
- transfer attempts accepted/rejected and the reason;
- shipment dispatch and destination receipt;
- order fulfilment and reward transaction.

Show rolling throughput (such as items moved over the last 60 seconds and 5 minutes), recent transfer events, and routes with repeated failures or long receipt-pending times. Count item quantities, not just stack operations. Keep event history bounded and configurable so busy networks cannot grow saved data without limit.

### Correctness rules

- **Moved is not delivered.** Inventory movement updates cargo-transfer statistics only. A delivery is counted as successful only after the shared fulfilment service validates the destination receipt.
- **No invented precision.** If an adapter cannot report readiness, distance, or cargo state, show “unknown/unavailable” rather than guessing.
- **Server-authoritative.** Collect and aggregate stats on the logical server. The client may request a view, but cannot submit counts, job states, receipts, or payout data.
- **Permission-aware.** Network stats must apply the same owner/team/network access rules as the endpoints and jobs being summarized; private factory inventory and another team's routes must not leak through the UI.
- **Restart-safe totals.** Persist cumulative counters and bounded recent events in saved data; derive short rolling windows from timestamped events or reset them clearly after restart. Do not persist unbounded per-tick samples.
- **Low overhead.** Update counters on transfer/job events rather than scanning every loaded chunk or polling every endpoint every tick. Refresh the client view on demand or at a modest interval.
- **One source of truth.** Job counts come from the job service, endpoint counts from the registry, and reward counts from the idempotent economy receipts. Do not maintain competing counters that can drift.

### Suggested first release

1. **Implemented foundation:** `NetworkStatsData` persists per-owner cumulative input/output item totals and rejected-transfer counts in overworld SavedData, with a bounded 256-event history.
2. **Implemented instrumentation:** actual accepted input/output item movement is recorded from the Terminal Logger transfer loop; explicitly rejected transfers reported through `recordTransfer` are recorded separately.
3. **Implemented snapshot API:** `TerminalLoggerBlockEntity.getNetworkStats` returns the owner's read-only snapshot only when the requester passes the logger's existing owner/team access check.
4. **Still needed:** wire the snapshot into a stats screen; add rolling-window rates and job/receipt/payout counters from the shared delivery services.
5. **Verification needed:** test restart persistence, denied access, transfer retries, and the distinction between moved cargo and completed orders in a running game.

The stats service currently measures item movement, not verified order completion. Existing endpoint detection remains diagnostic, and no transfer statistic is evidence of a fulfilled order or payout.

## Network Statistics block (implementation foundation)

- The `network_statistics` block and persistent block entity are registered. The block stores its owner, the selected factory UUID, the selected factory's party UUID, and a `FACTORY` / `TEAM_GLOBAL` view mode.
- First interaction claims the display. Only its owner and currently authorized members of the bound FTB party can configure it.
- Right-clicking with Create's wrench toggles between factory mode and Team Global. Team Global is rejected unless the display owner still belongs to the party captured when the display was claimed; no server-wide or cross-team fallback is permitted.
- The block entity exposes a permission-checked server-side factory-binding API. The Order Terminal is now implemented and invokes this API when a claimed terminal is right-clicked beside a claimed display.
- This remains a compact readout rather than a full statistics screen: right-click reports cumulative totals in chat, and the registered Create Display Link source exposes the same scope-labelled totals. The block model still uses a temporary vanilla iron-block texture; richer selectable channels and rolling-window metrics remain follow-up work.
- Cargo movement remains distinct from order delivery. No delivery totals should be exposed until the delivery receipt service provides verified completion events.

### Factory-scoped Network Statistics and Display Link output

- `NetworkStatsData` keeps backward-compatible owner totals and now also stores bounded per-factory and per-party totals. Each transfer event records its optional factory/team UUID as well as the existing dimension, position, item, count, type, and game-time fields.
- Terminal Loggers can persist a stable factory UUID and factory-party UUID. Once bound, actual accepted input/output movement and explicitly rejected transfer events update owner totals plus the matching factory/team aggregates. Unbound loggers continue to contribute to owner totals and may contribute to their live party aggregate.
- The Network Statistics block entity exposes a permission-checked snapshot API for Factory or Team Global mode. The physical Display Link output is intentionally a public in-world readout, but it fails closed when the configured factory or party identity is no longer valid.
- The `FactoryIdentityProvider` contract and `bindFactoryFrom` hook are wired to the registered Order Terminal, which persists a stable factory UUID and performs adjacent logger/display binding with per-target permission checks.
- Create Display Link now has a registered single-line source that reports `IN`, `OUT`, and `REJ` totals from the same authoritative snapshot source as the block interaction.
- These remain observed inventory transfer metrics. They are not confirmed shipments, completed orders, or paid rewards. Those require order-bound shipment receipts and the existing idempotent delivery service.

### Order Terminal factory identity

- The Order Terminal block entity now creates and persists a stable factory UUID on placement, then claims an owner and captures the owner's current FTB party on first interaction.
- Right-clicking an already-claimed Order Terminal attempts to bind every adjacent, already-claimed Network Statistics block and Terminal Logger to that factory. Each target independently checks its owner/team permissions; unclaimed or unauthorized blocks are not overwritten.
- The factory identity implements `FactoryIdentityProvider`, which is the shared trusted contract used by the Network Statistics block. This is the first in-world path connecting the terminal's identity to factory-scoped statistics.
- The block currently has placeholder vanilla art and no order UI/menu yet. This patch implements identity and stats binding, not order creation, order management, shipment tracking, or payout logic.
- A personal factory can only bind its owner's personal Terminal Logger; a party factory can bind loggers already owned by members of that same live FTB party. This prevents a terminal from silently reassigning another party's or player's inventory junction.

## Backend implementation boundary (current)

This section distinguishes implemented scaffolding from functionality that must not be treated as complete.

| Area | Current state | Completion gate |
|---|---|---|
| Persistent orders and order lifecycle | Implemented foundation with regression coverage | Expand lifecycle, permissions, and crash-recovery tests |
| Delivery receipt matching and idempotency | Implemented foundation with regression coverage | Exercise duplicate/reordered calls and save/reload boundaries end-to-end |
| Automated adapter registry and request preparation | Implemented; unavailable/broken adapters fail closed | Add a real route adapter only when it can reserve, commit, and roll back actual server-side cargo |
| Create Redstone Requester bridge | Implemented bridge foundation | Verify request timing, duplicate suppression, and block interactions in a live world |
| Terminal Logger inventory routing | Implemented sided item-handler routing and bounded transfer loop | Verify adjacent capability edge cases and ensure order fulfilment is driven by confirmed destination receipt |
| Train / airship / Frogport / belt delivery | Not complete; registry-only detection is not a delivery adapter | Each route must pass its own integration tests before it can register |
| Player-facing Order Terminal GUI | Not complete | Add server-authoritative menu actions and client screens; validate all inputs on server |
| Display Link: Network Statistics | Source class exists | Confirm source registration and output in Create 6.0.6 in a live world |
| Display Link: Order Terminal and Terminal Logger | Planned | Add separate sources; logger output must be local to that block, not aggregated across every logger in a factory |
| Stock Link: Order Terminal | Planned | Define precisely whether reported stock is available, reserved, in transit, or craftable; never count reserved stock as available |

### Required automated-delivery transaction contract

An adapter is not complete merely because its transport block exists or a source reference parses. Every automated route must obey this order:

1. Validate the order, item registry ID, positive count, claimant, route, and transaction identity on the logical server thread.
2. Re-check factory ownership/permissions and the live source/destination context. Client-provided coordinates or source strings are identifiers to resolve, not authorization.
3. Reserve the exact requested quantity atomically. A reservation must prevent another order or adapter from promising the same stack.
4. Move cargo using the transport's real server-side API. A planned shipment, scheduled train, connected belt, or docked airship is not proof of receipt.
5. Verify the destination inventory contains the delivered item count. Partial delivery must record only the amount actually received.
6. Commit order progress and any escrow payout through the shared order/delivery service, keyed by the persistent transaction ID. Route adapters must not edit order progress or pay currency directly.
7. If transfer or finalization fails, retain enough durable state to retry safely or restore/release the reservation. Replaying the same transaction must neither duplicate cargo nor award payment twice.

If a route cannot prove the actual transfer or safely recover after a restart, it must remain unavailable. This is intentional fail-closed behavior, not a reason to register a placeholder adapter.

#### Create Redstone Requester bridge contract

The current bridge listens for logical-server neighbor notifications and inspects the changed position plus its six adjacent positions. A powered Create Redstone Requester next to a configured Order Terminal may create zero-reward restock orders under that terminal's factory owner. Each valid encoded item/count entry is checked against the item registry and order-count limit; an existing active order for the same owner and item suppresses another one.

- The requester must be powered and directly adjacent to a configured Order Terminal. The bridge must not infer a terminal from distance or create an order without a stable factory owner.
- Triggering is edge-like: a requester position is latched after a valid request is processed and is eligible again after it is observed unpowered. This prevents repeated neighbor notifications from spamming orders.
- If no configured terminal is adjacent, or the request is empty/invalid, the bridge does not latch the requester; a later neighbor update can retry after the setup is corrected.
- Offline owners retain their UUID identity; the bridge uses the cached profile name and scoreboard team when available. If no team can be resolved, the order falls back to the owner's personal visibility rather than trusting client-supplied identity.
- These orders intentionally have no reward escrow. This bridge creates requests only; it does not extract items, dispatch cargo, claim an order, or mark a delivery complete.
- Live-world verification remains required for Create's event timing, block placement/removal behavior, encoded request edits, power cycling, and duplicate suppression. Unit/CI coverage does not replace that test.

## Terminal Logger identity and visibility

A factory may contain many Terminal Loggers. Each logger's routing, filter, buffer, and transfer diagnostics belong to that individual block. Factory-level order and stock totals belong to the Order Terminal/network service. Do not sum logger buffers to calculate factory stock: the same items may be observed at multiple points as they move through a production line. A logger's local Display Link must identify the selected logger's own state and must not reveal another factory's private order data.

### Backend completion checklist

- [ ] Add tests for manual handoff success, partial handoff, invalid item/count, unauthorized claimant, and repeated transaction.
- [ ] Add crash/reload tests at each durable delivery boundary: reserved, cargo moved, receipt recorded, order progress applied, payout finalized.
- [ ] Add a real server-side adapter and integration test for each route independently: train/Portable Storage Interface, Aeronautics/Docking Connector, Create Frogport, and belt/Terminal Logger.
- [ ] Verify Redstone Requester event timing and duplicate-order suppression in-game.
- [ ] Implement and test server-authoritative Order Terminal menu actions before exposing the GUI.
- [ ] Verify Display Link sources and any Stock Link integration against the pinned Create version in a live world.
- [ ] Run the full CI suite after backend changes; CI compilation alone does not prove live-world transport behavior.

