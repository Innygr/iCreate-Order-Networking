# Ponder Guide

This document tracks the in-game Ponder tutorials registered for iCreate: Order Networking.

## Registered scenes

| Entry block | Scene | What it teaches |
| --- | --- | --- |
| Terminal Logger | Tracking a delivery | Inspect delivery stages and verify destination receipt |
| Brass Terminal Logger | The brass logger | Understand the variant and configure it through the actual block UI |
| Order Terminal | From request to fulfilment | Create/inspect, claim, fulfil, and verify an order |
| Order Terminal | Transport integration overview | Planned train and Aeronautics integration points |
| Network Statistics | Diagnosing network activity | Use reported status to narrow down stalled requests |
| Scrapper | Scrapper input and output | Check accepted inputs, recipe data, and outputs |
| Missile Thruster | Fuel efficiency overview | Current water/lava range-helper relationship and limitations |
| Missile Thruster | Orbital assembly overview | Planned Y=250 minimum, inverted assembly, and current integration status |
| Missile Tag | Missile identity data | Identifier, team name, banner data, and remaining rendering/integration work |

## Scene implementation

All nine scenes are registered client-side in `ModPonderPlugin` and implemented in `ModPonderScenes`. Each scene reveals its demonstration layout in stages and points its instructional text at the relevant block. This gives each tutorial a clear sequence instead of revealing the entire layout at once.

The layouts use vanilla blocks as visual props, not as working iCreate machines. No schematic assets are required for these scenes.

## Accuracy and limitations

The scenes intentionally distinguish implemented helpers from planned gameplay. In particular, missile launch/impact, orbital launch integration, transport adapters, and an in-world banner renderer are not presented as complete working machinery. The Scrapper example blocks are not a recipe demonstration. Keep tutorial wording synchronized with runtime behavior as those features are implemented.

## Verification checklist

- [ ] Build the mod against the pinned Minecraft, NeoForge, Create, and Ponder versions.
- [ ] Open every scene in a client and verify camera framing, text placement, and reveal timing.
- [ ] Confirm every entry block exposes its expected scene(s) in the Ponder index.
- [ ] Replace visual props with accurate machinery / animated item movement when the relevant runtime integration is available.


## Animated movement pass

The logistics-oriented Ponder scenes now include a moving redstone marker that travels across the demonstration area, giving each sequence a visible motion beat. These are teaching animations only and do not imply that the visual marker is a real shipment entity. The orbital scene separately animates assembly components into position.
