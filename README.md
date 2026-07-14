# ItemShops

[![Codacy Badge](https://app.codacy.com/project/badge/Grade/17d8e44ec4034a6fa9cfd307e536f810)](https://app.codacy.com/gh/wsg138/ItemShops/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)

ItemShops is a Paper/Leaf item-for-item shop plugin with protected shop containers, searchable market listings, trusted-player access, hopper controls, profit vaults, and reload-safe runtime handling.

## Build

- Java 21
- Maven
- Paper/Leaf API target for Minecraft 1.21.x

Build with:

```powershell
mvn -q -DskipTests package
```

Build output under `target/` is a local artifact. Do not commit plugin JARs.

## Website Market synchronization

ItemShops 1.8.0 includes an optional public-safe exporter for the Enthusia website Market API. Its independent configuration is installed as `plugins/ItemShops/website-sync.yml`. Missing fields are merged from the JAR on startup and the prior file is copied to `plugins/ItemShops/backups/` before a migration. Existing endpoint, timing, enablement, and secret values are preserved.

Set the shared credential from the server console (or an in-game administrator) without placing it in `config.yml`:

```text
/shopmarket sync secret <value>
```

The command confirms only that a secret was saved. Status and logs never display it. Clear it and stop outbound synchronization with:

```text
/shopmarket sync clear-secret confirm
```

Operational commands require `itemshops.market.sync` (operator by default):

- `/shopmarket sync status` reports safe state, revisions, and pending counts.
- `/shopmarket sync test` performs an authenticated, non-mutating transport probe.
- `/shopmarket sync full` requests a complete 71-stall reconciliation.
- `/shopmarket sync enable` and `/shopmarket sync disable` persist the desired state. Disabling preserves the outbox.
- `/shopmarket sync retry` retries the latest pending state for each stall.

Cloudflare, DNS, TLS, authentication, provider, or outbox failures do not disable shops, purchases, restocking, hoppers, vaults, guild shops, or local market regions. HTTP runs on bounded background workers after immutable state is captured on the server thread. Shutdown uses a bounded worker wait.

Full synchronization is deliberately refused unless a supported runtime provider proves authoritative stall identity, sold/available state, player or guild ownership, public members, and rent expiry. The exporter does not bundle or add a dependency on AdvancedRegionMarket, ARM-Guilds-Bridge, WorldGuard, LumaGuilds, or EnthusiaMarketMapper; ItemShops' pre-existing optional guild integrations remain unchanged. Before installation, supply and validate a provider compatible with the deployed AdvancedRegionMarket and ARM-Guilds-Bridge versions. Canonical coordinate assignment rejects overlaps; the approved layout currently gives `stall60` and `stall62` identical bounds, so those stalls also require authoritative region identity.

### Public data boundary

The exporter permits canonical stall identity and public location, public owner identity/name, public member names, rent timestamps, public shop sign coordinates, public transaction items, and live stock/trade counts. It never publishes container coordinates, trusted editor UUIDs, permissions, staff data, database identifiers or paths, raw `ItemStack` data, book page text, arbitrary NBT/components/PDC data, secrets, or configuration.

The canonical mapping is packaged as small derived data and does not require EnthusiaMarketMapper. Future changes must preserve the signing canonicalization, exact-body authentication, main-thread Bukkit capture boundary, strict API schema, latest-state outbox semantics, stable integer shop IDs, secret redaction, and private-field exclusions.

The deployed Worker's strict schema is the serialization authority. It currently has no `available`, goat-horn, dyed-color, firework, banner-pattern, or public-variant metadata fields even though the broader snapshot design describes them. ItemShops therefore does not emit those keys; adding them before the Worker contract changes would make authenticated writes fail schema validation.
