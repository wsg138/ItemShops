package dev.enthusia.itemshops.manager;

import dev.enthusia.itemshops.ItemShopsPlugin;
import dev.enthusia.itemshops.data.ShopStorage;
import dev.enthusia.itemshops.model.Shop;
import dev.enthusia.itemshops.util.ItemUtils;
import dev.enthusia.itemshops.util.Pos;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public final class ShopManager {

    public enum RemovalReason {
        OWNER_BROKE_CONTAINER, EXPLOSION, INVALID_MISSING_CONTAINER, ADMIN_COMMAND
    }

    private final ItemShopsPlugin plugin;
    private final ShopStorage storage;

    
    private final Map<Pos, List<Shop>> byContainer = new ConcurrentHashMap<>();

    
    private final Map<Pos, Shop> bySign = new ConcurrentHashMap<>();

    private final Map<UUID, Integer> ownedCounts = new ConcurrentHashMap<>();

    
    private final ConcurrentLinkedQueue<Pos> refreshQueue = new ConcurrentLinkedQueue<>();
    private final Set<Pos> queued = ConcurrentHashMap.newKeySet();
    private int batchPerRun = 200;
    private long runDelayTicks = 1L;
    private org.bukkit.scheduler.BukkitTask drainTask;

    private final Map<UUID, String> ownerNameCache = new ConcurrentHashMap<>();

    public ShopManager(ItemShopsPlugin plugin, ShopStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    
    public void startSignUpdateScheduler() {
        this.batchPerRun = Math.max(1, plugin.getConfig().getInt("sign-update.batch-per-run", 200));
        this.runDelayTicks = Math.max(1L, plugin.getConfig().getLong("sign-update.run-delay-ticks", 1L));
    }
    public void stopSignUpdateScheduler() {
        if (drainTask != null) drainTask.cancel();
        drainTask = null; refreshQueue.clear(); queued.clear();
    }
    public void requestSignRefreshForContainer(Pos container) {
        if (container != null && queued.add(container)) {
            refreshQueue.add(container);
            scheduleDrainIfNeeded();
        }
    }
    public void requestSignRefresh(Shop s) { if (s != null) requestSignRefreshForContainer(s.container()); }
    private void scheduleDrainIfNeeded() {
        if (drainTask == null) {
            drainTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::drainOnce, runDelayTicks);
        }
    }
    private void drainOnce() {
        int processed = 0;
        while (processed < batchPerRun) {
            Pos p = refreshQueue.poll();
            if (p == null) break;
            queued.remove(p);
            List<Shop> shops = byContainer.get(p);
            if (shops != null) {
                for (Shop s : shops) updateSign(s);
            }
            processed++;
        }
        if (refreshQueue.isEmpty()) drainTask = null;
        else drainTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::drainOnce, runDelayTicks);
    }

    
    public int size(){
        int count = 0;
        for (List<Shop> shops : byContainer.values()) count += shops.size();
        return count;
    }
    public Collection<Shop> all(){
        List<Shop> out = new ArrayList<>();
        for (List<Shop> shops : byContainer.values()) out.addAll(shops);
        return out;
    }

    public void clearAll() {
        byContainer.clear();
        bySign.clear();
        ownedCounts.clear();
        ownerNameCache.clear();
        refreshQueue.clear();
        queued.clear();
    }

    
    public Shop getBySign(Pos signPos){
        return bySign.get(signPos);
    }
    public List<Shop> byContainer(Pos container){ return shopsOn(container); }

    
    public List<Shop> shopsOn(Pos uni) {
        List<Shop> list = byContainer.get(uni);
        if (list == null || list.isEmpty()) return List.of();
        return new ArrayList<>(list);
    }

    public List<Shop> ownedBy(UUID owner) {
        return all().stream().filter(x -> x.owner().equals(owner)).toList();
    }

    public boolean isAllowedContainer(Material m) {
        return plugin.getConfig().getStringList("allowed-containers").contains(m.name());
    }

    public boolean canCreate(Player p){
        if (p.hasPermission("itemshops.maxshops.bypass")) return true;
        int max = plugin.getConfig().getInt("max-shops-per-player", 64);
        int got = ownedCounts.getOrDefault(p.getUniqueId(), 0);
        return got < max;
    }

    public void incOwner(UUID u){ ownedCounts.merge(u,1,Integer::sum); }
    public void decOwner(UUID u){ ownedCounts.merge(u,-1,Integer::sum); }

    public void put(Shop s){
        List<Shop> list = byContainer.computeIfAbsent(s.container(), k -> new ArrayList<>());
        if (!list.contains(s)) list.add(s);

        Shop prev = bySign.put(s.sign(), s);
        if (prev != null && prev != s) {
            removeFromContainer(prev);
            decOwner(prev.owner());
        }

        incOwner(s.owner());
        cacheOwnerName(s.owner());
        updateSign(s);
        storage.saveAsync(this);
    }

    public void relinkSign(Pos oldSign, Pos newSign, Shop s) {
        if (oldSign != null) bySign.remove(oldSign);
        bySign.put(newSign, s);
        s.setSign(newSign);
        requestSignRefresh(s);
        storage.saveAsync(this);
    }

    public void deleteShop(Shop s) { deleteShop(s, RemovalReason.ADMIN_COMMAND, null); }
    public void deleteShop(Shop s, RemovalReason reason, UUID actor) {
        if (s == null) return;
        if (!removeFromContainer(s)) return;
        bySign.remove(s.sign());
        decOwner(s.owner());
        clearSign(s);
        storage.saveAsync(this);
        if (plugin.getConfig().getBoolean("debug.removals", true)) {
            plugin.getLogger().info(String.format(Locale.ROOT,
                    "[ItemShops] Removed shop at %s reason=%s actor=%s",
                    s.container(), reason, actor));
        }
    }

    
    public boolean isValid(Shop s) {
        var cl = s.container().toLocation();
        if (cl == null) return false;
        return cl.getBlock().getState() instanceof Container; 
    }

    
    public int pruneInvalidShops() {
        int removed = 0;
        for (Shop s : new ArrayList<>(all())) {
            if (!isValid(s)) { deleteShop(s, RemovalReason.INVALID_MISSING_CONTAINER, null); removed++; }
        }
        return removed;
    }

    
    public void clearSign(Shop s) {
        var l = s.sign().toLocation(); if (l == null) return;
        var b = l.getBlock(); if (!(b.getState() instanceof Sign sign)) return;
        var side = sign.getSide(org.bukkit.block.sign.Side.FRONT);
        boolean changed = false;
        for (int i=0;i<4;i++) if (!Objects.equals(side.getLine(i), "")) { side.setLine(i,""); changed = true; }
        if (changed) sign.update();
    }

    public void updateSign(Shop s){
        var l = s.sign().toLocation();
        if (l == null) return;
        Block b = l.getBlock();
        if (!(b.getState() instanceof Sign sign)) return; 

        var cfg = plugin.getConfig();
        String headerRaw = cfg.getString("sign.header", "[Shop]");
        String colorIn  = cfg.getString("sign.instock-color", "&a");
        String colorOut = cfg.getString("sign.outstock-color", "&c");

        boolean inStock = false;
        var cl = s.container().toLocation();
        if (cl != null && cl.getBlock().getState() instanceof Container cont) {
            inStock = hasAtLeast(cont.getInventory(), s.sell(), s.sell().getAmount());
        }
        String header = ItemUtils.colored((inStock ? colorIn : colorOut) + headerRaw);

        String ownerName = ownerName(s.owner());
        String sellName = ItemUtils.niceName(s.sell());
        String costName = ItemUtils.niceName(s.cost());

        List<String> lines = cfg.getStringList("sign.format-lines");
        String[] out = new String[4];
        for (int i=0;i<Math.min(4, lines.size());i++){
            String ln = lines.get(i)
                    .replace("{header}", header)
                    .replace("{owner}", ownerName)
                    .replace("{sell_amt}", String.valueOf(s.sell().getAmount()))
                    .replace("{sell_name}", sellName)
                    .replace("{cost_amt}", String.valueOf(s.cost().getAmount()))
                    .replace("{cost_name}", costName);
            out[i] = ItemUtils.colored(ln);
        }

        var side = sign.getSide(org.bukkit.block.sign.Side.FRONT);
        boolean changed = false;
        for (int i=0;i<4;i++) {
            String nv = out[i] == null ? "" : out[i];
            if (!Objects.equals(side.getLine(i), nv)) { side.setLine(i, nv); changed = true; }
        }
        if (changed) sign.update();
    }

    private boolean hasAtLeast(Inventory inv, ItemStack template, int needed) {
        if (inv == null || template == null || needed <= 0) return false;
        int remaining = needed;
        for (ItemStack cur : inv.getStorageContents()) {
            if (cur == null || cur.getType().isAir()) continue;
            if (cur.isSimilar(template)) {
                remaining -= cur.getAmount();
                if (remaining <= 0) return true;
            }
        }
        return false;
    }

    private void cacheOwnerName(UUID u) {
        if (ownerNameCache.containsKey(u)) return;
        Player online = Bukkit.getPlayer(u);
        if (online != null) { ownerNameCache.put(u, online.getName()); return; }
        OfflinePlayer op = Bukkit.getOfflinePlayer(u);
        ownerNameCache.put(u, op != null && op.getName()!=null ? op.getName() : "Unknown");
    }
    private String ownerName(UUID u) {
        String n = ownerNameCache.get(u);
        if (n != null) return n;
        cacheOwnerName(u);
        return ownerNameCache.getOrDefault(u, "Unknown");
    }

    
    public boolean isShopSign(Block b) {
        if (b == null) return false;
        return getBySign(Pos.of(b.getLocation())) != null;
    }

    public Block findAttachedContainer(Sign sign) {
        Block signBlock = sign.getBlock();
        if (sign.getBlockData() instanceof WallSign wall) {
            BlockFace attachedTo = wall.getFacing().getOppositeFace();
            Block possible = signBlock.getRelative(attachedTo);
            if (isValidContainerBlock(possible)) return possible;
        }
        Block below = signBlock.getRelative(BlockFace.DOWN);
        if (isValidContainerBlock(below)) return below;
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block nb = signBlock.getRelative(face);
            if (isValidContainerBlock(nb)) return nb;
        }
        return null;
    }

    private boolean isValidContainerBlock(Block b){
        if (b == null) return false;
        if (!(b.getState() instanceof Container)) return false;
        return isAllowedContainer(b.getType());
    }

    
    public Pos unifyContainerPos(Block containerBlock) {
        if (containerBlock == null || !(containerBlock.getState() instanceof Container)) {
            return Pos.of(containerBlock.getLocation());
        }
        if (containerBlock.getState() instanceof org.bukkit.block.Chest chest) {
            if (chest.getInventory() instanceof DoubleChestInventory dci) {
                var left = ((Container) dci.getLeftSide().getHolder()).getBlock().getLocation();
                var right = ((Container) dci.getRightSide().getHolder()).getBlock().getLocation();
                Pos pl = Pos.of(left), pr = Pos.of(right);
                int cmp = pl.world.compareTo(pr.world);
                if (cmp != 0) return cmp < 0 ? pl : pr;
                if (pl.x != pr.x) return pl.x < pr.x ? pl : pr;
                if (pl.y != pr.y) return pl.y < pr.y ? pl : pr;
                return pl.z <= pr.z ? pl : pr;
            }
        }
        return Pos.of(containerBlock.getLocation());
    }

    public Inventory getLinkedInventory(Block containerBlock) {
        if (!(containerBlock.getState() instanceof Container c)) return null;
        return c.getInventory();
    }

    
    public List<Shop> search(String query, String mode, int limit){
        Material mat = ItemUtils.matchMaterial(query);

        return all().stream().filter(s -> {
            if (!s.isSearchEnabled()) return false;

            boolean inSell = false, inBuy = false;
            if (mat != null) {
                
                inSell = s.sell().getType() == mat || ItemUtils.shulkerContains(s.sell(), mat);
                inBuy  = s.cost().getType() == mat || ItemUtils.shulkerContains(s.cost(), mat);
            }
            switch (mode.toLowerCase(Locale.ROOT)) {
                case "sell": return inSell;
                case "buy" : return inBuy;
                default    : return inSell || inBuy;
            }
        }).limit(limit).collect(Collectors.toList());
    }

    public int computeTradesAvailable(Shop shop, Block contBlock) {
        if (shop == null || contBlock == null || !(contBlock.getState() instanceof Container cont)) return 0;
        int stock = ItemUtils.countSimilar(cont.getInventory(), shop.sell());
        return stock / Math.max(1, shop.sell().getAmount());
    }

    
    public void indexExisting(Shop s) {
        byContainer.computeIfAbsent(s.container(), k -> new ArrayList<>()).add(s);
        bySign.put(s.sign(), s);
        incOwner(s.owner());
    }

    






    public boolean isInMarket(Pos container){
        return plugin.market().isInMarket(container);
    }

    public int outsideSpacingRadius(){
        return Math.max(0, plugin.getConfig().getInt("market.outside-spacing-radius", 2));
    }

    
    public Shop findAnyShopWithin(Pos center, int radius){
        if (radius <= 0) return null;
        for (Shop s : all()) {
            if (!Objects.equals(s.container().world, center.world)) continue;
            int dx = Math.abs(s.container().x - center.x);
            int dy = Math.abs(s.container().y - center.y);
            int dz = Math.abs(s.container().z - center.z);
            int cheb = Math.max(dx, Math.max(dy, dz));
            if (cheb <= radius) return s;
        }
        return null;
    }

    




    public String canCreateHere(Pos containerPos){
        
        if (!isInMarket(containerPos)) {
            int r = outsideSpacingRadius();
            if (r > 0) {
                Shop nearby = findAnyShopWithin(containerPos, r);
                if (nearby != null) {
                    
                    if (!nearby.container().equals(containerPos)) {
                        return "errors.spacing-outside";
                    }
                }
            }
        }
        return null;
    }

    public void requestSave() { storage.saveAsync(this); }

    private boolean removeFromContainer(Shop s) {
        List<Shop> list = byContainer.get(s.container());
        if (list == null) return false;
        boolean removed = list.remove(s);
        if (list.isEmpty()) byContainer.remove(s.container());
        return removed;
    }
}
