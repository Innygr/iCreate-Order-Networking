# Mechanical Arm Backpack Upgrade

Target: Minecraft 1.21.1, NeoForge 21.1.x, Create 6.x, Sophisticated Backpacks + Sophisticated Core.

## Player-facing design

The Mechanical Arm Upgrade adds a third held-item slot, separate from the vanilla main hand and offhand. The player must supply the arm's item.

Example:
- Offhand: Torch
- Main hand: Pickaxe
- Backpack arm: Sword

The arm is not an item magnet and does not automatically collect drops. Its supplied item is used only when the player explicitly activates the arm for a supported action.

## Activation model

Keep the three action categories separate so the arm does not unexpectedly repeat every vanilla action:

- **Combat:** explicit arm-attack input. Attack uses the supplied weapon, validates target/range/server state, and respects attack cooldown and item durability. Do not automatically duplicate the vanilla main-hand attack.
- **Tools:** explicit arm-tool input while targeting a block. Only allow a defined list of safe block-breaking/harvesting interactions; validate reach, hardness, permissions, tool durability, drops, and block updates server-side.
- **Item use:** explicit arm-use input while targeting a block or entity. Invoke only supported item-use behavior through server-authoritative game interaction paths; validate permissions, reach, cooldowns, item consumption, and resulting inventory changes.

The input bindings and exact client/server interaction APIs must be verified against the project's Minecraft 1.21.1 NeoForge mappings before implementation. Do not ship a fake implementation that simply fires a second ordinary main-hand event: that can duplicate block placement, attacks, item consumption, or interaction side effects.

## Slot and persistence

- Implement a genuine Sophisticated Backpacks upgrade through the supported upgrade API, not just a standalone item.
- Provide a dedicated arm-item slot in the upgrade's configuration UI. The item can be inserted, removed, and swapped by the owner.
- Persist the item stack through the upgrade/backpack save lifecycle. Never duplicate the item during menu transfer, death handling, or save/reload.
- When the slot is empty, the arm is inactive.
- Avoid mutating the player's vanilla main-hand or offhand stacks to simulate the extra slot.
- Server validates all arm actions; clients request actions but cannot claim hits, block breaks, or successful item uses.

## Recipe and presentation

Use a Create Mechanical Arm, a chestplate, and a chest as the core crafting ingredients, with the exact recipe arrangement and item IDs checked against the pinned Create build. Keep it survival-accessible. Add a backpack-mounted arm model/animation after the actual upgrade and action logic work.

## Compatibility

- Pin and verify Sophisticated Backpacks and Sophisticated Core versions for Minecraft 1.21.1 / NeoForge.
- Isolate Sophisticated Backpacks integration behind a compatibility boundary if it remains optional.
- Do not import client-only classes into common/server code.
- If the optional dependency is absent, the mod must still load unless project policy deliberately makes it required.

## Acceptance checklist

- [ ] Upgrade installs in Sophisticated Backpacks.
- [ ] Dedicated arm slot accepts, persists, and returns an item without loss or duplication.
- [ ] Player can simultaneously hold a torch offhand, pickaxe main hand, and sword in the arm slot.
- [ ] Combat, tool, and item-use actions each have separate explicit activation.
- [ ] No automatic duplicate of vanilla main-hand attacks/interactions.
- [ ] Server checks reach, target/block permissions, cooldown, durability, item count, and resulting inventory changes.
- [ ] Failed/denied actions do not consume or duplicate the supplied item.
- [ ] Recipe, item model, localization, UI, and backpack rendering work.
- [ ] Build and test with the exact modpack versions in a clean world.

## Status

Design specification only. Runtime implementation, Sophisticated Backpacks API wiring, arm slot UI, action input handling, recipe/model, and in-game tests are not yet complete. The repo's existing CI does not verify this feature until implementation is added.
