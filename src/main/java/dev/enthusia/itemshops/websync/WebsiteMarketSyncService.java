package dev.enthusia.itemshops.websync;

import dev.enthusia.itemshops.ItemShopsPlugin;
import dev.enthusia.itemshops.model.Shop;
import dev.enthusia.itemshops.util.Pos;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class WebsiteMarketSyncService implements AutoCloseable {
    private final ItemShopsPlugin plugin;
    private final WebsiteSyncSettings settings;
    private final CanonicalMarketLayout layout;
    private final StallOwnershipProvider ownership;
    private final WebsiteSyncOutbox outbox;
    private final MarketSnapshotBuilder snapshots;
    private final ThreadPoolExecutor workers;
    private final Map<String, BukkitTask> debounced = new java.util.HashMap<>();
    private final java.util.Set<String> inFlight = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private BukkitTask startupTask;
    private BukkitTask reconciliationTask;
    private BukkitTask deliveryTask;
    private volatile boolean accepting;
    private volatile Instant lastFullSuccess;
    private volatile Instant lastStallSuccess;
    private volatile String lastSafeError;
    private volatile boolean reconciliationRequired;
    private volatile MarketHttpClient httpClient;

    public WebsiteMarketSyncService(ItemShopsPlugin plugin, WebsiteSyncSettings settings, StallOwnershipProvider ownership) throws SQLException {
        this.plugin = plugin; this.settings = settings; this.ownership = ownership;
        this.layout = CanonicalMarketLayout.load();
        WebsiteSyncSettings.Values values = settings.values();
        this.outbox = new WebsiteSyncOutbox(new File(plugin.getDataFolder(), values.outboxDatabaseFile()));
        this.snapshots = new MarketSnapshotBuilder(layout, ownership, plugin.shops(), outbox);
        this.workers = new ThreadPoolExecutor(values.maximumConcurrentRequests(), values.maximumConcurrentRequests(), 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(CanonicalMarketLayout.STALL_COUNT + 8), runnable -> {
                    Thread thread = new Thread(runnable, "ItemShops-website-sync"); thread.setDaemon(true); return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        restartScheduling();
    }

    public void restartScheduling() {
        cancelSchedules();
        accepting = settings.values().enabled() && settings.secretConfigured();
        httpClient = accepting ? new MarketHttpClient(settings.values()) : null;
        if (!accepting) return;
        long startupTicks = settings.values().startupDelaySeconds() * 20L;
        startupTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> full(result -> {}), startupTicks);
        long reconciliationTicks = settings.values().reconciliationMinutes() * 60L * 20L;
        reconciliationTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::reconcile, startupTicks + reconciliationTicks, reconciliationTicks);
        deliveryTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::drainPending, 20L, 20L);
    }

    public void markShopDirty(Shop shop) { if (shop != null) markPositionDirty(shop.sign()); }
    public void markContainerDirty(Pos container) {
        if (container == null) return;
        for (Shop shop : plugin.shops().byContainer(container)) markShopDirty(shop);
    }

    private void markPositionDirty(Pos position) {
        if (!accepting) return;
        CanonicalMarketLayout.Assignment assignment = layout.assign(position);
        if (assignment.status() != CanonicalMarketLayout.Assignment.Status.MATCHED) return;
        String stallId = assignment.stall().id();
        BukkitTask previous = debounced.remove(stallId);
        if (previous != null) previous.cancel();
        long ticks = Math.max(1L, (settings.values().stallDebounceMilliseconds() + 49L) / 50L);
        debounced.put(stallId, plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            debounced.remove(stallId);
            captureDirty(stallId);
        }, ticks));
    }

    private void captureDirty(String stallId) {
        if (!accepting) return;
        try {
            MarketDtos.Stall stall = snapshots.captureOne(stallId);
            String hash = MarketRequestSigner.sha256(MarketJson.encodeBytes(stall));
            if (hash.equals(outbox.acknowledgedHash(stallId))) return;
            long revision = outbox.nextStallRevision(stallId);
            String eventId = UUID.randomUUID().toString();
            Map<String, Object> body = envelope(eventId);
            body.put("revision", revision); body.put("stall", stall);
            outbox.enqueue(stallId, revision, MarketJson.encode(body), hash, eventId);
            drainPending();
        } catch (Exception e) { safeError("Could not capture a dirty Market stall", e); }
    }

    public void test(Consumer<Result> callback) {
        if (!settings.values().enabled()) { callback.accept(Result.failed("Website Market sync is disabled.")); return; }
        if (!settings.secretConfigured()) { callback.accept(Result.failed("Website Market sync secret is not configured.")); return; }
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> body = envelope(eventId); body.put("probe", "itemshops-1.8.0");
        if (!submit(() -> {
            Result result;
            try {
                byte[] bytes = MarketJson.encodeBytes(body);
                MarketHttpClient.Result response = client().send("POST", "/internal/v1/test", eventId, bytes);
                result = response.successful() ? Result.passed("Authenticated Market transport test passed.") : Result.failed("Market transport test returned HTTP " + response.statusCode() + ".");
            } catch (Exception e) { result = Result.failed("Market transport test failed: " + safeMessage(e)); }
            Result finalResult = result;
            plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(finalResult));
        })) callback.accept(Result.failed("Website Market worker queue is full."));
    }

    public void full(Consumer<Result> callback) {
        if (!settings.values().enabled() || !settings.secretConfigured()) { callback.accept(Result.failed("Website Market sync is disabled or has no secret.")); return; }
        if (!ownership.available()) { callback.accept(Result.failed("Ownership provider unavailable: " + ownership.unavailableReason())); return; }
        try {
            List<MarketDtos.Stall> stalls = snapshots.captureAll();
            long revision = outbox.nextSnapshotRevision();
            String eventId = UUID.randomUUID().toString();
            Map<String, Object> body = envelope(eventId);
            body.put("snapshotRevision", revision); body.put("generatedAt", Instant.now().toString());
            List<MarketDtos.StallRevision> entries = stalls.stream().map(stall -> new MarketDtos.StallRevision(revision, stall)).toList();
            body.put("stalls", entries);
            byte[] bytes = MarketJson.encodeBytes(body);
            if (!submit(() -> {
                Result result;
                try {
                    MarketHttpClient.Result response = client().send("POST", "/internal/v1/full-sync", eventId, bytes);
                    if (response.successful()) {
                        outbox.markFullAcknowledged(revision, stalls); lastFullSuccess = Instant.now(); lastSafeError = null; reconciliationRequired = false;
                        result = Result.passed("Full 71-stall Market sync passed.");
                    } else result = Result.failed("Full Market sync returned HTTP " + response.statusCode() + ".");
                } catch (Exception e) { safeError("Full Market sync failed", e); result = Result.failed("Full Market sync failed: " + safeMessage(e)); }
                Result finalResult = result;
                plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(finalResult));
            })) callback.accept(Result.failed("Website Market worker queue is full."));
        } catch (Exception e) { callback.accept(Result.failed("Full Market sync rejected: " + safeMessage(e))); }
    }

    private void reconcile() {
        if (!accepting || !ownership.available()) return;
        try {
            for (MarketDtos.Stall stall : snapshots.captureAll()) {
                String hash = MarketRequestSigner.sha256(MarketJson.encodeBytes(stall));
                if (!hash.equals(outbox.acknowledgedHash(stall.id()))) captureDirty(stall.id());
            }
        } catch (Exception e) { safeError("Market reconciliation failed", e); }
    }

    public void retry() { if (accepting) drainPending(); }

    private void drainPending() {
        if (!accepting || reconciliationRequired) return;
        try {
            for (WebsiteSyncOutbox.Pending pending : outbox.ready(System.currentTimeMillis(), settings.values().maximumConcurrentRequests())) sendPending(pending);
        } catch (SQLException e) { safeError("Could not read Website Market outbox", e); }
    }

    private void sendPending(WebsiteSyncOutbox.Pending pending) {
        if (!inFlight.add(pending.eventId())) return;
        if (!submit(() -> {
            try {
                MarketHttpClient.Result response = client().send("PUT", "/internal/v1/stalls/" + pending.stallId(), pending.eventId(), pending.payload().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if (response.successful()) {
                    outbox.acknowledge(pending);
                    lastStallSuccess = Instant.now(); lastSafeError = null;
                } else if (response.statusCode() == 409) {
                    reconciliationRequired = true; lastSafeError = "Market state requires controlled full reconciliation (HTTP 409).";
                } else scheduleFailure(pending);
            } catch (Exception e) { scheduleFailure(pending); safeError("Website Market stall delivery failed", e); }
            finally { inFlight.remove(pending.eventId()); }
        })) inFlight.remove(pending.eventId());
    }

    private void scheduleFailure(WebsiteSyncOutbox.Pending pending) {
        try {
            int attempts = pending.attemptCount() + 1;
            int capped = Math.min(attempts, settings.values().maximumAttemptsBeforeCooldown());
            long delay = WebsiteSyncOutbox.retryDelayMillis(capped, settings.values().initialRetrySeconds(), settings.values().maximumRetrySeconds());
            outbox.failed(pending, System.currentTimeMillis() + delay);
        } catch (SQLException e) { safeError("Could not update Website Market retry", e); }
    }

    private Map<String, Object> envelope(String eventId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schemaVersion", 1); body.put("serverId", settings.values().serverId());
        try { body.put("serverEpoch", outbox.serverEpoch()); } catch (SQLException e) { throw new IllegalStateException("Website Market outbox unavailable", e); }
        body.put("eventId", eventId); body.put("sentAt", Instant.now().toString());
        return body;
    }

    private MarketHttpClient client() {
        MarketHttpClient current = httpClient;
        if (current == null) throw new IllegalStateException("Website Market transport is not configured");
        return current;
    }

    private boolean submit(Runnable work) {
        try { workers.execute(work); return true; }
        catch (RejectedExecutionException e) { lastSafeError = "Website Market worker queue is full."; return false; }
    }

    private void safeError(String context, Exception error) {
        lastSafeError = context + ": " + safeMessage(error);
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message.replaceAll("https?://\\S+", "remote endpoint");
    }

    public Status status() {
        int pending = -1; long revision = -1;
        try { pending = outbox.pendingCount(); revision = outbox.snapshotRevision(); } catch (SQLException ignored) {}
        return new Status(settings.values().enabled(), settings.secretConfigured(), ownership.available(), ownership.name(),
                lastFullSuccess, lastStallSuccess, pending, shortEpoch(), revision, lastSafeError);
    }

    private String shortEpoch() {
        try { String epoch = outbox.serverEpoch(); return epoch.substring(0, Math.min(8, epoch.length())); }
        catch (SQLException e) { return "unavailable"; }
    }

    public WebsiteSyncSettings settings() { return settings; }
    public StallOwnershipProvider ownership() { return ownership; }

    private void cancelSchedules() {
        accepting = false;
        httpClient = null;
        if (startupTask != null) startupTask.cancel(); if (reconciliationTask != null) reconciliationTask.cancel(); if (deliveryTask != null) deliveryTask.cancel();
        startupTask = null; reconciliationTask = null; deliveryTask = null;
        for (BukkitTask task : new ArrayList<>(debounced.values())) task.cancel(); debounced.clear();
    }

    @Override public void close() {
        cancelSchedules(); workers.shutdown();
        try { if (!workers.awaitTermination(2, TimeUnit.SECONDS)) workers.shutdownNow(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); workers.shutdownNow(); }
        try { outbox.close(); } catch (SQLException e) { plugin.getLogger().warning("Could not close Website Market outbox: " + safeMessage(e)); }
    }

    public record Result(boolean successful, String message) {
        static Result passed(String message) { return new Result(true, message); }
        static Result failed(String message) { return new Result(false, message); }
    }
    public record Status(boolean enabled, boolean secretConfigured, boolean providerAvailable, String providerName,
                         Instant lastFullSuccess, Instant lastStallSuccess, int pendingCount, String shortEpoch,
                         long snapshotRevision, String lastSafeError) {}
}
