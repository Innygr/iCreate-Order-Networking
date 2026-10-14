# iCreate: Order Networking

**A Create-inspired logistics network for Minecraft.**

iCreate: Order Networking is a Minecraft mod project focused on making automated logistics behave like a real delivery network. Instead of moving items only through isolated belts, funnels, and storage systems, the goal is to let players define what needs to be delivered, connect destinations to a shared order system, and use Create-based transport to fulfil those requests.

> **Project status:** In active development. The order and automated-delivery backend is being built in stages. Not every planned block, transport adapter, or integration is available yet.

## What is this mod?

The core idea is simple:

1. A player or machine creates an **order** for a specific item and quantity.
2. A logistics network determines how that order can be supplied and delivered.
3. A compatible transport system carries the requested cargo.
4. The destination confirms receipt.
5. The order records the delivered quantity and settles any configured reward.

The long-term goal is to make this feel like an actual logistics network built with Create machinery—not a magical inventory teleport.

## Main systems

### Order Terminals

Order Terminals are intended to be the main interface for creating and managing delivery orders. They provide the point where players specify what is needed and connect requests to the logistics network.

The exact setup and user interface may change as development continues.

### Orders and progress tracking

Orders track requested items, quantities, status, and delivery progress. Automated deliveries use transaction IDs so that retries can be distinguished from new deliveries.

The backend is being designed to avoid counting the same delivery twice. Progress updates and reward payouts need to remain safe across retries and server restarts.

### Automated delivery

Automated delivery is the bridge between an order and a physical transport system. The backend can prepare a delivery through a transport adapter, reserve the corresponding order quantity, and record a delivery receipt.

A delivery should only be completed after the destination has confirmed receipt of the cargo. Preparing a shipment by itself is not proof that it arrived.

### Create transport integrations

The intended direction is to use Create-compatible machinery for real item movement:

- **Trains:** planned integration using Portable Storage Interfaces.
- **Airships:** planned integration using Docking Connectors.
- **Other transport systems:** may be supported through adapters as the network develops.

These integrations must report delivery outcomes reliably. A saved order record alone cannot establish where physical cargo is after a crash or interrupted journey.

### Redstone Requester integration

The planned Redstone Requester bridge will let an existing Create Redstone Requester sit beside a configured Order Terminal and create a restock request from its item/count configuration.

The intended behaviour is to create a suitable order and avoid duplicate active requests for the same owner and item. This bridge is a request-generation feature: it does **not** itself transport cargo or prove that an order has been fulfilled.

The integration still needs in-game verification with the actual Create block and its supported configuration.

### Delivery verification and logging

A future **Terminal Logger** is being considered to help verify and diagnose deliveries. Its role would be to make delivery state easier to inspect and to help distinguish a shipment that was prepared from one that was actually received.

## Delivery lifecycle

The backend is being developed around a transaction-oriented flow:

1. **Validate** the request against the live order.
2. **Prepare** a shipment through a compatible transport adapter.
3. **Reserve** the requested quantity and persist a delivery receipt.
4. **Transport** the cargo through the physical logistics system.
5. **Confirm arrival** at the intended destination.
6. **Apply progress and payout** using transaction-safe operations.
7. **Finalize** the receipt after completion has been recorded.

If a server restarts while a delivery is pending, the system should retain enough information to reconcile it instead of blindly counting the shipment again. Physical recovery still depends on the transport adapter being able to report or reconstruct what happened.

## Design principles

- **Physical logistics first:** cargo should move through compatible transport machinery rather than being silently teleported.
- **Early-game friendly:** planned components and recipes should be affordable and craftable without requiring a late-game infrastructure setup.
- **Reliable retries:** repeating a transaction should not accidentally duplicate order progress or rewards.
- **Recoverable state:** pending deliveries should remain visible to recovery logic after a restart.
- **Modular transport:** different transport types should integrate through a common adapter contract.
- **Clear feedback:** players should be able to understand whether an order is waiting, in transit, delivered, or needs attention.

## Current development status

The project is under active development. The backend currently includes work on order validation, automated-delivery preparation, persistent delivery receipts, idempotent order-progress updates, reward finalization, and access to pending automated deliveries for recovery.

The following are **not yet safe to assume complete**:

- End-to-end physical delivery through working train and airship adapters.
- Crash recovery that can determine the actual physical location of cargo for every transport type.
- Fully verified in-game Redstone Requester integration.
- Complete end-to-end testing of the whole order-to-destination workflow.
- A finished Terminal Logger block.

This page will be updated as these systems are implemented and tested.

## Planned development roadmap

1. Finish and test the persistent order/delivery transaction lifecycle.
2. Define and implement transport-adapter recovery and destination confirmation.
3. Connect the Order Terminal to the Create Redstone Requester.
4. Implement and test Create transport integrations, starting with the adapter contracts.
5. Add delivery diagnostics and logging.
6. Verify the complete workflow in-game, including retries and server restarts.
7. Refine recipes, presentation, and player-facing documentation.

The order may change as implementation and testing reveal what needs to be done first.

## Technical details

- **Minecraft target:** 1.21.1
- **Mod loader:** NeoForge
- **Java:** 21
- **Core dependency:** Create
- **Repository:** [Innygr/iCreate-Order-Networking](https://github.com/Innygr/iCreate-Order-Networking)

For deeper technical documentation, see:

- [Logistics Architecture](LOGISTICS_ARCHITECTURE.md)
- [Create Compatibility Notes](CREATE_COMPATIBILITY.md)
- [Order Terminal Setup GUI](TERMINAL_SETUP_GUI.md)
- [Redstone Requester bridge contract and implementation notes](LOGISTICS_ARCHITECTURE.md)
- [Missile System](MISSILE_SYSTEM.md)
- [Ponder Guide](PONDER_GUIDE.md)

## Related iCreate projects

These companion projects are developed separately from Order Networking:

- [iCreate: Missile Tech](https://github.com/Innygr/iCreate-Missile-Tech) — the companion project for missile technology.
- [iCreate: Robotics](https://github.com/Innygr/iCreate-Robotics) — the companion project for robotics.

## Contributing and feedback

Bug reports, reproducible test cases, and suggestions are useful—especially for delivery recovery, Create compatibility, and in-game usability. When reporting a delivery issue, include the order state, transport type, what the destination observed, and whether the server restarted during the shipment.

---

*This is a living project. Features described as planned are design goals, not promises that they are already available in a released build.*
