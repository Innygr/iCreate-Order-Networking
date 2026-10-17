# Mythic companion bot API

The Mythic build can post events to a companion bot over HTTPS. Configure `apiBaseUrl` and `apiToken` in the dedicated server's `config/icreate-order-networking-mythic.json`. Keep the token private; never commit the server config or place credentials in client-side files.

The mod posts to `POST /api/icreate/events` with `Authorization: Bearer <apiToken>` and a JSON body containing `source`, `event`, `details`, and an ISO-8601 `timestamp`. The bot should return a 2xx status after accepting an event and 401/403 for an invalid token. The mod validates HTTPS URLs and sends requests asynchronously away from the Minecraft server tick.

Events include server lifecycle and player join/leave, plus missile launch/impact/interception when the relevant gameplay handlers call the hooks.

**Important:** this repository contains the Minecraft mod, not the companion bot's `index.js`. The bot host still needs to implement and mount the authenticated HTTP endpoint. The existing `/mc-link` and team-sync approval queue are not connected by this outbound event client alone.