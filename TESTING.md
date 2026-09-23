# ItemShops testing guide

ItemShops is a superseded Enthusia experiment, but its code still has a normal repository-local test suite. Green tests preserve the behavior of this repository; they do **not** mean ItemShops is the current production shop system.

## Test ownership

Handwritten unit/regression tests live in `src/test/java` in this repository. Sentinel Sim may be used for packaged-plugin compatibility evidence, but it is not the storage location for normal ItemShops tests.

## Existing tests

Before this hardening work, the repository already covered:

- `StoredShopTest` — stored-shop snapshots restore important live shop fields;
- `ItemStackCodecTest` — item encoding/decoding and malformed YAML handling;
- `ItemShopsPluginSmokeTest` — MockBukkit startup with the default SQLite storage path.

## Added hardening tests

### `ShopAnalyticsRecordTest`

Covers the deterministic analytics record model:

- negative amounts/trades/costs/prices are clamped to safe non-negative values;
- nullable display/text fields normalize to empty strings;
- player involvement matches owner or actor only;
- serialization round-trips through Bukkit configuration;
- invalid type or non-positive timestamps fail closed;
- malformed UUID fields are ignored without discarding an otherwise valid record.

### `ShopTransactionEventTest`

Covers transaction event contracts:

- pre-transaction events begin unmodified and uncancelled;
- modified price/reason and cancellation state are independent;
- post-transaction events expose the expected immutable transaction snapshot;
- both event types return their canonical static Bukkit handler lists.

### `PluginSurfaceContractTest`

Freezes the reviewed plugin metadata surface:

- main class and API version;
- optional dependency set;
- command set and aliases;
- intentionally unpermissioned outer commands;
- command permission references;
- complete permission set and reviewed `true`/`op` defaults.

If this contract fails, first verify that the command/dependency/authority change is intentional. Do not merely edit expected values to make CI green.

## Running tests

Use Java 21 and Maven.

Run everything:

```bash
mvn -B test
```

Run the new hardening tests only:

```bash
mvn -B -Dtest=ShopAnalyticsRecordTest,ShopTransactionEventTest,PluginSurfaceContractTest test
```

Run the full Maven verification/package path:

```bash
mvn -B clean verify
```

## Results

Surefire writes local results to:

- `target/surefire-reports/*.txt`
- `target/surefire-reports/*.xml`

The hardening PR also adds a PR-only GitHub Actions workflow that runs `mvn -B clean verify` on Java 21 and uploads Surefire reports when available. Final evidence should always be tied to the exact PR-head SHA.

## Failure triage

### Analytics-model failure

Check normalization, UUID parsing, serialization compatibility, timestamp validity, and player ownership/actor semantics before changing expectations.

### Transaction-event failure

Check whether the pre-event cancellation/price-mutation contract or post-event transaction snapshot intentionally changed. These are extension-facing APIs and should not be changed casually.

### Plugin-surface failure

Treat command, alias, dependency, and permission changes as public/security surface changes. Verify command behavior and authority boundaries before updating the contract.

### MockBukkit/plugin smoke failure

Separate a MockBukkit compatibility limitation from a real plugin startup regression. Do not weaken production metadata solely to satisfy MockBukkit.

### Packaging/dependency failure

A unit-test pass does not override a package/build failure. Inspect Maven dependency resolution, shaded SQLite runtime behavior, provided Paper/Plan APIs, and JAR contents separately.

## Known coverage gaps

The current suite is still small relative to the repository. Important areas that would need additional focused tests if this experiment is revived include:

- `ShopAnalyticsStore` retention/debounced persistence and query windows;
- command subtrees and tab completion;
- trust/untrust and ownership authorization;
- market-region rules;
- trade stock/money/item atomicity and rollback;
- Vault/WorldGuard/guild-provider present/missing behavior;
- shop vault persistence;
- Plan analytics integration;
- GUI click flows and stale inventory views;
- real Paper lifecycle/restart behavior.

Do not interpret the current suite as exhaustive coverage of those areas.

## Adding tests

For a meaningful change:

1. add focused behavior tests close to the owning class;
2. include negative/unauthorized/malformed paths;
3. cover reload/restart/idempotency for persisted state;
4. cover provider-present and provider-missing behavior for integrations;
5. update `PluginSurfaceContractTest` only when the reviewed public/security surface intentionally changes;
6. run focused tests and then `mvn -B clean verify`;
7. inspect exact-head CI evidence before merge.

## Sentinel / real Paper boundary

Use repository tests for deterministic models, commands, storage rules, event contracts, and regression logic. Use Sentinel or a real Paper environment for packaged JAR loading, real scheduler behavior, cross-plugin dependencies, server restart behavior, and production-stack compatibility. These are separate evidence layers and should be reported separately.
