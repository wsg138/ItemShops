# Enthusia status

ItemShops is an Enthusia-developed shop experiment/prototype, but it is **not the canonical current Enthusia market implementation** and should not be used as a source for public server-feature/wiki documentation.

The current production server snapshot contains an old/empty `plugins/ItemShops/` data directory (`shops.yml`, `vault.yml` and `analytics.yml` are empty), but the active/canonical market documentation and populated production configuration are under **EnthusiaMarket**. A leftover plugin-data folder does not by itself prove that ItemShops is an active player system.

## What this repository implemented

For historical/development reference, ItemShops contains an item-for-item shop design with features such as:

- protected shop containers,
- searchable listings,
- trusted-player access,
- hopper controls,
- profit vaults,
- reload-safe runtime handling.

Those features must not be mixed into the public EnthusiaMarket feature set unless the current market implementation independently provides them.

## Wiki / documentation rule

Future automation should classify this repository as **legacy/superseded development**:

- do not list ItemShops as a current Enthusia player feature,
- do not combine its mechanics with EnthusiaMarket,
- use `wsg138/EnthusiaMarket` + current `enthusia-server-state` config for the live market,
- keep this repo only as historical implementation context unless it is deliberately revived and redeployed later.