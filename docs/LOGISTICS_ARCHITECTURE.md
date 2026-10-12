# Logistics Architecture

This document records the initial design for the factory logistics layer in iCreate: Order Networking. It is a design target, not a claim that every feature below is already implemented.

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
