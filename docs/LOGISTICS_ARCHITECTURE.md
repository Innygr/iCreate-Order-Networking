# Logistics Architecture

This document records the initial design for the factory logistics layer in iCreate: Order Networking. It is a design target, not a claim that every feature below is already implemented.

## Core request flow

1. A player submits an item request at a terminal.
2. The terminal queries registered factory endpoints before requesting outside supply.
3. Available stock is reserved before a shipment is dispatched.
4. If local/connected factories cannot cover the full amount, the network seeks only the remaining shortfall.
5. A request is complete only after the destination confirms receipt.
6. The Terminal Logger records the job state and manages bounded chunk-loading tickets for active logistics work.

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

Chunk loading must be bounded by server configuration (maximum tickets/chunks and a job timeout), released on completion/cancellation/failure, and cleaned up safely on block removal or server shutdown. It must not keep every factory loaded indefinitely. The existing logger block's six-face routing/filter settings remain separate from the job manager.

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
- Adapters must report readiness, reserve/commit cargo safely, verify delivery, and return a clear failure result when disconnected or unable to complete a route.
- Keep the core order/network model independent of optional Create-family classes; use safe optional-mod detection.

## Ownership and permissions

- Network membership and private factory access should follow FTB Teams where the integration is available.
- Public/shared endpoints must be an explicit choice. Divine Land may host a deliberately public satellite, but that should not make every nearby private factory public.
- Validate permissions server-side for every inventory reservation, transfer, configuration change, and shared-network connection.

## Implementation milestones

1. Define network identity, factory endpoint registration, and safe stock lookup/reservation APIs.
2. Add terminal request sourcing that checks connected factory stock first and requests only the shortfall.
3. Connect the Terminal Logger to durable job tracking and bounded chunk loading.
4. Implement and test local delivery and receipt verification.
5. Add satellite multiblock, coverage/config validation, and sequenced-assembly recipe.
6. Add Portable Storage Interface train and Docking Connector airship adapters.
7. Add FTB Teams permissions and explicitly shared/public networks.
8. Consider cross-dimensional routing only after same-dimension delivery is reliable.

Each milestone should be compiled and tested before the next one is layered on.
