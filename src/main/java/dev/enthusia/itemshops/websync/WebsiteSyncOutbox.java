package dev.enthusia.itemshops.websync;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class WebsiteSyncOutbox implements AutoCloseable {
    private final Connection connection;

    public WebsiteSyncOutbox(File file) throws SQLException {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        initialize();
    }

    private void initialize() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("CREATE TABLE IF NOT EXISTS sync_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS stall_revisions (stall_id TEXT PRIMARY KEY, revision INTEGER NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS pending_stalls (stall_id TEXT PRIMARY KEY, revision INTEGER NOT NULL, payload TEXT NOT NULL, public_hash TEXT NOT NULL, event_id TEXT NOT NULL, attempt_count INTEGER NOT NULL, next_retry_at INTEGER NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS acknowledged_stalls (stall_id TEXT PRIMARY KEY, revision INTEGER NOT NULL, public_hash TEXT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS public_shop_ids (shop_uuid TEXT PRIMARY KEY, public_id INTEGER NOT NULL UNIQUE)");
        }
        if (meta("server_epoch") == null) setMeta("server_epoch", UUID.randomUUID().toString());
        if (meta("snapshot_revision") == null) setMeta("snapshot_revision", "0");
    }

    public synchronized String serverEpoch() throws SQLException { return meta("server_epoch"); }
    public synchronized long snapshotRevision() throws SQLException { return Long.parseLong(meta("snapshot_revision")); }

    public synchronized long nextSnapshotRevision() throws SQLException {
        long next = snapshotRevision() + 1;
        setMeta("snapshot_revision", Long.toString(next));
        return next;
    }

    public synchronized long nextStallRevision(String stallId) throws SQLException {
        connection.setAutoCommit(false);
        try {
            long current = 0;
            try (PreparedStatement query = connection.prepareStatement("SELECT revision FROM stall_revisions WHERE stall_id=?")) {
                query.setString(1, stallId);
                try (ResultSet rows = query.executeQuery()) { if (rows.next()) current = rows.getLong(1); }
            }
            long next = current + 1;
            try (PreparedStatement upsert = connection.prepareStatement("INSERT INTO stall_revisions(stall_id,revision) VALUES(?,?) ON CONFLICT(stall_id) DO UPDATE SET revision=excluded.revision")) {
                upsert.setString(1, stallId); upsert.setLong(2, next); upsert.executeUpdate();
            }
            connection.commit();
            return next;
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public synchronized void markFullAcknowledged(long revision, List<MarketDtos.Stall> stalls) throws SQLException {
        connection.setAutoCommit(false);
        try {
            for (MarketDtos.Stall stall : stalls) {
                String hash = MarketRequestSigner.sha256(MarketJson.encodeBytes(stall));
                try (PreparedStatement rev = connection.prepareStatement("INSERT INTO stall_revisions(stall_id,revision) VALUES(?,?) ON CONFLICT(stall_id) DO UPDATE SET revision=MAX(revision,excluded.revision)")) {
                    rev.setString(1, stall.id()); rev.setLong(2, revision); rev.executeUpdate();
                }
                try (PreparedStatement ack = connection.prepareStatement("INSERT INTO acknowledged_stalls(stall_id,revision,public_hash) VALUES(?,?,?) ON CONFLICT(stall_id) DO UPDATE SET revision=excluded.revision,public_hash=excluded.public_hash")) {
                    ack.setString(1, stall.id()); ack.setLong(2, revision); ack.setString(3, hash); ack.executeUpdate();
                }
                try (PreparedStatement clearMatching = connection.prepareStatement("DELETE FROM pending_stalls WHERE stall_id=? AND public_hash=?")) {
                    clearMatching.setString(1, stall.id()); clearMatching.setString(2, hash); clearMatching.executeUpdate();
                }
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback(); throw e;
        } finally { connection.setAutoCommit(true); }
    }

    public synchronized void enqueue(String stallId, long revision, String payload, String publicHash, String eventId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO pending_stalls(stall_id,revision,payload,public_hash,event_id,attempt_count,next_retry_at) VALUES(?,?,?,?,?,0,0) ON CONFLICT(stall_id) DO UPDATE SET revision=excluded.revision,payload=excluded.payload,public_hash=excluded.public_hash,event_id=excluded.event_id,attempt_count=0,next_retry_at=0")) {
            statement.setString(1, stallId); statement.setLong(2, revision); statement.setString(3, payload); statement.setString(4, publicHash); statement.setString(5, eventId); statement.executeUpdate();
        }
    }

    public synchronized List<Pending> ready(long now, int limit) throws SQLException {
        List<Pending> pending = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT stall_id,revision,payload,public_hash,event_id,attempt_count,next_retry_at FROM pending_stalls WHERE next_retry_at<=? ORDER BY next_retry_at,stall_id LIMIT ?")) {
            statement.setLong(1, now); statement.setInt(2, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) pending.add(new Pending(rows.getString(1), rows.getLong(2), rows.getString(3), rows.getString(4), rows.getString(5), rows.getInt(6), rows.getLong(7)));
            }
        }
        return pending;
    }

    public synchronized int pendingCount() throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet row = statement.executeQuery("SELECT COUNT(*) FROM pending_stalls")) { return row.next() ? row.getInt(1) : 0; }
    }

    public synchronized void acknowledge(Pending pending) throws SQLException {
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM pending_stalls WHERE stall_id=? AND event_id=?")) {
                delete.setString(1, pending.stallId()); delete.setString(2, pending.eventId()); delete.executeUpdate();
            }
            try (PreparedStatement ack = connection.prepareStatement("INSERT INTO acknowledged_stalls(stall_id,revision,public_hash) VALUES(?,?,?) ON CONFLICT(stall_id) DO UPDATE SET revision=excluded.revision,public_hash=excluded.public_hash")) {
                ack.setString(1, pending.stallId()); ack.setLong(2, pending.revision()); ack.setString(3, pending.publicHash()); ack.executeUpdate();
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback(); throw e;
        } finally { connection.setAutoCommit(true); }
    }

    public synchronized void failed(Pending pending, long nextRetryAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE pending_stalls SET attempt_count=attempt_count+1,next_retry_at=? WHERE stall_id=? AND event_id=?")) {
            statement.setLong(1, nextRetryAt); statement.setString(2, pending.stallId()); statement.setString(3, pending.eventId()); statement.executeUpdate();
        }
    }

    public synchronized String acknowledgedHash(String stallId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT public_hash FROM acknowledged_stalls WHERE stall_id=?")) {
            statement.setString(1, stallId); try (ResultSet rows = statement.executeQuery()) { return rows.next() ? rows.getString(1) : null; }
        }
    }

    public synchronized int publicShopId(UUID uuid) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT public_id FROM public_shop_ids WHERE shop_uuid=?")) {
            query.setString(1, uuid.toString()); try (ResultSet rows = query.executeQuery()) { if (rows.next()) return rows.getInt(1); }
        }
        int candidate = positiveUuidHash(uuid);
        while (true) {
            try (PreparedStatement occupied = connection.prepareStatement("SELECT shop_uuid FROM public_shop_ids WHERE public_id=?")) {
                occupied.setInt(1, candidate);
                try (ResultSet rows = occupied.executeQuery()) {
                    if (rows.next()) {
                        candidate = candidate == Integer.MAX_VALUE ? 1 : candidate + 1;
                        continue;
                    }
                }
            }
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO public_shop_ids(shop_uuid,public_id) VALUES(?,?)")) {
                insert.setString(1, uuid.toString()); insert.setInt(2, candidate); insert.executeUpdate(); return candidate;
            }
        }
    }

    static int positiveUuidHash(UUID uuid) {
        long mixed = uuid.getMostSignificantBits() ^ Long.rotateLeft(uuid.getLeastSignificantBits(), 29);
        mixed ^= mixed >>> 33; mixed *= 0xff51afd7ed558ccdl; mixed ^= mixed >>> 33;
        return (int) (Math.floorMod(mixed, Integer.MAX_VALUE - 1) + 1);
    }

    public static long retryDelayMillis(int attempts, int initialSeconds, int maximumSeconds) {
        int shift = Math.min(30, Math.max(0, attempts));
        long delay = Math.multiplyExact((long) initialSeconds * 1000L, 1L << shift);
        return Math.min(delay, (long) maximumSeconds * 1000L);
    }

    private String meta(String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT value FROM sync_meta WHERE key=?")) {
            statement.setString(1, key); try (ResultSet rows = statement.executeQuery()) { return rows.next() ? rows.getString(1) : null; }
        }
    }

    private void setMeta(String key, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO sync_meta(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value")) {
            statement.setString(1, key); statement.setString(2, value); statement.executeUpdate();
        }
    }

    @Override public synchronized void close() throws SQLException { connection.close(); }
    public record Pending(String stallId, long revision, String payload, String publicHash, String eventId, int attemptCount, long nextRetryAt) {}
}
