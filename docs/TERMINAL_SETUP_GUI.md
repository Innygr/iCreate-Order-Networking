# Terminal Setup GUI and Resource Linking

This is the interaction specification for the first-time setup and later configuration screen of a terminal. It describes intended behavior, not an already implemented GUI.

## Setup flow

1. On the first server-side right-click of an unconfigured terminal, bind it to the interacting player's UUID and current FTB Team identity, if any, and open the setup screen.
2. Existing configured terminals open their normal terminal screen for authorized users. Right-clicking must never silently rebind ownership.
3. Show a persistent identity header with owner/team and configuration status.
4. Require a valid terminal name and transfer mode before saving. Location and public names may have defaults but remain editable.
5. Validate all resource links and permissions on the server when the player saves; client GUI state is never trusted as authority.
6. Provide Save, Cancel, and Reset-to-unsaved-changes behavior. Closing the screen without saving must not partially apply edits.

## Identity fields

- **Terminal Name**: private/internal name used in configuration, logs, and administration.
- **Location Name**: human-readable place/factory name, such as "North Foundry".
- **Public Name**: label shown to other authorized terminals/networks or in public discovery, such as "North Foundry — Iron Supply". Only expose it when sharing/public visibility is enabled.
- Show the bound owner and team in a read-only section. Changing ownership should be a separate explicit, permission-checked action, not a side effect of editing names.
- Prevent blank names and enforce a reasonable server-side length limit. Normalize whitespace and reject formatting/control characters that could make logs or lists misleading.

## Resource-link interaction

Each resource link is a row/card containing:
- A user-defined link label (optional).
- The target stock endpoint/inventory name and location.
- An optional item filter/tag or selected resource list.
- A live availability summary, marked **stale/unavailable** when the target cannot currently be queried.
- Link actions: **Inspect**, **Edit**, **Unlink**. Provide a clear add-link action.

### Creating a link

1. Player selects **Add stock link**.
2. GUI enters link-selection mode and explains: "Select a compatible stock inventory in the world, or cancel."
3. Player clicks a compatible stock endpoint/block to target it. Validate dimension, reach/permissions, endpoint type, and whether the target can be linked.
4. Return to setup with the endpoint shown as a pending link. Let the player configure optional item filters before saving.
5. Avoid making a link just because the player clicked an arbitrary block. If no valid endpoint is selected, show a useful failure message and preserve the current form.

The endpoint should be stored by dimension and position plus a stable endpoint identity when available, not as a raw client-supplied block position alone. Revalidate it on the server on save and before any stock operation.

### Right-click to inspect a stock link

- Right-clicking an existing resource-link row opens an **Inventory Inspection** view; it must not remove, retarget, or otherwise mutate the link.
- The view shows endpoint name/location, connection status, item icons, item names, available counts, reserved counts (if exposed by the endpoint API), and the last successful refresh time.
- Clearly distinguish **available** from **physical total**. Available count must exclude reserved stock so it is not promised twice.
- For inventories too large to display at once, use pagination/search and optional filter chips. Sort deterministically (e.g. item name, then item ID) to avoid rows jumping around.
- Refresh requests are server-authoritative and rate-limited. If the endpoint is unloaded or temporarily unreachable, show cached data with a visible stale timestamp and a status such as "Endpoint unloaded — counts may be outdated"; never present stale counts as live.
- The inspection view is read-only. Moving items, changing filters, or unlinking must require separate explicit actions.
- Provide Back to Setup without losing unsaved form edits.

### Link health and errors

- Display connected, unloaded, missing, permission-denied, and incompatible states distinctly.
- A broken/missing endpoint must not be silently substituted with another inventory at the same position.
- Unlinking removes only this terminal's reference; it must not delete or alter the target inventory.
- Enforce dimension/range, team/network permissions, and access policy server-side for link creation, inspection, refresh, and unlinking.

## Transfer mode

Provide a mutually exclusive **Send / Receive / Both** selector:
- **Send**: terminal advertises/exports eligible stock from linked sources. Do not export reserved items or exceed configured filters/limits.
- **Receive**: terminal accepts inbound deliveries and reports valid destination capacity.
- **Both**: enables both behaviors, subject to endpoint filters and network permissions.

Show concise helper text for the selected mode. The mode controls logistics behavior; it does not override ownership, permissions, or route availability.

## Recommended additional settings

Keep advanced options in a collapsible section so first-time setup stays simple:

- **Network visibility**: Private (default), Team, or Explicitly Public. Public visibility must be a deliberate opt-in and must not expose nearby private factories automatically.
- **Allowed resources**: allowlist/filter for item types or tags; empty-filter semantics must be explicit (recommend "all compatible resources" with a warning/preview).
- **Priority**: default/low/high, used only as a tie-breaker after safety and reservation rules.
- **Request and export limits**: optional per-request maximum and export threshold; validate non-negative values.
- **Endpoint behavior**: allow linking multiple stock endpoints, with per-link enable/disable and ordering/priority.
- **Notifications**: on/off for failed link, low stock, and completed delivery.
- **Diagnostics**: connection state, last successful query, last configuration save, and a test-link action that only reads stock.

Avoid exposing chunk tickets, raw internal IDs, or advanced recovery switches in the ordinary setup screen. Those belong in the Terminal Logger/admin diagnostics UI.

## Save, validation, and security

- Keep edits client-side until Save succeeds; display field-level validation errors and preserve user input after a failed save.
- The server checks owner/team access, name limits, endpoint validity, dimensions/range, and transfer-mode constraints.
- Save configuration atomically so partial failure cannot leave half the links updated.
- Avoid repeated inventory scans per GUI tick. Refresh on open, explicit refresh, and a modest configured interval; cache results and rate-limit requests.
- On terminal destruction, remove the terminal's configuration and its links. Do not destroy linked inventories. Release terminal-owned jobs/tickets through the logistics cleanup path.
- The first-use binding and configuration save should be robust against two players racing to configure the same terminal.

## Suggested screen navigation

- **Overview**: identity, mode, link summaries, Save/Cancel.
- **Inspect Link**: read-only stock list and endpoint health, with Back.
- **Add Link**: target-selection instructions, target validation, filters.
- **Advanced**: visibility, priorities, limits, notifications, diagnostics.

Keyboard/controller navigation and clear status text should accompany mouse interactions; right-click inspection is a shortcut, not the only way to inspect a link.
