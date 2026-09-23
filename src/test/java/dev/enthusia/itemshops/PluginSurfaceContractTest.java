package dev.enthusia.itemshops;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PluginSurfaceContractTest {
    private static final Set<String> EXPECTED_COMMANDS = Set.of(
            "itemshops",
            "shopvault",
            "shopmarket",
            "shophelp",
            "store"
    );

    private static final Set<String> DEFAULT_TRUE = Set.of(
            "itemshops.breakdelete",
            "itemshops.use",
            "itemshops.help",
            "itemshops.vault"
    );

    private static final Set<String> DEFAULT_OP = Set.of(
            "itemshops.admin",
            "itemshops.break.others",
            "itemshops.maxshops.bypass",
            "itemshops.open.others",
            "itemshops.market"
    );

    @Test
    void descriptorKeepsReviewedIdentityAndOptionalDependencies() {
        YamlConfiguration plugin = descriptor();

        assertEquals("dev.enthusia.itemshops.ItemShopsPlugin", plugin.getString("main"));
        assertEquals("1.21", plugin.getString("api-version"));
        assertEquals(
                Set.of("ARM-Guilds-Bridge", "LumaGuilds", "WorldGuard", "Vault", "Plan"),
                Set.copyOf(plugin.getStringList("softdepend"))
        );
    }

    @Test
    void commandSurfaceAliasesAndOuterPermissionBoundaryStayExplicit() {
        ConfigurationSection commands = descriptor().getConfigurationSection("commands");
        assertNotNull(commands);
        assertEquals(EXPECTED_COMMANDS, commands.getKeys(false));

        assertEquals(Set.of("shops", "shop"), Set.copyOf(commands.getStringList("itemshops.aliases")));
        assertEquals(Set.of("vault", "sv"), Set.copyOf(commands.getStringList("shopvault.aliases")));
        assertEquals(Set.of("shoptutorial", "shopguide", "sht"), Set.copyOf(commands.getStringList("shophelp.aliases")));
        assertEquals(Set.of("shopstore", "tebex"), Set.copyOf(commands.getStringList("store.aliases")));

        assertNull(commands.get("itemshops.permission"));
        assertNull(commands.get("store.permission"));
        assertEquals("itemshops.vault", commands.getString("shopvault.permission"));
        assertEquals("itemshops.market", commands.getString("shopmarket.permission"));
        assertEquals("itemshops.help", commands.getString("shophelp.permission"));
    }

    @Test
    void permissionDefaultsMatchReviewedAuthoritySurface() {
        ConfigurationSection permissions = descriptor().getConfigurationSection("permissions");
        assertNotNull(permissions);

        Set<String> expected = new HashSet<>();
        expected.addAll(DEFAULT_TRUE);
        expected.addAll(DEFAULT_OP);
        assertEquals(expected, declaredPermissions(permissions));

        DEFAULT_TRUE.forEach(permission ->
                assertEquals(Boolean.TRUE, permissions.get(permission + ".default"), permission));
        DEFAULT_OP.forEach(permission ->
                assertEquals("op", permissions.get(permission + ".default"), permission));
    }

    private static Set<String> declaredPermissions(ConfigurationSection permissions) {
        return permissions.getKeys(true).stream()
                .filter(key -> key.endsWith(".default"))
                .map(key -> key.substring(0, key.length() - ".default".length()))
                .collect(Collectors.toSet());
    }

    private static YamlConfiguration descriptor() {
        return YamlConfiguration.loadConfiguration(new File("src/main/resources/plugin.yml"));
    }
}
