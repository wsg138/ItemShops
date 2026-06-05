package dev.enthusia.itemshops.analytics.plan;

import com.djrapitops.plan.capability.CapabilityService;
import com.djrapitops.plan.extension.DataExtension;
import com.djrapitops.plan.extension.ExtensionService;
import dev.enthusia.itemshops.ItemShopsPlugin;
import dev.enthusia.itemshops.analytics.ShopAnalyticsStore;

public final class PlanAnalyticsHook implements AnalyticsIntegration {
    private final ItemShopsPlugin plugin;
    private final DataExtension extension;
    private boolean registered;

    public PlanAnalyticsHook(ItemShopsPlugin plugin, ShopAnalyticsStore analyticsStore) {
        this.plugin = plugin;
        this.extension = new PlanItemShopsExtension(plugin, analyticsStore);
    }

    public void hookIntoPlan() {
        if (!hasRequiredCapabilities()) {
            plugin.getLogger().fine("Plan is installed, but required DataExtension capabilities are unavailable.");
            return;
        }
        registerDataExtension();
        CapabilityService.getInstance().registerEnableListener(isPlanEnabled -> {
            if (Boolean.TRUE.equals(isPlanEnabled)) {
                registerDataExtension();
            }
        });
    }

    @Override
    public void shutdown() {
        if (!registered) {
            return;
        }
        try {
            ExtensionService.getInstance().unregister(extension);
        } catch (IllegalStateException ignored) {
            // Plan may already be disabled during server shutdown.
        } catch (RuntimeException t) {
            plugin.getLogger().fine("Failed to unregister ItemShops Plan extension: " + t.getMessage());
        } finally {
            registered = false;
        }
    }

    private boolean hasRequiredCapabilities() {
        CapabilityService capabilities = CapabilityService.getInstance();
        return capabilities.hasCapability("DATA_EXTENSION_VALUES")
                && capabilities.hasCapability("DATA_EXTENSION_TABLES")
                && capabilities.hasCapability("DATA_EXTENSION_BUILDER_API");
    }

    private void registerDataExtension() {
        try {
            if (registered) {
                ExtensionService.getInstance().unregister(extension);
                registered = false;
            }
            ExtensionService.getInstance().register(extension);
            registered = true;
            plugin.getLogger().info("Registered ItemShops Plan analytics integration.");
        } catch (IllegalStateException e) {
            plugin.getLogger().fine("Plan is not ready for ItemShops analytics registration.");
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid ItemShops Plan analytics extension: " + e.getMessage());
        } catch (RuntimeException t) {
            plugin.getLogger().warning("Failed to register ItemShops Plan analytics integration: " + t.getMessage());
        }
    }
}
