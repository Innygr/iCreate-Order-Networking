# Missile System Design

## Assembly requirements

A valid missile assembly must contain:

- 4 fins
- 1 thruster
- 1 fuel tank or compatible fluid tank
- 1 navigator
- 1 cone
- At least 1 explosive payload

The assembly validator should report missing components and refuse launch until the build is valid.

## Targeting remote

The targeting remote is the player's control item.

- Right-click a block to store that block's coordinates as a fixed target.
- Right-click a player to select that player as a heat-seeking target. The missile tracks the selected player's current position during flight.
- Right-click in the air to open the remote control screen.
- The control screen should allow the player to link/select a missile, inspect readiness and range, and issue a launch command.
- Store target data in the remote item so it persists between uses and survives relogging. Block targets store coordinates; player targets store a stable player UUID, not just a username or the player's current coordinates.
- A missile should be referenced by a stable identifier rather than only its current coordinates.
- Target-setting and launch requests must be handled and validated server-side.

## Range and propulsion

Each installed fluid tank provides 100 blocks of maximum range:

`maximumRange = fluidTankCount * 100`

Examples:
- 1 tank: 100 blocks
- 2 tanks: 200 blocks
- 5 tanks: 500 blocks

For a block target, measure 3D distance from the launch position to the center of the target block. For a heat-seeking target, measure 3D distance from the launch position to the player's current position. If the target exceeds the missile's maximum range during flight, shut the thruster off immediately.

After the thruster cuts out, preserve the missile's current velocity and let it coast under the flight physics until it collides or comes to rest. A missile must have at least one valid tank to have non-zero range.

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

The repository does not yet contain the missile assembly, targeting remote, flight entity, impact handling, or detonation pipeline. Therefore the Nuclear Core is registered but is not yet wired into a launchable missile. The Radiation effect currently communicates contamination with green effect particles and applies damage over time; a full green tint over the player's rendered model still needs a client-render implementation.

### Existing mod compatibility

The current `build.gradle` dependencies list NeoForge, Create, Ponder, Flywheel, and Registrate; they do not show a nuclear or radiation mod dependency. If another mod in the intended modpack already provides nuclear cores, radiation clouds, or a radiation effect, check its actual mod ID, API, and version before integrating it. Otherwise, implement the core marker, radiation-zone state, cloud visuals, and custom Radiation effect in this mod without requiring an unverified dependency.

## Launch checks

Before launching, verify:

1. The remote is linked to a loaded, valid missile assembly.
2. The assembly has all required components.
3. The missile has sufficient fuel.
4. A valid block or player target has been set.
5. The target is within range.
6. The player is permitted to command that missile.

If any check fails, do not launch and report the reason. For a player target, handle the target becoming unavailable (for example, disconnecting or no longer being a valid target) deterministically and report the resulting missile status. The first implementation can separate assembly validation and remote/target persistence from the later physical missile flight and impact simulation.
