# iCreate: Order Networking

🚧 **Work in progress — not yet a playable release.**

A player-driven economy mod for Create-based Minecraft servers. The goal is to connect factories, trade resources, and cooperate through a shared order network that lets players request materials and fulfil industrial needs.

## Target platform
- Minecraft **1.21.1**
- NeoForge **21.1.251**
- Java **21**
- License: **MIT** (see [LICENSE](LICENSE))

## Planned development
- Persistent server-wide player orders
- Create, browse, claim, partially fulfil, and cancel orders
- Server-authoritative validation for item transfers and rewards
- An in-game order terminal with a functional GUI
- Later: optional integrations for teams and factory logistics

These are goals, not claims that the features are already implemented. This repository is currently a development scaffold.

## Current backend status

The repository includes a GitHub Actions Java/Gradle build workflow and JUnit 5 regression tests for delivery-receipt transaction matching/finalization and bounded route-specific receipt validation, rejection of incomplete zero-count receipts, request validation, adapter registration/route-resolution failures, and rejection of economy providers whose metadata initialization throws. Optional economy providers are isolated: runtime/API-linkage failures fail closed instead of crashing gameplay or silently switching to the internal currency ledger. GitHub Actions runs on pushes; check the linked Actions history for the latest result. Rapid successive commits can cancel earlier runs, so a successful completed run is required before treating the suite as verified. Factory stock discovery now isolates runtime and missing-linkage failures from individual endpoints so one broken optional factory cannot abort sourcing from healthy factories. Manual handoff payouts now use the delivery transaction ID for idempotent economy credits. Cancellation refunds use a separate purpose-scoped idempotency key, preventing duplicate escrow refunds if the economy save lands before the cancelled order state. Pending automated deliveries reserve order quantity until progress is applied, and the persistent reservation boundary independently verifies the claimant, order state, and remaining unreserved count before accepting a reservation. Server-stop lifecycle handling clears transient endpoint/adapter registries and Redstone Requester edge latches, preventing stale in-memory registrations from leaking across server restarts. The backend includes persistent orders, server-side order validation, the shared delivery-service boundary, and a bridge from Create's built-in Redstone Requester to adjacent configured Order Terminals.

When activated, a valid Redstone Requester can publish zero-reward restock orders to the order board. Duplicate active orders for the same owner and item are suppressed. This bridge still requires live-world verification against the pinned Create version; CI success confirms compilation, not that redstone event timing and block interactions work in-game.

A Create 6.0.x / Minecraft 1.21.1 train adapter is now implemented around Portable Storage Interfaces and Terminal Logger endpoints. It tags one or more cargo stacks with a shared transaction UUID, reserves quantities across the Terminal Logger buffer, dispatches through the active source PSI, verifies the full marked stack at the claimant-owned destination logger, and finalizes order progress/reward only after the receipt is verified. Reserved cargo can be resumed after restart, and a destination receipt can reconcile the narrow crash window before the dispatch milestone is saved. The adapter is code/CI-tested but still requires live-world testing with moving trains and real PSI handoffs. The Aeronautics Docking Connector adapter is implemented using the optional stack's registry detection and server-side item-handler capability, with transaction-marked multi-stack cargo and verified destination receipt. It still requires a real Aeronautics installation and live-world verification. Frogport and belt automated adapters remain unimplemented; unsupported routes fail closed.


## Create Display and Stock integrations

The planned Create-facing integrations are split by Create's **Display Link** and **Stock Link** systems. The Order Terminal represents the factory-wide view; each Terminal Logger represents one physical routing point. A factory can have multiple loggers, so logger displays must be local to the selected block rather than aggregated as if they were the whole factory.

| Create integration | iCreate block | Intended role | Status |
|---|---|---|---|
| Display Link | Network Statistics | Show network statistics, such as item input/output and rejected transfers | A Display Link source class exists; registration and live-world behavior still need verification |
| Display Link | Order Terminal | Show factory order information, such as active order counts and outstanding item quantity | Source implemented, registered, and covered by summary tests; live-world verification remains |
| Display Link | Terminal Logger | Show this logger's own routing configuration, filter, buffer/transfer state, and diagnostic status where available | Source implemented and registered; live-world verification remains |
| Stock Link | Order Terminal | Expose factory stock/request information to Create's stock network, with clear rules for what counts as available stock | Planned; semantics and implementation still need verification |

The Terminal Logger now has a local-state Display Link source showing owner short ID, output face, buffered item/slot counts, accepted/rejected transfer counters, filter, and last status. It is registered against the Terminal Logger block entity. This is a per-logger diagnostic display, not a factory-wide order display. The Terminal Logger is **not currently planned as a Stock Link source**: its local buffer and routing role do not necessarily represent the factory's overall stock. Multiple loggers may observe different parts of the same flow, so summing their inventories could double-count items. Any future stock integration would need explicit accounting rules to avoid that.

These integrations are distinct from crafting recipes and block models. Do not assume that a block supports Display or Stock just because its recipe exists. Each integration must use the pinned Create 6.x API, register safely, handle missing/unconfigured data, and be tested in a live Minecraft world. Create is an optional dependency, so Create-specific classes must remain isolated behind the mod's compatibility boundary.

## Logistics design

The required delivery routes are **manual handoff, Create trains, Aeronautics airships, Create Frogports, and Create belts**. Train adapters are designed around Portable Storage Interfaces; airship adapters around Docking Connectors. Frogports are base Create package logistics, not an Aeronautics feature.

The six-way **Terminal Logger** is the local belt/funnel handoff point, with one configurable output face and five remaining faces for routing. Empty-hand right-click cycles the output face; sneak + empty-hand click on a non-output face toggles that input face, and the output face is always output-only. The Brass variant keeps its filter-clear shortcut by clearing the filter when sneak-clicking its output face. The standalone `TerminalLoggerConfig` routing-policy helper restores the former output as an input while preserving other explicitly disabled inputs; regression tests cover the helper's routing invariant and config serialization. This is a compact interaction control, not yet a full configuration GUI. The **Andesite Terminal Logger** is the cheap early-game version and uses an Andesite Funnel (not an Andesite Alloy substitute). The **Brass Terminal Logger** uses Brass Funnels and Create Filters for a more expensive filtered version.

Automated route support is adapter-specific. The train route has a Portable Storage Interface implementation; other automated routes remain unavailable until they can verify and reserve real server-side cargo. Unsupported routes fail closed rather than consuming items on an unverified event.

## Backend completion

The backend now includes persistent orders and delivery receipts, server-authoritative validation, claim/fulfil/cancel flows, idempotent payout/refund accounting, pending-delivery quantity reservations, manual inventory fulfilment, a Create Redstone Requester restock-order bridge, and a durable shipment journal with `RESERVED → DISPATCHED → DESTINATION_RECEIVED → COMPLETED` milestones. The Create PSI train adapter supports multi-stack shipments within the Terminal Logger buffer's available slots, verifies the full marked quantity at destination before applying progress/reward, and recovers reserved shipments and payout retries after restart. Regression tests cover transaction validation, journal transitions, malformed saved rows, and receipt persistence.

**Still outstanding before calling the backend complete:** live-world verification of the train/PSI flow and Redstone Requester event behavior; broader end-to-end tests for interrupted dispatch, destination receipt, and payout recovery; and additional transport adapters for Frogports and belts. The Aeronautics Docking Connector adapter is implemented but needs live-world verification. Unsupported routes fail closed. A passing GitHub Actions build verifies compilation/tests, not in-game behavior. The order-terminal GUI and Stock Link integration remain separate implementation work. The Order Terminal Display Link source and summary regression tests are implemented; it still needs live-world verification. The Terminal Logger Display Link source is implemented but needs live-world verification.

## Total project completion: 45%

This is a rough estimate of the **entire mod**, not just the backend. The backend has substantial implemented foundations and a code/CI-tested train adapter, but the player-facing order-terminal GUI, Frogport/belt adapters, full Create logistics verification, and broader end-to-end/in-game testing remain incomplete. The percentage is a planning estimate and should be revised as these major milestones land; a successful CI build alone does not mean the mod is playable or release-ready.


The Order Terminal Display Link reports active orders and outstanding item quantity for the factory owner's orders. It is registered alongside the Terminal Logger and Network Statistics Display Links. Create Frogport compatibility detection now requires Create's item, block, and block-entity type registry entries to be present; this is deliberately only a prerequisite check, not a working Frogport shipment adapter.


The Order Terminal Display Link has regression tests for active-order counting, outstanding quantities, owner scoping, and unclaimed terminals.
