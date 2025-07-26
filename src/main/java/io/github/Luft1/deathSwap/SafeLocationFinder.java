package io.github.Luft1.deathSwap;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SafeLocationFinder {

    private final JavaPlugin plugin;
    private final SafeLocationChecker checker;
    private static final Random random = new Random();

    private final ConcurrentLinkedQueue<Location> safeLocationCache = new ConcurrentLinkedQueue<>();
    private BukkitTask populatingTask;
    private boolean cacheReadyMessageSent = false;

    // --- SOLUTION ---
    // This flag prevents multiple find operations from running at the same time.
    private volatile boolean isFindingLocation = false;

    // --- CONFIGURATION ---
    private static final int TARGET_CACHE_SIZE = 10;
    private static final int MAX_DISTANCE = 8000;

    public SafeLocationFinder(JavaPlugin plugin) {
        this.plugin = plugin;
        this.checker = new SafeLocationChecker();
    }

    public void initialize() {
        plugin.getLogger().info("Initializing SafeLocationFinder cache. Target size: " + TARGET_CACHE_SIZE);
        populatingTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::populateCache, 0L, 100L);
    }

    public void shutdown() {
        if (populatingTask != null) {
            populatingTask.cancel();
        }
        plugin.getLogger().info("Clearing location cache and releasing " + safeLocationCache.size() + " chunk tickets.");
        for (Location loc : safeLocationCache) {
            loc.getWorld().removePluginChunkTicket(loc.getChunk().getX(), loc.getChunk().getZ(), plugin);
        }
        safeLocationCache.clear();
    }

    private void populateCache() {
        // --- FIX ---
        // If we are already searching for a location, don't start another search.
        if (isFindingLocation || safeLocationCache.size() >= TARGET_CACHE_SIZE) {
            if (safeLocationCache.size() >= TARGET_CACHE_SIZE && !cacheReadyMessageSent) {
                plugin.getLogger().info("Location cache is full and ready for a game!");
                Bukkit.getScheduler().runTask(plugin, () ->
                        Bukkit.broadcast(MiniMessage.miniMessage().deserialize("<dark_green>[DeathSwap] <green>Location cache is full. Ready to start."), "deathswap.admin")
                );
                cacheReadyMessageSent = true;
            }
            return;
        }

        cacheReadyMessageSent = false;
        findAndAddLocationToCache();
    }

    public Location getNextSafeLocation() {
        Location loc = safeLocationCache.poll();
        if (loc != null) {
            plugin.getLogger().info("Using cached location and removing chunk ticket at: " + loc.toVector());
            loc.getWorld().removePluginChunkTicket(loc.getChunk().getX(), loc.getChunk().getZ(), plugin);
        } else {
            plugin.getLogger().warning("Location cache is empty! The game might lag while finding a new location on-demand.");
            return findLocationSynchronously();
        }
        return loc;
    }

    private void findAndAddLocationToCache() {
        // --- FIX ---
        // Set the lock to prevent other tasks from starting.
        isFindingLocation = true;
        World world = Bukkit.getWorlds().getFirst();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                for (int i = 0; i < 500; i++) {
                    // Only add if the cache still needs locations.
                    if (safeLocationCache.size() >= TARGET_CACHE_SIZE) {
                        return;
                    }
                    int x = random.nextInt(MAX_DISTANCE * 2) - MAX_DISTANCE;
                    int z = random.nextInt(MAX_DISTANCE * 2) - MAX_DISTANCE;
                    Location loc = checker.getSafeLocationInColumn(world, x, z);
                    if (loc != null) {
                        plugin.getLogger().info("Found a new location. Adding chunk ticket at: " + loc.toVector());
                        world.addPluginChunkTicket(loc.getChunk().getX(), loc.getChunk().getZ(), plugin);
                        safeLocationCache.add(loc);
                        plugin.getLogger().info("Cached a new location. Cache size: " + safeLocationCache.size());
                        return; // Exit after finding one location.
                    }
                }
            } finally {
                // --- FIX ---
                // Always release the lock, even if no location was found.
                isFindingLocation = false;
            }
        });
    }

    private Location findLocationSynchronously() {
        World world = Bukkit.getWorlds().getFirst();
        for (int i = 0; i < 1000; i++) {
            int x = random.nextInt(MAX_DISTANCE * 2) - MAX_DISTANCE;
            int z = random.nextInt(MAX_DISTANCE * 2) - MAX_DISTANCE;
            Location loc = checker.getSafeLocationInColumn(world, x, z);
            if (loc != null) return loc;
        }
        return null;
    }
}