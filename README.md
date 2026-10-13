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

The repository includes a GitHub Actions Java/Gradle build workflow and JUnit 5 regression tests for delivery-receipt transaction matching and finalization. The latest commits have no reported status checks available to confirm a fresh pass. The backend includes persistent orders, server-side order validation, the shared delivery-service boundary, and a bridge from Create's built-in Redstone Requester to adjacent configured Order Terminals.

When activated, a valid Redstone Requester can publish zero-reward restock orders to the order board. Duplicate active orders for the same owner and item are suppressed. This bridge still requires live-world verification against the pinned Create version; CI success confirms compilation, not that redstone event timing and block interactions work in-game.

Train, airship, Frogport, and belt delivery adapters remain unavailable until each route can verify real server-side cargo transfer and safely commit delivery receipts.


## Logistics design

The required delivery routes are **manual handoff, Create trains, Aeronautics airships, Create Frogports, and Create belts**. Train adapters are designed around Portable Storage Interfaces; airship adapters around Docking Connectors. Frogports are base Create package logistics, not an Aeronautics feature.

The six-way **Terminal Logger** is the local belt/funnel handoff point, with one configurable output face and five remaining faces for routing. The **Andesite Terminal Logger** is the cheap early-game version and uses an Andesite Funnel (not an Andesite Alloy substitute). The **Brass Terminal Logger** uses Brass Funnels and Create Filters for a more expensive filtered version.

Automated route adapters are not yet implemented or build-verified. Until an adapter can verify and reserve real server-side cargo, the backend rejects that route instead of consuming items on an unverified event.

## Backend completion: 65%

This estimate covers backend functionality, not the overall mod or GUI. Implemented foundations include world-persistent orders and delivery receipts, server-side order/economy validation, claim/fulfil/cancel flows, retry/idempotency guards that require the exact reserved order/route/claimant/count before applying automated progress, recovery handling that finalizes already-applied progress without re-committing cargo, JUnit regression tests for receipt matching/finalization, delivery-request validation, and adapter-registry safeguards, including rejection of manual-route adapters and safe handling of broken optional integrations; the shared delivery-service boundary; and the Create Redstone Requester restock-order bridge. The remaining work is substantial: implement and verify real cargo adapters for manual handoff, trains, airships, Frogports, and belts; add broader automated lifecycle/recovery tests; and verify requester/redstone behavior in a live world. A passing CI build proves compilation only, not in-game behavior.
