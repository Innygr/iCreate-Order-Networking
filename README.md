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

Train, airship, Frogport, and belt delivery adapters remain unavailable until each route can verify real server-side cargo transfer and safely commit delivery receipts.


## Logistics design

The required delivery routes are **manual handoff, Create trains, Aeronautics airships, Create Frogports, and Create belts**. Train adapters are designed around Portable Storage Interfaces; airship adapters around Docking Connectors. Frogports are base Create package logistics, not an Aeronautics feature.

The six-way **Terminal Logger** is the local belt/funnel handoff point, with one configurable output face and five remaining faces for routing. Empty-hand right-click cycles the output face; sneak + empty-hand click on a non-output face toggles that input face, and the output face is always output-only. The Brass variant keeps its filter-clear shortcut by clearing the filter when sneak-clicking its output face. The standalone `TerminalLoggerConfig` routing-policy helper restores the former output as an input while preserving other explicitly disabled inputs; regression tests cover the helper's routing invariant and config serialization. This is a compact interaction control, not yet a full configuration GUI. The **Andesite Terminal Logger** is the cheap early-game version and uses an Andesite Funnel (not an Andesite Alloy substitute). The **Brass Terminal Logger** uses Brass Funnels and Create Filters for a more expensive filtered version.

Automated route adapters are not yet implemented or build-verified. Until an adapter can verify and reserve real server-side cargo, the backend rejects that route instead of consuming items on an unverified event.

## Backend completion: 85%

This estimate covers backend functionality, not the overall mod or GUI. Implemented foundations include world-persistent orders and delivery receipts, server-side order/economy validation, claim/fulfil/cancel flows, retry/idempotency guards that require the exact reserved order/route/claimant/count before applying automated progress, recovery handling that finalizes already-applied progress without re-committing cargo, including load-time receipt finalization from durable progress markers, JUnit regression tests for receipt matching/finalization and bounded route-specific receipt validation, delivery-request validation, adapter-registry safeguards including route-resolution failures and fail-closed public registration, economy-provider metadata failures, and persistent economy-ledger idempotency/round-trip/overflow checks, Terminal Logger side-routing/serialization invariants, and order SavedData round-trip/corrupt-record rejection, malformed persisted delivery-receipt isolation, load-time receipt recovery and temporary progress-marker cleanup, manual-only legacy receipt enforcement, and pending-delivery reservation accounting, and server-stop cleanup for transient logistics state; isolated factory endpoint failures during stock discovery; fail-closed exception handling for optional economy providers; the shared delivery-service boundary; and the Create Redstone Requester restock-order bridge. The remaining work is substantial: expand manual-handoff transaction/recovery tests and implement/verify automated cargo adapters for trains, airships, Frogports, and belts; add broader automated lifecycle/recovery tests; and verify requester/redstone behavior in a live world. A passing CI build proves compilation only, not in-game behavior.
