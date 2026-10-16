# Missile System Design

## Assembly requirements

A valid missile assembly must contain:

- 4 fins
- 1 custom missile thruster
- 1 fluid tank containing a supported fuel (water or lava)
- 1 navigator
- 1 cone
- At least 1 explosive payload

The assembly validator should report missing components and refuse launch until the build is valid.

## Navigation and guidance

The navigator is responsible for stable guidance rather than simply pointing the missile at the target once.

- **Plan altitude before ascent.** Before applying upward velocity, sample the direct launch-to-target corridor using already-loaded terrain. Select the lowest cruise altitude that clears the highest sampled terrain by a configurable safety margin (initially 8 blocks), while respecting the world's build-height limit.
- Sample the corridor at regular intervals (initially every 4 horizontal blocks). Do not load chunks just to plan or chase a target; if the required corridor crosses an unloaded chunk, postpone planning or fail launch safely.
- Estimate the fuel cost as climb distance + horizontal distance + descent distance. If this estimate exceeds the fuel-derived range budget, report insufficient fuel/range before takeoff rather than wasting fuel on an impossible route.
- For long routes, a later improvement can plan multiple waypoints and re-plan around high terrain; the first implementation uses a direct corridor and conservative clearance.
- Keep the target in world coordinates and update guidance every tick on the server.
- For block targets, aim at the target block's center. For player targets, resolve the saved player UUID and continually steer toward the player's current position.
- Use smooth course corrections and limit turning acceleration so the missile does not jitter, instantly reverse, or overshoot while chasing moving targets.
- Compensate for the missile's current velocity and lead moving targets where possible instead of always aiming at their last position.
- Re-check target validity and maximum range throughout flight. If a player target disconnects or becomes invalid, stop active guidance and apply a consistent fail-safe (coast with current velocity rather than teleporting or abruptly reversing).
- Keep guidance and launch validation server-authoritative; synchronize position and orientation to clients.
- Provide a configurable steering strength/turn-rate so the guidance can be tuned for gameplay.

## Targeting remote

The targeting remote is the player's control item.

- Right-click a block to store that block's coordinates as a fixed target.
- Right-click a player to select that player as a heat-seeking target.
- Right-click in the air to open the remote control screen.
- The control screen should allow the player to link/select a missile, inspect readiness and range, and issue a launch command.
- Store target data in the remote item so it persists between uses and survives relogging. Block targets store coordinates; player targets store a stable player UUID, not just a username or the player's current coordinates.
- A missile should be referenced by a stable identifier rather than only its current coordinates.
- Target-setting and launch requests must be handled and validated server-side.

## Thruster and fuel

The missile uses a custom `Missile Thruster` block. When it is actively firing, the thruster should emit light and heat; nearby flammable blocks can catch fire. The registered standalone block currently responds to redstone power as a testable ignition/lighting behaviour. Actual missile-mounted thrust and fuel consumption will be wired into the missile entity/assembly implementation.

Supported fuels:

- **Water:** valid low-efficiency fuel. It provides the baseline thrust/range but consumes fuel more quickly or produces less range per tank.
- **Lava:** valid high-efficiency fuel. It provides more thrust/range per tank than water.
- Do not accept arbitrary fluids as fuel. Fuel type must be detected from the tank's actual contents.
- Keep fuel efficiency values configurable. For the initial balance, water provides 50% of lava's range per equal tank capacity.
- When fuel runs out, shut down the thruster and let the missile coast under its existing velocity.

Maximum range should account for tank count and fuel efficiency:

`maximumRange = tankCount * 100 * fuelEfficiency`

Use `fuelEfficiency = 0.5` for water and `1.0` for lava initially. Thus one full tank gives 50 blocks on water or 100 blocks on lava; two tanks give 100 or 200 blocks respectively. The altitude planner estimates route cost as climb + horizontal travel + descent and rejects a launch if that estimate exceeds this range budget.

If a missile exceeds its maximum range during flight, shut the thruster off immediately. Preserve its current velocity and let it coast under flight physics until it collides or comes to rest.

## Water landing and flotation

Water contact takes priority over the normal impact-detonation check:

- If the missile lands in water, it must not explode, regardless of impact speed.
- Transition it into a floating, inert state on the water surface.
- Stop propulsion and disable impact detonation while it is floating.
- Keep the missile entity or dud recoverable rather than deleting it.
- Use buoyancy and water-surface positioning so it floats on top instead of sinking. Avoid repeatedly triggering the impact handler while it bobs on the surface.

Sable may be used for physics if it is compatible with the project's Minecraft/NeoForge version and licensing. Treat it as an optional physics integration until its exact dependency and API are verified; keep the missile's water-state logic independent so a fallback buoyancy implementation remains possible.

## Impact and dud behaviour

On collision, first determine whether the collision is a water landing. Water landings never detonate and instead enter the floating inert state.

For non-water impacts, determine the missile's impact speed:

- If impact speed meets or exceeds the configured impact-speed threshold, detonate the explosive payload.
- If impact speed is below the threshold, the missile does not detonate and remains as a dud at the impact location.

Keep the threshold configurable so it can be tuned for gameplay. The payload must be present and valid for a detonation; otherwise the missile should not produce an explosive effect.

## Nuclear payload and radiation

A missile may contain a nuclear core instead of, or in addition to, a conventional explosive payload. The nuclear core must be an explicitly recognized assembly component; its presence changes the detonation outcome.

- When a valid nuclear core detonates, create a persistent radioactive blast zone around the impact point.
- Show the contaminated area with green radiation clouds.
- The cloud and its hazardous zone persist until rain clears them. Define the zone's radius and persistence in one place so they can be tuned.
- Players entering the contaminated zone receive the custom Radiation status effect. The effect visually turns the affected player green and deals damage over time (DoT).
- Prevent repeated effect application from stacking into unbounded damage; refresh or update the existing effect while the player remains exposed.
- Keep radiation damage and duration configurable, and apply the effect server-side. The green tint is visual feedback, not the source of the damage.
- Water-landing safety still takes priority: a missile that lands in water does not detonate, so it must not create a nuclear blast or radiation zone.

### Implementation status

The current code implements the persistent per-dimension radiation-zone data, rain cleanup, green dust cloud particles, server-side exposure checks, the Radiation status effect, and the Nuclear Core assembly-marker item. The zone entry point is `RadiationService.createBlastZone(ServerLevel, BlockPos, int)`; a missile detonation handler should call it once the missile-flight system exists.

The custom missile thruster block is registered and can be redstone-powered to light up and attempt to ignite nearby flammable surroundings. The water/lava efficiency helper and pre-launch altitude planner are implemented as reusable utilities, but neither is yet connected to a working missile tank, assembly, launch, or flight entity. The actual missile assembly, targeting remote, flight entity, navigation controller, impact handling, and fuel-consuming propulsion are not yet implemented.

The Radiation effect currently communicates contamination with green effect particles and applies damage over time; a full green tint over the player's rendered model still needs a client-render implementation.

### Existing mod compatibility

The current `build.gradle` dependencies list NeoForge, Create, Ponder, Flywheel, and Registrate; they do not show a nuclear or radiation mod dependency. If another mod in the intended modpack already provides nuclear cores, radiation clouds, or a radiation effect, check its actual mod ID, API, and version before integrating it. Otherwise, implement the core marker, radiation-zone state, cloud visuals, and custom Radiation effect in this mod without requiring an unverified dependency.

## Launch checks

Before launching, verify:

1. The remote is linked to a loaded, valid missile assembly.
2. The assembly has all required components.
3. The tank contains a supported fuel and has sufficient fluid.
4. A valid block or player target has been set.
5. The target is within range based on the current fuel.
6. The player is permitted to command that missile.

If any check fails, do not launch and report the reason. For a player target, handle the target becoming unavailable deterministically and report the resulting missile status. The first implementation can separate assembly validation and remote/target persistence from the later physical missile flight and impact simulation.


## Optional Missile Tag

The optional Missile Tag is an identity marker for a missile assembly. Its block entity persists a stable missile UUID, team name, and a copy of the team's banner item stack. Assembly code can assign these through `MissileTagBlockEntity.configureIdentity(...)`; a missile's death message can use `MissileDeathMessages.explodedByTeam(...)` to produce “player was exploded via Team's missile”, falling back to “player was exploded via missile” when the tag has no team name. This identity data is ready for the future missile entity/impact handler. The current repository still has no missile entity to call the death-message helper, and the tag's small banner/sign-style in-world renderer remains a follow-up client-rendering task.


## Orbital missile variant

An orbital missile is a separate assembly mode: its designated structure must be assembled upside down, with the trigger/anchor block at or above Y=250. The structure checker should reject normal orientation, missing required blocks, and anchor positions below Y=250 before launch. Orbital kills use dedicated death-message translation keys: `%1$s was orbitally struck by missile` for an untagged missile and `%1$s was struck by %2$s’s orbital missile` for a tagged missile. The current patch adds the assembly validator and message helper; wiring them into a live missile entity/assembly interaction remains dependent on the not-yet-implemented missile launch system.


## Missile component recipe specifications

These are the agreed recipe designs for implementation. They are design specs, not currently loadable crafting recipes: the missile fin, missile nose, missile navigator, altitude sensor, and navigation table still need registered game items/blocks before recipe JSON can reference them.

### Missile fin

- **Output:** 4 Missile Fins
- **Pattern:** three iron ingots arranged diagonally

```text
I..
.I.
..I
```

`I` = Iron Ingot.

### Missile nose

- **Output:** 1 Missile Nose
- **Pattern:** an iron ingot directly above an iron block

```text
I
B
```

`I` = Iron Ingot; `B` = Iron Block.

### Missile navigator

- **Output:** 1 Missile Navigator
- **Ingredients:** 1 Altitude Sensor, 1 Navigation Table, and 1 Cartography Table.

The exact shaped/shapeless arrangement can be settled when the component items are registered. Keep the recipe focused on guidance equipment rather than adding extra expensive materials.
