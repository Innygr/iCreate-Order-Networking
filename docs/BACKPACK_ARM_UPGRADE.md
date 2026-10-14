# Mechanical Arm Backpack Upgrade

Target: Minecraft 1.21.1, NeoForge 21.1.x, Create 6.x, Sophisticated Backpacks + Sophisticated Core.

## Player-facing design

The Mechanical Arm Upgrade adds a third held-item slot, separate from the vanilla main hand and offhand. The player supplies the item; the arm applies item-aware behavior rather than acting as a generic duplicate mouse button.

Example:
- Offhand: Torch
- Main hand: Pickaxe
- Backpack arm: Sword

The arm is not an item magnet and does not automatically collect drops. Its supplied item stays in a dedicated upgrade slot and is used by the arm's configured behavior.

## Arm-slot quick transfer

Use a rapid double-tap of the vanilla swap-hands/offhand keybind as the quick-transfer gesture:
- A single press retains vanilla swap-hands behavior.
- A second press within a short configurable window (default target: 250 ms) requests transfer to/from the arm slot instead of performing a second vanilla swap.
- If the arm slot is empty and the offhand contains an item, move the offhand stack into the arm slot and leave the offhand empty.
- If the arm slot contains an item and the offhand is empty, move the arm stack into the offhand.
- If both slots contain items, swap the arm and offhand stacks.
- If both are empty, do nothing.
- Transfer the full stack, preserving item data, enchantments, durability, and components. Do not clone or delete items.
- The operation must be server-authoritative and work only when the player has the compatible Mechanical Arm upgrade installed.
- Respect screen/menu state: do not trigger while typing in chat or while an unrelated container/menu has focus. Provide a configurable keybind fallback if another mod conflicts with the swap-hands key.
- If a single press already triggered vanilla swapping, the implementation must handle the double-tap without a confusing or irreversible extra swap. Verify the correct event/input interception for the target NeoForge version before implementing; do not implement by simply detecting two key events after both vanilla swaps have already happened.

## Item-aware behavior

The arm selects behavior based on the supplied item and the selected mode. Autonomous actions must be bounded, server-authoritative, configurable, and interruptible.

- **Sword — automatic defense:** Detect nearby hostile mobs and attack eligible threats within configured range. Respect attack cooldown, reach, target validity, item durability, and server-side permissions. Prioritize immediate threats; do not blindly attack neutral/passive entities or repeat the vanilla main-hand attack.
- **Shield — projectile defense:** Detect incoming projectiles that threaten the player and raise the shield when the arm can plausibly block them. Account for facing, timing, cooldown, and shield durability. Do not claim a block succeeded until the server validates the result.
- **Bow — aim, then player-authorized fire:** Aim at a valid target while equipped. The player right-clicks to authorize firing; the arm handles the draw/release cycle. Require real ammunition, respect bow draw time, line of sight, cooldowns, durability, and server validation. Aiming alone must not consume arrows or fire.
- **Pickaxe — mining:** Mine eligible targeted/designated blocks while mining is enabled. Respect reach, block hardness, tool effectiveness, harvest requirements, permissions, drops, durability, and block updates. Never mine continuously without a valid target and active configuration.
- **Axe — mode-dependent chop or combat:** In chop mode, break eligible axe-appropriate blocks; in combat mode, attack eligible hostile targets. Keep modes explicit to prevent accidental chopping or attacks.
- **Shovel — mode-dependent dig or path:** In dig mode, break eligible shovel-appropriate blocks. In path mode, use the supported path-making interaction on valid blocks. Do not silently substitute one mode for the other.
- **Hoe — farming interactions:** Perform supported hoe actions such as tilling valid soil. Validate block conditions, permissions, durability, and resulting block state.
- **Blocks — configurable placement:** Support a look-at-target placement mode and configurable patterns such as lines, walls, and floors. Consume real blocks from an explicitly defined inventory source; validate reach, replaceability, collision, permissions, and each placement server-side. Pattern placement must stop safely when blocked or out of materials.
- **Torches — lighting:** Support an optional lighting mode that places torches at configured intervals or when suitable dark areas are encountered, using actual torches and valid placement checks. Held-item dynamic lighting is a separate optional feature and must not be implied by ordinary torch behavior.

Unsupported items should remain inert or offer only explicitly supported generic use behavior. Do not guess at arbitrary modded-item behavior.

## Configuration and controls

Provide per-upgrade settings for:
- Enable/disable autonomous behavior.
- Behavior mode where an item has multiple roles (axe chop/combat, shovel dig/path).
- Detection range and action limits, within safe server-enforced bounds.
- Block placement mode (look-at-target or pattern) and pattern parameters.
- Torch placement/light mode and interval.
- Targeting/priority options where applicable.

The bow uses a distinct player authorization input to fire. Mining and placement should have explicit enable/disable controls and a clear target rule. Defensive sword/shield behaviors may run automatically only when enabled. Inputs and exact interaction APIs must be verified against the project's Minecraft 1.21.1 NeoForge mappings before implementation.

Do not ship a fake implementation that simply fires ordinary main-hand events twice: that can duplicate block placement, attacks, item consumption, or interaction side effects.

## Slot and persistence

- Implement a genuine Sophisticated Backpacks upgrade through the supported upgrade API, not just a standalone item.
- Provide a dedicated arm-item slot in the upgrade's configuration UI. The item can be inserted, removed, and swapped by the owner.
- Persist the item stack and configuration through the upgrade/backpack save lifecycle. Never duplicate the item during menu transfer, death handling, or save/reload.
- When the slot is empty, the arm is inactive.
- Avoid mutating the player's vanilla main-hand or offhand stacks to simulate the extra slot.
- Server validates all arm actions; clients request actions but cannot claim hits, block breaks, successful blocks, or successful item uses.

## Recipe and presentation

Use a Create Mechanical Arm, a chestplate, and a chest as the core crafting ingredients, with the exact recipe arrangement and item IDs checked against the pinned Create build. Keep it survival-accessible. Add a backpack-mounted arm model/animation after the actual upgrade and action logic work.

## Compatibility

- Pin and verify Sophisticated Backpacks and Sophisticated Core versions for Minecraft 1.21.1 / NeoForge.
- Isolate Sophisticated Backpacks integration behind a compatibility boundary if it remains optional.
- Do not import client-only classes into common/server code.
- If the optional dependency is absent, the mod must still load unless project policy deliberately makes it required.
- Ensure automated actions do not bypass protection claims, adventure-mode restrictions, server event hooks, or item/block interaction rules.

## Acceptance checklist

- [ ] Upgrade installs in Sophisticated Backpacks.
- [ ] Dedicated arm slot accepts, persists, and returns an item without loss or duplication.
- [ ] Double-tap offhand/swap-hands key transfers the offhand item to the arm slot; reverse transfer and swap rules work without item loss.
- [ ] Single-tap swap-hands retains vanilla behavior and double-tap detection does not cause accidental extra swaps.
- [ ] Player can simultaneously hold a torch offhand, pickaxe main hand, and sword in the arm slot.
- [ ] Sword defense and shield projectile blocking are configurable and server-validated.
- [ ] Bow aims automatically but only fires after player authorization, consuming valid ammunition.
- [ ] Pickaxe, axe, shovel, and hoe behaviors respect tool rules and their configured modes.
- [ ] Block placement supports look-at-target and configured patterns with safe stopping conditions.
- [ ] Torch behavior uses real torches; dynamic held-item lighting is treated separately.
- [ ] Unsupported items do not trigger arbitrary or unsafe behavior.
- [ ] No automatic duplicate of vanilla main-hand attacks/interactions.
- [ ] Server checks reach, target/block permissions, cooldown, durability, item count, and resulting inventory changes.
- [ ] Failed/denied actions do not consume or duplicate the supplied item.
- [ ] Recipe, item model, localization, UI, and backpack rendering work.
- [ ] Build and test with the exact modpack versions in a clean world.

## Status

Design specification only. Runtime implementation, Sophisticated Backpacks API wiring, arm slot UI, action input handling, recipe/model, and in-game tests are not yet complete. The repo's existing CI does not verify this feature until implementation is added.
