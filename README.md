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


## Logistics design

The required delivery routes are **manual handoff, Create trains, Aeronautics airships, Create Frogports, and Create belts**. Train adapters are designed around Portable Storage Interfaces; airship adapters around Docking Connectors. Frogports are base Create package logistics, not an Aeronautics feature.

The six-way **Terminal Logger** is the local belt/funnel handoff point, with one configurable output face and five remaining faces for routing. The **Andesite Terminal Logger** is the cheap early-game version and uses an Andesite Funnel (not an Andesite Alloy substitute). The **Brass Terminal Logger** uses Brass Funnels and Create Filters for a more expensive filtered version.

Automated route adapters are not yet implemented or build-verified. Until an adapter can verify and reserve real server-side cargo, the backend rejects that route instead of consuming items on an unverified event.
