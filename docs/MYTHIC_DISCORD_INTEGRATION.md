# Mythic server integration

The Mythic build is selected with `-Pis_Mythic_version=true`. The default/public build uses `false` and excludes the entire `com.innygr.icreateordernetworking.mythic` package, including its Discord HTTP client and team-mapping loader. No Discord token or webhook is bundled in the JAR or sent to clients.

## Build commands

```sh
gradle --no-daemon -Pis_Mythic_version=true build
gradle --no-daemon -Pis_Mythic_version=false build
```

The JAR manifest records `Is-Mythic-Version: true|false`.

## Discord event notifications

On the first Mythic server start, the integration creates `config/icreate-order-networking-mythic.json`:

```json
{
  "enabled": false,
  "serverName": "Minecraft server",
  "discordWebhookUrl": ""
}
```

Set `enabled` to `true` and set `discordWebhookUrl` to a Discord HTTPS webhook URL in the dedicated server's config directory. Never commit this file or share the webhook URL. Events are sent asynchronously, HTTP failures do not block the server tick, and user/target text is sanitized to prevent mass mentions. Player join/leave and server lifecycle events are wired now.

The missile subsystem can publish launch, impact, and interception notifications through `MythicIntegration.missileLaunched`, `missileImpacted`, and `missileIntercepted`. These are integration hooks for the missile assembly/flight/defence code; they are not automatically emitted by a full missile flight system yet.

## FTB Teams mapping

The integration creates `config/icreate-order-networking-mythic-teams.json` with schema version 1. Add a mapping for each FTB team ID:

```json
{
  "schemaVersion": 1,
  "teams": {
    "your-ftb-team-id": {
      "name": "Your team name",
      "discordRoleId": "optional-discord-role-id"
    }
  }
}
```

This is a server-side, read-only mapping used to give missile events a stable team display name. It does not modify FTB Teams membership or automatically synchronize Discord bot account-link requests. The current Discord notification transport is a webhook; connecting the bot's account-link/approval queue requires a matching authenticated API endpoint in the bot process.
