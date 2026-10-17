const http = require("node:http");
const crypto = require("node:crypto");

const MAX_BODY_BYTES = 8192;

// Configure ICREATE_MYTHIC_API_TOKEN and ICREATE_MYTHIC_EVENTS_CHANNEL_ID.
// Mount behind an HTTPS reverse proxy if the Minecraft server is remote.
// Usage: const { startMythicApi } = require("./mythic-api"); startMythicApi(client);
function startMythicApi(client, options = {}) {
  const token = process.env.ICREATE_MYTHIC_API_TOKEN;
  const channelId = process.env.ICREATE_MYTHIC_EVENTS_CHANNEL_ID;
  const host = options.host || process.env.ICREATE_MYTHIC_API_HOST || "127.0.0.1";
  const port = Number(options.port || process.env.ICREATE_MYTHIC_API_PORT || 8787);

  if (!token || token.length < 32) throw new Error("ICREATE_MYTHIC_API_TOKEN must be at least 32 characters.");
  if (!channelId) throw new Error("ICREATE_MYTHIC_EVENTS_CHANNEL_ID must identify the events channel.");
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error("Invalid ICREATE_MYTHIC_API_PORT.");

  const expected = Buffer.from(token, "utf8");
  const server = http.createServer(async (req, res) => {
    res.setHeader("Cache-Control", "no-store");
    res.setHeader("X-Content-Type-Options", "nosniff");
    if (req.method !== "POST" || req.url !== "/api/icreate/events") return res.writeHead(404).end();

    const auth = req.headers.authorization || "";
    const supplied = Buffer.from(auth.startsWith("Bearer ") ? auth.slice(7) : "", "utf8");
    if (supplied.length !== expected.length || !crypto.timingSafeEqual(supplied, expected)) {
      return res.writeHead(401, { "Content-Type": "application/json" }).end(JSON.stringify({ error: "unauthorized" }));
    }
    if (!String(req.headers["content-type"] || "").toLowerCase().startsWith("application/json")) return res.writeHead(415).end();

    try {
      const chunks = [];
      let size = 0;
      for await (const chunk of req) {
        size += chunk.length;
        if (size > MAX_BODY_BYTES) {
          res.writeHead(413).end();
          req.destroy();
          return;
        }
        chunks.push(chunk);
      }
      const body = JSON.parse(Buffer.concat(chunks).toString("utf8"));
      if (!body || body.source !== "icreate-order-networking" || typeof body.event !== "string" ||
          typeof body.details !== "string" || typeof body.timestamp !== "string") {
        return res.writeHead(400, { "Content-Type": "application/json" }).end(JSON.stringify({ error: "invalid event payload" }));
      }

      const eventName = body.event.replace(/[\r\n\x00-\x1f]/g, " ").slice(0, 80);
      const details = body.details.replace(/@everyone/g, "@\u200beveryone")
        .replace(/@here/g, "@\u200bhere").replace(/[\x00-\x08\x0b\x0c\x0e-\x1f]/g, "").slice(0, 1800);
      const channel = await client.channels.fetch(channelId);
      if (!channel || !channel.isTextBased() || typeof channel.send !== "function") throw new Error("Configured events channel is not a text channel.");
      await channel.send({ content: "**[iCreate Mythic] " + eventName + "**\n" + details, allowedMentions: { parse: [] } });
      res.writeHead(202, { "Content-Type": "application/json" }).end(JSON.stringify({ accepted: true }));
    } catch (error) {
      console.error("[Mythic API] Event rejected:", error.message);
      if (!res.headersSent) res.writeHead(400, { "Content-Type": "application/json" }).end(JSON.stringify({ error: "event could not be processed" }));
    }
  });

  server.listen(port, host, () => console.log("[Mythic API] Listening on " + host + ":" + port + "/api/icreate/events"));
  return server;
}

module.exports = { startMythicApi };
