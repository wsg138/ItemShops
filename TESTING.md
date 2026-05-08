# ItemShops Testing Checklist

## Status Summary

Current status: ready for structured manual testing on a Paper/Leaf 1.21.x server with Java 21.

The codebase has already had its main release-hardening pass:

- business logic moved out of most commands and GUIs into the service/manager layer
- buy flow centralized through `ShopTradeService`
- sign refreshes batched
- shop storage save/load cleaned up
- recovery behavior hardened so shared-container shops are not relinked incorrectly
- market-region, hopper-toggle, vault-profit, and fork-compatible event/hook behavior preserved

## Known Acceptable Limitations

- The profits vault is still plugin-managed YAML-backed state, not a transactional database. A hard crash at the wrong moment can still lose the newest vault delta.
- Ambiguous sign recovery on a container with two shops is intentionally conservative now. It avoids incorrect auto-repair, but may require manual admin intervention instead of silently guessing.
- There is no automated test suite yet. This release decision should be based on deliberate manual coverage.
- Guild integration is reflective and depends on the external plugin APIs being present and enabled. That path must be tested on a real integration server if you rely on it.

## Recommended Testing / Release Approach

1. Test first on a clean staging server using the intended runtime stack:
   - Leaf/Paper 1.21.x
   - Java 21
   - same plugin set you expect in production, especially guild-related plugins if used
   - any Badger-authored dependent plugins that listen for ItemShops events or call the added integration APIs
2. Use at least 2 normal player accounts and 1 admin account.
3. Run tests once with no guild plugins present and once with the guild stack present if guild shops matter to your server.
4. Run at least one pass with the actual dependent plugins Badger built against this plugin, because API compatibility is present here but real runtime verification still matters.
5. Keep `debug.removals: true` during staging so unexpected cleanup is visible in console.
6. Delete old beta shop data if you want a clean verification pass. This build is intended for fresh-format validation rather than legacy migration coverage.

## Ordered Manual Testing Checklist

### 1. Boot / Basic Startup

- Start the server with only ItemShops installed.
- Confirm the plugin enables cleanly with no stack traces.
- Confirm the startup log reports the number of loaded/pruned shops.
- Confirm `plugin.yml` command registration works:
  - `/itemshops`
  - `/shopvault`
  - `/shopmarket`
  - `/shophelp`
  - `/store`

### 2. Fresh Shop Creation

- Place a chest and a sign in a valid attached configuration.
- Sneak-left-click the sign as a normal player.
- Confirm the create GUI opens.
- Set a normal item-for-item trade such as `5 diamonds for 16 cobblestone`.
- Confirm shop creation succeeds.
- Confirm:
  - the sign text renders correctly
  - owner name appears correctly
  - stock color is correct
  - `/itemshops list` shows the shop
  - `/itemshops edit` shows the shop

### 3. Invalid Creation Cases

- Try to create a shop with no valid attached container.
- Try to create a shop on a disallowed container type.
- Try to exceed `max-shops-per-player`.
- Try to exceed `max-shops-per-container`.
- With `one-owner-per-container: true`, try creating a second shop on the same container as a different player.
- Outside market regions, verify the spacing rule:
  - `X0X` style placement should work
  - touching outside-market containers should fail if spacing config requires a gap
- Inside a market region, verify tightly packed shops can still be created.

### 4. Two Shops On One Container

- Create two shops on one unified container as the same owner.
- Confirm both signs render correctly.
- Confirm both shops are listed and editable.
- Confirm a third shop on the same container is denied by default.
- Break one sign only:
  - confirm only that shop is removed
  - confirm the other sign/shop remains valid
- Recreate the removed sign/shop and confirm the surviving one was not remapped incorrectly.

### 5. Buying Flow

- Stock the container with only enough items for exactly 1 trade.
- Buy 1 trade from another player.
- Confirm:
  - buyer receives the sell item
  - buyer pays the cost item
  - stock decrements correctly
  - sign updates to out-of-stock when empty
  - owner profit appears in `/shopvault`
- Repeat with enough stock for multiple trades.
- Confirm self-purchase is blocked.
- Confirm buying fails cleanly when:
  - buyer lacks payment item
  - buyer lacks inventory space
  - shop stock is insufficient

### 6. Buy Max / Custom Quantity

- Stock enough items for many trades.
- Give buyer partial funds so wallet is the limiting factor.
- Use buy max and confirm the purchased quantity matches the smallest of:
  - container stock
  - buyer funds
  - buyer inventory capacity
- Test custom quantity:
  - valid number
  - larger than max available
  - zero / negative / invalid chat input if your capture flow allows it
- Confirm no dupes and no item loss when custom/max purchase is capped.

### 7. Trusted / Co-Manager Access

- Add a trusted player through GUI/menu.
- Add a trusted player through `/itemshops trust <player> all`.
- Confirm the trusted player can open the edit menu for stock/settings access as intended.
- Confirm the trusted player cannot perform owner-only actions that should remain blocked.
- Remove trust through GUI and through `/itemshops untrust <player> all`.
- Confirm access is revoked immediately.

### 8. Edit GUI / Shop Settings

- As owner, open the edit GUI.
- Toggle:
  - hopper in
  - hopper out
  - search enabled
- Confirm toggles persist after closing/reopening.
- Change trade templates as owner and save.
- Confirm sign updates to new trade values.
- As trusted non-owner, confirm template editing is blocked if intended but stock/settings access still behaves correctly.

### 9. Hopper Behavior

- With hopper input disabled, attempt to pipe stock into the shop container and confirm it is blocked.
- With hopper input enabled, confirm stock can be inserted.
- With hopper output disabled, attempt extraction and confirm it is blocked.
- With hopper output enabled, confirm extraction works.
- For a two-shop shared container:
  - if one shop disables input, confirm input is blocked for the shared container
  - if one shop disables output, confirm output is blocked for the shared container
- After hopper movement, confirm sign stock status refreshes correctly.

### 10. Search / Market Behavior

- Create one shop inside a market region and one outside it.
- Confirm inside-market shop is searchable by default.
- Confirm outside-market shop is hidden by default unless enabled in the edit GUI.
- Toggle search on for the outside-market shop and confirm it appears in search.
- Toggle search off again and confirm it disappears.
- Test `/itemshops search <item>` for:
  - sell mode
  - buy mode
  - any mode
- Test both GUI search results and console output.

### 11. Container Access / Protection

- As a non-owner, try to open a shop container directly and confirm it is blocked.
- As owner, confirm direct container access works.
- As trusted player, confirm access works where intended.
- Try breaking a shop sign as:
  - owner
  - trusted player
  - unrelated player
  - admin
- Try breaking a shop container as:
  - owner
  - unrelated player
  - admin with bypass/break modes
- Confirm deletion/cleanup behavior matches expectations and does not leave orphaned sign/shop state.

### 12. Vault Behavior

- Make several purchases so profits accumulate in the owner vault.
- Open `/shopvault`.
- Redeem:
  - one stack from an entry
  - all of one entry
  - redeem all
- Confirm redeemed items match what buyers paid.
- Confirm vault entries reduce correctly and do not duplicate.
- Fill the player inventory partially and confirm redeem-all only gives what fits.

### 13. Freeze / Admin Controls

- Freeze a single player’s shops from the menu.
- Freeze all shops with `/itemshops freeze all`.
- Confirm purchases are blocked while frozen.
- Unfreeze shops and confirm purchases resume.
- Test temporary freeze durations and verify expiry after waiting or using `/itemshops fix`.
- Test:
  - `/itemshops adminview`
  - `/itemshops info`
  - `/itemshops breakothers`
  - `/itemshops breakdelete`

### 14. Reload / Restart / Shutdown

- Create several shops, including one two-shop container setup.
- Change toggles and trust settings.
- Run `/itemshops reload`.
- Confirm after reload:
  - shops still exist
  - signs still match shop state
  - trust settings persist
  - hopper/search toggles persist
  - market regions still function
- Stop the server cleanly and restart it.
- Confirm all persisted state reloads correctly.
- Make a purchase, then stop the server cleanly shortly afterward.
- Confirm the profit vault and shop state still persist.

### 15. Recovery / Cleanup Edge Cases

- Break a sign while leaving the container.
- Confirm the associated shop is cleaned up correctly.
- Break the container while leaving the sign.
- Confirm the associated shop is removed.
- Trigger explosion damage affecting:
  - only sign
  - only container
  - both
- Confirm cleanup removes the correct shop(s) and does not leave stale entries.
- Force a blank sign state if possible, then interact with it:
  - single-shop container should repair safely
  - two-shop container should not guess the wrong relink

### 16. Integration Checks

- If running without guild plugins:
  - confirm ItemShops still starts cleanly
  - confirm no guild features interfere with normal shops
- If running with Badger-authored dependent plugins that hook into ItemShops:
  - confirm they enable cleanly with this build
  - confirm there are no missing-class, missing-method, or event-registration errors at startup
  - confirm any features that depend on `PreShopTransactionEvent`, `PostShopTransactionEvent`, `ShopCreatedEvent`, `ShopDeletedEvent`, `ShopStockDepletedEvent`, or `GuildShopIntegration` still behave as expected
  - confirm any plugin that modifies transaction pricing through the pre-transaction event still reaches the intended end result in live purchases
- If running with `ARM-Guilds-Bridge` and `LumaGuilds`:
  - confirm startup reports guild integration enabled
  - confirm guild shop recognition works
  - confirm guild permissions for chest access / stock edit / price modification behave as expected
  - confirm physical-currency purchases route income to the guild vault rather than the player vault
- If using WorldGuard only for market workflows, confirm nothing breaks when it is present or absent.

## Edge Cases To Verify

- Barrel shops
- Double chest shops
- Two shops sharing one container with mixed toggle settings
- Buyer inventory nearly full
- Shop stock exactly equal to one trade
- Shop stock exactly equal to buy-max cap
- Trusted player online vs offline owner
- Frozen shop with expired timer after restart
- Search results for shulker-contained items if you rely on that behavior

## Reload / Restart / Shutdown Checks

- Run `/itemshops reload` during active player use, then re-check signs and menus.
- Restart with several pending sign refreshes after stock changes.
- Confirm no repeated sign corruption or missing sign text after restart.
- Confirm there is no obvious save corruption in `shops.yml` or `vault.yml`.

## Performance / Hot-Path Checks

- Test with many shops in one area and perform repeated purchases.
- Test repeated hopper movement through shop containers.
- Test mass sign refresh conditions by restocking multiple shops quickly.
- Watch server timings / MSPT during:
  - heavy buying
  - heavy hopper activity
  - chunk loads containing many shop signs
- Confirm there are no obvious lag spikes from sign updates or chunk recovery.

## Final Release Decision

Release to a wider testing server if:

- all creation, buy, max-buy, custom-buy, trust, edit, search, vault, hopper, reload, and cleanup tests pass
- no dupes, item loss, stale sign mappings, or incorrect shared-container behavior are observed
- guild integration behaves correctly on the real plugin stack if you depend on it
- restart/reload persistence is stable over at least a few repeated cycles

Hold release and fix first if:

- any purchase path duplicates or loses items
- two-shop shared containers relink incorrectly
- signs desync from stock after normal gameplay
- hopper rules do not match per-shop toggles
- reload/restart loses trust/toggle/shop data
