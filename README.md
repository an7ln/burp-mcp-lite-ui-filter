# burp-mcp-lite-ui-filter

Based on [0xdead4f/burp-mcp-lite](https://github.com/0xdead4f/burp-mcp-lite), licensed under MIT. Original copyright and license are preserved.

默认跟随 Burp HTTP 历史界面的显示过滤，减少 MCP 读取到的图片、视频和其他杂包。关闭扩展页的跟随开关可恢复全量读取。


## HTTP history display-following edition (experimental)

The MCP Lite tab has **Follow HTTP history display filter / 跟随历史显示过滤**, enabled by default.
After loading this JAR, open the main **Proxy > HTTP history** tab once. Apply your usual filters there.
On each history-tool call, this edition reads all rows in that table's filtered view (not merely the rows currently on screen), then retrieves matching native history IDs through Montoya. Additional MCP filters narrow that set further. UI sort order is not inherited; MCP `order` still controls capture order. Pending edits in the filter dialog take effect only after Apply.

Applies to `list_history`, `stats`, `endpoints`, `view_request`, `view_response`, and `match`.
`sitemap` and Collaborator are independent data sources and do not inherit HTTP-history filters.
Previously returned IDs may become unavailable after changing the display filter. Empty display means empty history, not all history.

**Implementation limit:** Montoya has no public getter for the active display filter. This adapter reads Swing JTable view IDs on the EDT (5-second timeout), recognizing the English/Chinese HTTP-history tab and the `#`, `Host`/`主机`, `URL` columns. Keep those columns available. It rejects missing, ambiguous, or malformed tables, with no automatic raw-history fallback. Other localizations, custom tab widgets, Burp UI changes, or duplicate history windows may need adapter updates. Concurrent incoming traffic can appear on the following call. This is display membership following, not an import of Burp's filter configuration.

Uncheck the option to immediately restore raw-history behavior. The option is persisted. To revert the extension binary, disable this extension and reload your previously saved original JAR. This does not delete captured traffic.

Automated tests cover a synthetic Swing history view and source filtering. Actual compatibility with the running Burp UI requires reloading the new JAR and comparing returned IDs against the main HTTP-history table; it is not established by the unit tests alone.

**Save 90%+ of your tokens on Burp Suite MCP — nine lean tools.**

This is a from-scratch Kotlin rewrite of PortSwigger's official Burp MCP extension, keeping the same SSE transport and Montoya entry point but cutting the tool surface down to **nine** tools tuned for low context cost. Headers off by default. Auth values redacted to length stubs. A `match` *predicate* tool that returns matched / not-matched + a bounded evidence snippet instead of dumping the whole body.

## What it replaces

The official `Burp MCP Server` ships 24 tools and serializes the full request + full response on every history entry. A 20-row history listing burns \~10k tokens before any work happens. `burp-mcp-lite` cuts that to a compact table by default, with field projection and filters on the way in and slicing on the way out.

Token cost, tool by tool — same job, both servers:

| Job | `burp-mcp-lite` | Burp's official MCP |
| --- | --- | --- |
| Browse 20 recent entries | `list_history` — \~400 tokens (compact table, headers off) | `get_proxy_http_history` — \~10 K tokens (full req + resp, JSON-serialized) |
| View one request | `view_request` — \~120 tokens (path + body, headers off, auth redacted) | one entry from `get_proxy_http_history` — \~1200 tokens (full bytes, headers + cookies on) |
| Verify a value is in a response | `match` — \~60 tokens (matched? + 240-char snippet centered on hit) | `get_proxy_http_history_regex` — full body up to the 5000-char cut |
| Inventory endpoints across history | `endpoints` — \~hundreds of tokens (deduplicated `method host path` + count) | no equivalent — page through `get_proxy_http_history` and dedup yourself (\~10 K+) |
| Stats (counts by method / status class / top hosts) | `stats` — \~50 tokens | no equivalent — page through `get_proxy_http_history` and aggregate yourself |
| Mint Collaborator payloads for 10 injection points | `collaborator_payload count=10` — one call, one table, one tag per point | `generate_collaborator_payload` — 10 round-trips, no per-point tagging |
| Poll for OOB callbacks | `collaborator_log` — \~40 tokens (one line per hit, raw capture withheld) | `get_collaborator_interactions` — \~600+ tokens per HTTP hit (full JSON, raw request *and* response, every poll) |

## Tools

| Tool | What it does |
| --- | --- |
| `list_history` | Browse + filter proxy history with field projection. Compact text table by default. |
| `view_request` | View one request by id. Headers + cookies OFF unless asked. Auth values redacted. |
| `view_response` | View one response by id. `body="auto"` truncates &gt;4 KB to `head:20`. |
| `match` | Predicate over one entry — matched? + small evidence snippet. Never the whole body. |
| `endpoints` | Deduplicated method+host+path inventory with hit counts (from proxy history). |
| `sitemap` | Browse Burp's site map (spider + scanner + proxy). `mode="domains"` (default) is just the host inventory; `mode="entries" domain=…` lists endpoints under one host — `dedup=true` (default) groups by method+path with last-seen status/mime + hit count, `dedup=false` flat-lists method/status/path. |
| `collaborator_payload` | Mint Burp Collaborator payloads for OOB testing. `count=N` mints N in one call; `custom_data=` tags them (and appends a per-payload index when `count>1`, so a hit names the injection point that fired). Pro only. |
| `collaborator_log` | Poll Collaborator and list DNS / HTTP / SMTP hits as a compact table. The raw capture is withheld until you ask with `detail=`. Filter with `payload=` and `type=`. Pro only. |
| `stats` | Aggregates: by method, status class, top hosts. |

All tools share these flags where they apply:

- `host=`, `path=` (regex), `method=` (string or array), `status=` (e.g. `4xx,500-503`), `mime=`
- `match=`, `match_in=` (`request.body|headers|all`, `response.body|headers|all`)
- `fields=` from `id,method,status,host,path,len,mime,time`
- `format=text|json`, `order=latest|oldest`, `refresh=true` (force snapshot rebuild)
- `body=full|none|head:N|tail:N|/regex/` (+ `context=N` lines) — `detail=` on `collaborator_log` takes the same specs
- `redact=true` (default) on the view tools

### Out-of-band testing

```
collaborator_payload count=3 custom_data=uid      → uid1, uid2, uid3 planted in 3 sinks
collaborator_log                                   → time / type / client / payload / tag, one line per hit
collaborator_log payload=abc123 detail=auto        → the raw HTTP request the target's backend made
```

`payload=` takes the id, the payload string, a full URL, or an email address — whichever form you pasted into the target. Hits accumulate in-extension, so polling repeatedly never drops one Burp already handed over. The Collaborator client's secret key is persisted, so payloads planted before a Burp restart keep reporting in.

## Build

```bash
git clone https://github.com/an7ln/burp-mcp-lite-ui-filter
cd burp-mcp-lite-ui-filter
./gradlew shadowJar    
```

Requires JDK 21. The build uses Gradle 9.x via the bundled wrapper.

## Install in Burp

1. Run `./gradlew shadowJar`.
2. In Burp: `Extensions → Installed → Add → Java`, pick `build/libs/burp-mcp-lite-0.4.0.jar`.
3. A new top-level **MCP Lite** tab appears. The server auto-starts on `127.0.0.1:9876`.

## Connect an MCP client

The server speaks **two transports on the same port**:

| Path | Transport | Use it from |
| --- | --- | --- |
| `/mcp` | Streamable HTTP (MCP 2024-11-05 / 2025-03-26 / 2025-06-18) | Claude Code (`--transport http`), any modern MCP HTTP client |
| `/sse` | SSE (legacy MCP HTTP transport) | Clients that only speak SSE |

### Claude Code — one-liner install

```bash
claude mcp add --transport http burp-mcp-lite http://127.0.0.1:9876/mcp
```

That's it. Restart your Claude Code session and `burp-mcp-lite` shows up in `/mcp`.

### Other clients

Any MCP client that can configure a remote connection works the same way — point it at `http://127.0.0.1:9876/mcp` (Streamable HTTP) or `http://127.0.0.1:9876/sse` (SSE) as your client requires. The MCP tab inside Burp shows live, copy-pasteable URLs with copy buttons.

## License

MIT — see `LICENSE`
