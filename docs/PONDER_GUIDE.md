# Ponder Guide

This document tracks the in-game Ponder tutorials registered for iCreate: Order Networking.

## Registered scenes

| Entry block | Scene | What it teaches |
| --- | --- | --- |
| Terminal Logger | Tracking an item delivery | The logger's role in observing delivery activity |
| Brass Terminal Logger | Choosing the brass logger | Distinguishes the brass logger variant |
| Order Terminal | Creating and fulfilling orders | Order workflow and checking fulfillment |
| Network Statistics | Reading network statistics | Inspecting activity rather than guessing at failures |
| Scrapper | Using the Scrapper | Where to check accepted inputs and output behavior |
| Missile Thruster | Fuel and thruster basics | Water/lava efficiency and current engine limitations |
| Missile Tag | Assigning missile identity | Stored identity data and current attribution limitations |

The scenes are registered client-side through Create Ponder's plugin API. Their demonstration layouts use vanilla blocks instead of schematic files, so they do not need generated schematic assets.

## Scope and limitations

These Ponders are an initial in-game guide for the blocks currently registered in the repository. They do not claim the entire planned order-networking or missile system is implemented. The missile launch/impact system, orbital launch integration, transport adapters, and a rendered banner/sign on the Missile Tag still require implementation before those workflows can be demonstrated as working machinery.

As additional blocks and mechanics are implemented, add scenes for each real workflow and replace illustrative layouts with accurate schematics where appropriate. Keep tutorial wording synchronized with runtime behavior.
