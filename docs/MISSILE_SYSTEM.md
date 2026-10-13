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

- Right-click a block to store that block's coordinates as the target.
- Right-click in the air to open the remote control screen.
- The control screen should allow the player to link/select a missile, inspect readiness and range, and issue a launch command.
- Store target coordinates in the remote item data so they persist between uses and survive relogging.
- A missile should be referenced by a stable identifier rather than only its current coordinates.
- Target-setting and launch requests must be handled and validated server-side.

## Range

Each installed fluid tank provides 100 blocks of maximum range:

`maximumRange = fluidTankCount * 100`

Measure range in 3D from the missile's launch position to the center of the selected target block. Targets beyond maximum range must be rejected with a clear status message. The missile must have at least one valid tank to have non-zero range.

Examples:
- 1 tank: 100 blocks
- 2 tanks: 200 blocks
- 5 tanks: 500 blocks

## Launch checks

Before launching, verify:

1. The remote is linked to a loaded, valid missile assembly.
2. The assembly has all required components.
3. The missile has sufficient fuel.
4. A target block has been set.
5. The target is within range.
6. The player is permitted to command that missile.

If any check fails, do not launch and report the reason. The first implementation can separate assembly validation and remote/target persistence from the later physical missile flight and impact simulation.
