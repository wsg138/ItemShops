package dev.enthusia.itemshops.region;

import dev.enthusia.itemshops.ItemShopsPlugin;
import dev.enthusia.itemshops.util.Pos;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public final class MarketRegionManager {

    public static final class Region {
        public final String world;
        public final int minX, minY, minZ, maxX, maxY, maxZ;
        public Region(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.world = world;
            this.minX = Math.min(minX, maxX);
            this.minY = Math.min(minY, maxY);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxY = Math.max(minY, maxY);
            this.maxZ = Math.max(minZ, maxZ);
        }
        public boolean contains(Pos p) {
            if (!p.world.equals(world)) return false;
            return p.x >= minX && p.x <= maxX
                    && p.y >= minY && p.y <= maxY
                    && p.z >= minZ && p.z <= maxZ;
        }
    }

    private final ItemShopsPlugin plugin;
    private final List<Region> regions = new ArrayList<>();

    public MarketRegionManager(ItemShopsPlugin plugin){
        this.plugin = plugin;
        loadFromConfig();
    }

    public void reload() {
        loadFromConfig();
    }

    public boolean isInMarket(Pos p) {
        for (Region r : regions) if (r.contains(p)) return true;
        return false;
    }

    public List<Region> regions(){ return new ArrayList<>(regions); }

    public void clear() {
        regions.clear();
        saveToConfig();
    }

    public void addCenteredCube(Location center, int radius) {
        World w = center.getWorld();
        if (w == null) return;
        addRegion(
                w.getName(),
                center.getBlockX()-radius, center.getBlockY()-radius, center.getBlockZ()-radius,
                center.getBlockX()+radius, center.getBlockY()+radius, center.getBlockZ()+radius
        );
    }

    public void addRegion(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        regions.add(new Region(world, minX, minY, minZ, maxX, maxY, maxZ));
        saveToConfig();
    }

    private void loadFromConfig() {
        regions.clear();
        var list = plugin.getConfig().getMapList("market.regions");
        for (var m : list) {
            try {
                String world = String.valueOf(m.get("world"));
                int minX = (int) m.get("minX");
                int minY = (int) m.get("minY");
                int minZ = (int) m.get("minZ");
                int maxX = (int) m.get("maxX");
                int maxY = (int) m.get("maxY");
                int maxZ = (int) m.get("maxZ");
                regions.add(new Region(world, minX, minY, minZ, maxX, maxY, maxZ));
            } catch (Exception ignored) {}
        }
    }

    private void saveToConfig() {
        List<java.util.Map<String,Object>> out = new ArrayList<>();
        for (Region r : regions) {
            var m = new java.util.HashMap<String,Object>();
            m.put("world", r.world);
            m.put("minX", r.minX); m.put("minY", r.minY); m.put("minZ", r.minZ);
            m.put("maxX", r.maxX); m.put("maxY", r.maxY); m.put("maxZ", r.maxZ);
            out.add(m);
        }
        plugin.getConfig().set("market.regions", out);
        plugin.saveConfig();
    }
}
