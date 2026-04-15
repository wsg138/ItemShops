package dev.enthusia.itemshops.config;

import dev.enthusia.itemshops.ItemShopsPlugin;
import org.bukkit.Material;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public final class ItemShopsConfig {
    private final ItemShopsPlugin plugin;

    private int maxShopsPerPlayer;
    private int maxShopsPerContainer;
    private boolean oneOwnerPerContainer;
    private int outsideSpacingRadius;
    private Set<Material> allowedContainers = Collections.emptySet();
    private int signUpdateBatchPerRun;
    private long signUpdateRunDelayTicks;
    private String storageFileName;
    private boolean defaultHopperAllowIn;
    private boolean defaultHopperAllowOut;

    public ItemShopsConfig(ItemShopsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        maxShopsPerPlayer = Math.max(1, plugin.getConfig().getInt("max-shops-per-player", 64));
        maxShopsPerContainer = Math.max(1, plugin.getConfig().getInt("max-shops-per-container", 2));
        oneOwnerPerContainer = plugin.getConfig().getBoolean("one-owner-per-container", true);
        outsideSpacingRadius = Math.max(0, plugin.getConfig().getInt("market.outside-spacing-radius", 1));
        storageFileName = plugin.getConfig().getString("storage.file", "shops.yml");
        defaultHopperAllowIn = plugin.getConfig().getBoolean("hoppers.default-allow-in", false);
        defaultHopperAllowOut = plugin.getConfig().getBoolean("hoppers.default-allow-out", false);

        // Support both the old broken nested path and the current runtime path.
        signUpdateBatchPerRun = Math.max(
                1,
                plugin.getConfig().getInt(
                        "sign-update.batch-per-run",
                        plugin.getConfig().getInt("safety.sign-update.max-per-tick", 200)
                )
        );
        signUpdateRunDelayTicks = Math.max(
                1L,
                plugin.getConfig().getLong(
                        "sign-update.run-delay-ticks",
                        plugin.getConfig().getLong("safety.sign-update.period-ticks", 1L)
                )
        );

        EnumSet<Material> containers = EnumSet.noneOf(Material.class);
        for (String raw : plugin.getConfig().getStringList("allowed-containers")) {
            if (raw == null || raw.isBlank()) continue;
            try {
                containers.add(Material.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        allowedContainers = Collections.unmodifiableSet(containers);
    }

    public int maxShopsPerPlayer() {
        return maxShopsPerPlayer;
    }

    public int maxShopsPerContainer() {
        return maxShopsPerContainer;
    }

    public boolean oneOwnerPerContainer() {
        return oneOwnerPerContainer;
    }

    public int outsideSpacingRadius() {
        return outsideSpacingRadius;
    }

    public Set<Material> allowedContainers() {
        return allowedContainers;
    }

    public int signUpdateBatchPerRun() {
        return signUpdateBatchPerRun;
    }

    public long signUpdateRunDelayTicks() {
        return signUpdateRunDelayTicks;
    }

    public String storageFileName() {
        return storageFileName;
    }

    public boolean defaultHopperAllowIn() {
        return defaultHopperAllowIn;
    }

    public boolean defaultHopperAllowOut() {
        return defaultHopperAllowOut;
    }
}
