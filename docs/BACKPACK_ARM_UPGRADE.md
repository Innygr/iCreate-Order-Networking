# Mechanical Arm Backpack Upgrade

Target: Minecraft 1.21.1, NeoForge 21.1.x, Create 6.x, Sophisticated Backpacks.

## Core concept

The Mechanical Arm Upgrade adds an **extra main-hand slot** to the wearer, but the arm itself is not supplied with the upgrade. The player must supply an item for the arm to hold/use. Treat it as a second hand/tool mount, not as an automatic item vacuum.

## Intended behavior

- The upgrade is installed in a Sophisticated Backpack upgrade slot.
- The player supplies a held item into a dedicated arm slot in the upgrade's UI/configuration.
- The arm uses that supplied item as an additional main-hand item for explicitly supported interactions.
- The supplied item is not consumed merely by being equipped. Normal durability/consumption rules apply when the arm uses it.
- The arm must not duplicate items or bypass vanilla server-side interaction, permission, cooldown, durability, or inventory rules.
- If no item is supplied, the arm remains idle.
- Define supported actions and activation rules before implementing use: e.g. whether it can attack, mine, place blocks, use tools, or interact with blocks. Avoid assuming every player action can be safely replayed for a second hand.
- Keep the behavior server-authoritative and make the upgrade safe with other Sophisticated Backpacks upgrades.

## Recipe direction

The user proposed combining a Create Mechanical Arm, a chestplate, and a chest as the core ingredients. Confirm exact item IDs and final shaped arrangement against the pinned Create build before shipping. Keep it survival-accessible.

## Integration approach

- Use Sophisticated Backpacks' actual upgrade API for a real installable upgrade, not just a standalone item.
- Isolate the optional integration unless Sophisticated Backpacks is deliberately made a required dependency.
- Pin and verify Sophisticated Backpacks and Sophisticated Core versions for Minecraft 1.21.1 / NeoForge.
- Register the upgrade item, dedicated supplied-item slot, menu/interaction behavior, recipe, model, and localization.
- Treat backpack-mounted arm model/animation as presentation work after the server-side slot and action behavior are secure.

## Acceptance checklist

- [ ] Upgrade installs in Sophisticated Backpacks.
- [ ] Player can supply and remove an item in the arm's dedicated slot.
- [ ] The supplied item persists across save/reload without duplication or loss.
- [ ] No item is used when the slot is empty.
- [ ] Only explicitly supported actions can be triggered through the arm.
- [ ] Server validates permissions, target, cooldown, item count, durability, and resulting inventory changes.
- [ ] Upgrade doesn't duplicate vanilla main-hand events or fire interactions twice unintentionally.
- [ ] Recipe works in survival using the intended Create/vanilla item IDs.
- [ ] Test with the exact modpack versions before marking complete.

## Open design decision

The phrase “extra main hand” is not sufficient to safely implement all possible actions. Choose the first supported behavior set: **combat only**, **tools (mining/harvesting) only**, **block/item use only**, or **all of these with explicit per-action activation controls**. The last option is more complex and needs careful event/interaction design.

## Status

Specification only. The runtime upgrade, item registration, supplied-item slot, interaction handling, recipe, model, and tests have not yet been implemented or CI-verified.
