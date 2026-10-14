# Mechanical Arm Backpack Upgrade

Target: Minecraft 1.21.1, NeoForge 21.1.x, Create 6.x, Sophisticated Backpacks.

## Goal

Add a custom Mechanical Arm Upgrade for Sophisticated Backpacks. The upgrade is installed in a Sophisticated Backpacks upgrade slot and uses a Create Mechanical Arm as its defining component. It periodically collects nearby dropped item entities into the backpack, subject to the backpack's insertion rules and remaining capacity.

## Gameplay behavior

- Only operate server-side; client code is visual only.
- Search a small configurable radius around the backpack wearer (initial target: 3 blocks) at a throttled interval (initial target: every 10 ticks), rather than every game tick.
- Insert through Sophisticated Backpacks' normal upgrade/storage handling so filters and inventory rules are respected.
- Remove only the amount successfully inserted from each dropped item entity.
- If the backpack cannot accept an item, leave the remainder in the world.
- Do not collect items through solid blocks or from unloaded chunks; use entities in the wearer's current level and a bounded AABB.
- Respect the upgrade's enabled state and avoid duplicate processing if multiple callbacks happen in one tick.
- The first version should not include a custom energy system; keep it early-game and simple.

## Recipe direction

The user proposed combining a Create Mechanical Arm, a chestplate, and a chest. Use those as the core recipe ingredients, but confirm the final shaped arrangement and item IDs against the pinned Create build before shipping. Keep the recipe survival-accessible and avoid late-game materials.

## Integration approach

- Keep Sophisticated Backpacks integration isolated behind a compatibility layer so the mod can still load when that optional mod is absent, unless the release deliberately makes Sophisticated Backpacks a required dependency.
- Implement a genuine Sophisticated Backpacks upgrade item using its supported upgrade API; do not merely register a standalone item and claim it works in a backpack slot.
- Pin and verify the Sophisticated Backpacks and Sophisticated Core API versions for Minecraft 1.21.1 / NeoForge.
- Register item/model/localization/recipe resources and add an upgrade-specific test or test checklist.
- Consider a backpack-mounted arm model/animation as a separate presentation step after server-side pickup behavior is working.

## Acceptance checklist

- [ ] Mod launches with the intended Sophisticated Backpacks + Sophisticated Core versions.
- [ ] Upgrade can be crafted and inserted into a compatible backpack upgrade slot.
- [ ] Nearby dropped items enter the backpack.
- [ ] Backpack filters and inventory capacity are respected.
- [ ] Full backpack leaves uninserted items in the world.
- [ ] Pickup is server-authoritative and does not duplicate or delete items.
- [ ] Behavior is throttled and bounded to a small radius.
- [ ] Verify optional-mod loading policy and recipe in a clean survival world.
- [ ] Test with the exact modpack version before marking the feature complete.

## Status

Specification only. The runtime upgrade, item registration, recipe, model, and tests have not yet been implemented or CI-verified.
