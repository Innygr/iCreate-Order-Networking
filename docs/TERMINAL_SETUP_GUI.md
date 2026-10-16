# GUI implementation status

## Order Terminal

The Order Terminal screen now uses a code-drawn industrial frame: dark inset panel, steel header, amber accent bars, section dividers, and a persistent status strip. It keeps the existing server packet actions for create, claim, fulfil, cancel, and shipment dispatch, while improving field grouping and local validation feedback.

## Current limitations

- Minecraft's standard edit boxes and buttons are still used; custom-styled widgets and texture assets are a follow-up.
- The screen does not yet fetch a live order list or shipment history from the server. The user must provide an order UUID.
- Client feedback confirms only that a request was sent, not that the server accepted or completed it.
- Server-side packet validation remains authoritative.
- Dedicated-client visual and interaction testing is still required.

## Next UI milestones

1. Add server-to-client response packets for accepted/rejected actions and order snapshots.
2. Add searchable order list and shipment status/history.
3. Implement terminal setup and stock-link management screens from `TERMINAL_SETUP_GUI.md`.
4. Replace default widgets with custom-styled controls and add matching textures where useful.
