package dev.enthusia.itemshops.data;

import dev.enthusia.itemshops.ItemShopsPlugin;
import dev.enthusia.itemshops.config.ItemShopsConfig;
import dev.enthusia.itemshops.util.Pos;
import dev.enthusia.itemshops.vault.VaultManager;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class SqliteItemShopsDatabase {
    private final ItemShopsPlugin plugin;
    private final File file;
    private final String jdbcUrl;

    public SqliteItemShopsDatabase(ItemShopsPlugin plugin, ItemShopsConfig config) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), config.sqliteFileName()).getAbsoluteFile();
        this.jdbcUrl = "jdbc:sqlite:" + file.getAbsolutePath();
        initSchema();
    }

    public File file() {
        return file;
    }

    public synchronized void initSchema() {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (Connection connection = open();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("CREATE TABLE IF NOT EXISTS storage_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS shops (
                        id TEXT PRIMARY KEY,
                        sign_world TEXT NOT NULL,
                        sign_x INTEGER NOT NULL,
                        sign_y INTEGER NOT NULL,
                        sign_z INTEGER NOT NULL,
                        container_world TEXT NOT NULL,
                        container_x INTEGER NOT NULL,
                        container_y INTEGER NOT NULL,
                        container_z INTEGER NOT NULL,
                        owner TEXT NOT NULL,
                        sell_yaml TEXT NOT NULL,
                        cost_yaml TEXT NOT NULL,
                        trusted TEXT NOT NULL,
                        hopper_allow_in INTEGER NOT NULL,
                        hopper_allow_out INTEGER NOT NULL,
                        search_enabled INTEGER NOT NULL,
                        frozen INTEGER NOT NULL,
                        frozen_until_ms INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS vault_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        owner TEXT NOT NULL,
                        item_yaml TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        expires_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_shops_container ON shops(container_world, container_x, container_y, container_z)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_shops_sign ON shops(sign_world, sign_x, sign_y, sign_z)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_vault_owner ON vault_entries(owner)");
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize ItemShops SQLite storage", exception);
        }
    }

    public synchronized boolean hasMeta(String key) {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM storage_meta WHERE key = ?")) {
            statement.setString(1, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to read storage migration metadata.", exception);
            return false;
        }
    }

    public synchronized boolean shopsEmpty() {
        return tableEmpty(Table.SHOPS);
    }

    public synchronized boolean vaultEmpty() {
        return tableEmpty(Table.VAULT_ENTRIES);
    }

    public synchronized void markMeta(String key, String value) {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO storage_meta (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to write storage migration metadata.", exception);
        }
    }

    public synchronized List<StoredShop> loadShops() {
        List<StoredShop> shops = new ArrayList<>();
        String sql = "SELECT * FROM shops ORDER BY rowid";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                StoredShop shop = readShop(resultSet);
                if (shop != null) {
                    shops.add(shop);
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed loading shops from SQLite.", exception);
        }
        return shops;
    }

    public synchronized boolean saveShops(Collection<StoredShop> shops) {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try (Statement delete = connection.createStatement();
                 PreparedStatement insert = connection.prepareStatement("""
                         INSERT INTO shops (
                             id, sign_world, sign_x, sign_y, sign_z,
                             container_world, container_x, container_y, container_z,
                             owner, sell_yaml, cost_yaml, trusted,
                             hopper_allow_in, hopper_allow_out, search_enabled, frozen, frozen_until_ms
                         ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                         """)) {
                delete.executeUpdate("DELETE FROM shops");
                for (StoredShop shop : shops) {
                    writeShop(insert, shop);
                    insert.addBatch();
                }
                insert.executeBatch();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            plugin.performance().storageSaveFailed.increment();
            plugin.getLogger().log(Level.SEVERE, "Failed saving shops to SQLite.", exception);
            return false;
        }
        return true;
    }

    public synchronized List<VaultManager.VaultEntry> loadVaultEntries() {
        List<VaultManager.VaultEntry> entries = new ArrayList<>();
        String sql = "SELECT owner, item_yaml, amount, expires_at FROM vault_entries ORDER BY id";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                VaultManager.VaultEntry entry = readVaultEntry(resultSet);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed loading vault entries from SQLite.", exception);
        }
        return entries;
    }

    public synchronized void saveVaultEntries(Collection<VaultManager.VaultEntry> entries) {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try (Statement delete = connection.createStatement();
                 PreparedStatement insert = connection.prepareStatement(
                         "INSERT INTO vault_entries (owner, item_yaml, amount, expires_at) VALUES (?, ?, ?, ?)")) {
                delete.executeUpdate("DELETE FROM vault_entries");
                for (VaultManager.VaultEntry entry : entries) {
                    insert.setString(1, entry.owner.toString());
                    insert.setString(2, ItemStackCodec.encode(entry.item));
                    insert.setInt(3, entry.amount);
                    insert.setLong(4, entry.expiresAt);
                    insert.addBatch();
                }
                insert.executeBatch();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed saving vault entries to SQLite.", exception);
        }
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private boolean tableEmpty(Table table) {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(table.emptyCheckSql());
             ResultSet resultSet = statement.executeQuery()) {
            return !resultSet.next();
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed checking SQLite table state for " + table.logName() + ".", exception);
            return false;
        }
    }

    private enum Table {
        SHOPS("shops", "SELECT 1 FROM shops LIMIT 1"),
        VAULT_ENTRIES("vault_entries", "SELECT 1 FROM vault_entries LIMIT 1");

        private final String logName;
        private final String emptyCheckSql;

        Table(String logName, String emptyCheckSql) {
            this.logName = logName;
            this.emptyCheckSql = emptyCheckSql;
        }

        private String logName() {
            return logName;
        }

        private String emptyCheckSql() {
            return emptyCheckSql;
        }
    }

    private StoredShop readShop(ResultSet row) throws SQLException {
        try {
            UUID id = UUID.fromString(row.getString("id"));
            UUID owner = UUID.fromString(row.getString("owner"));
            Pos sign = new Pos(row.getString("sign_world"), row.getInt("sign_x"), row.getInt("sign_y"), row.getInt("sign_z"));
            Pos container = new Pos(row.getString("container_world"), row.getInt("container_x"), row.getInt("container_y"), row.getInt("container_z"));
            ItemStack sell = ItemStackCodec.decode(row.getString("sell_yaml"));
            ItemStack cost = ItemStackCodec.decode(row.getString("cost_yaml"));
            if (sell == null || cost == null) {
                return null;
            }
            return new StoredShop(
                    id,
                    sign,
                    container,
                    owner,
                    sell,
                    cost,
                    parseTrusted(row.getString("trusted")),
                    row.getInt("hopper_allow_in") != 0,
                    row.getInt("hopper_allow_out") != 0,
                    row.getInt("search_enabled") != 0,
                    row.getInt("frozen") != 0,
                    row.getLong("frozen_until_ms")
            );
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Skipped invalid shop row in SQLite storage: " + exception.getMessage());
            return null;
        }
    }

    private void writeShop(PreparedStatement statement, StoredShop shop) throws SQLException {
        statement.setString(1, shop.id().toString());
        statement.setString(2, shop.sign().world);
        statement.setInt(3, shop.sign().x);
        statement.setInt(4, shop.sign().y);
        statement.setInt(5, shop.sign().z);
        statement.setString(6, shop.container().world);
        statement.setInt(7, shop.container().x);
        statement.setInt(8, shop.container().y);
        statement.setInt(9, shop.container().z);
        statement.setString(10, shop.owner().toString());
        statement.setString(11, ItemStackCodec.encode(shop.sell()));
        statement.setString(12, ItemStackCodec.encode(shop.cost()));
        statement.setString(13, shop.trusted().stream().map(UUID::toString).collect(Collectors.joining(",")));
        statement.setInt(14, shop.hopperAllowIn() ? 1 : 0);
        statement.setInt(15, shop.hopperAllowOut() ? 1 : 0);
        statement.setInt(16, shop.searchEnabled() ? 1 : 0);
        statement.setInt(17, shop.frozen() ? 1 : 0);
        statement.setLong(18, shop.frozenUntilMs());
    }

    private VaultManager.VaultEntry readVaultEntry(ResultSet row) throws SQLException {
        try {
            UUID owner = UUID.fromString(row.getString("owner"));
            ItemStack item = ItemStackCodec.decode(row.getString("item_yaml"));
            int amount = row.getInt("amount");
            long expiresAt = row.getLong("expires_at");
            if (item == null || amount <= 0) {
                return null;
            }
            return new VaultManager.VaultEntry(owner, item, amount, expiresAt);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Skipped invalid vault row in SQLite storage: " + exception.getMessage());
            return null;
        }
    }

    private List<UUID> parseTrusted(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<UUID> trusted = new ArrayList<>();
        for (String part : raw.split(",")) {
            if (part.isBlank()) {
                continue;
            }
            try {
                trusted.add(UUID.fromString(part.trim()));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Skipped invalid trusted UUID in SQLite shop row: " + part);
            }
        }
        return trusted;
    }
}
