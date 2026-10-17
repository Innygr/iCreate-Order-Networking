# Mythic companion bot API

The Mythic build can post events to a companion bot over HTTPS. Configure `apiBaseUrl` and `apiToken` in the dedicated server's `config/icreate-order-networking-mythic.json`. Keep the token private; never commit the server config or place credentials in client-side files.

The mod posts to `POST /api/icreate/events` with `Authorization: Bearer <apiToken>` and a JSON body containing `source`, `event`, `details`, and an ISO-8601 `timestamp`. Requests run asynchronously away from the Minecraft server tick.

## Bot-side receiver

A drop-in Node.js receiver is included at `bot-integration/mythic-api.js`. It uses Node's built-in HTTP and crypto modules and the existing Discord.js client. In the bot's entrypoint, add:

```js
const { startMythicApi } = require("./mythic-api");
startMythicApi(client);
```

Set `ICREATE_MYTHIC_API_TOKEN` (at least 32 random characters) and `ICREATE_MYTHIC_EVENTS_CHANNEL_ID` in the bot host environment. The receiver binds to `127.0.0.1` by default; use an HTTPS reverse proxy for remote Minecraft servers and set the mod's `apiBaseUrl` to that HTTPS origin. The receiver validates bearer credentials, limits request size, rejects malformed payloads, sanitizes mentions, and posts accepted events to the configured Discord channel.

Events include server lifecycle and player join/leave, plus missile launch/impact/interception when the relevant gameplay handlers call the hooks.

**Still outstanding:** this event receiver does not implement `/mc-link` or the team-sync approval queue, and the current mod repository does not yet contain a working missile flight/impact/defence entity that can call those hooks. Those require separate integration work.