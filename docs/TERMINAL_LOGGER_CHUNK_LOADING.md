# Terminal Logger chunk loading

Terminal Loggers currently force-load the single chunk containing the logger. This lets the logger's server tick and local automation continue when players leave the area, and the forced-chunk setting is stored by Minecraft with the world.

## Scope and limitations

- Only the logger's own chunk is forced; adjacent chunks, train routes, and airship routes are not automatically loaded.
- This does not guarantee that third-party transport blocks work unattended; PSI and Aeronautics behaviour still needs in-game verification.
- Chunk loading has a server performance cost. Avoid placing large numbers of loggers until a configurable server-wide limit and an owner-controlled toggle are added.
- Test placement, removal, world restart, and multiple loggers in the same chunk on a dedicated server before treating this as release-ready.
