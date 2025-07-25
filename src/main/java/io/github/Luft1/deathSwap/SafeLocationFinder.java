package io.github.Luft1.deathSwap;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SafeLocationFinder {

    private final JavaPlugin plugin;
    private final SafeLocationChecker checker;
    private static final Random random = new Random();

    // This thread-safe queue stores locations that are pre-found and ready to use.
    private final ConcurrentLinkedQueue<Location> safeLocationCache = new ConcurrentLinkedQueue<>();
    private BukkitTask populatingTask;
    private boolean cacheReadyMessageSent = false; // Prevents spamming the 'ready' message

    // --- CONFIGURATION ---
    private static final int TARGET_CACHE_SIZE = 10; // How many locations we want ready.
    private static final int MAX_DISTANCE = 8000; // The world border or max distance to search.

    public SafeLocationFinder(JavaPlugin plugin) {
        this.plugin = plugin;
        this.checker = new SafeLocationChecker();
    }

    /**
     * Starts the background task to continuously find and cache safe locations.
     */
    public void initialize() {
        plugin.getLogger().info("Initializing SafeLocationFinder cache. Target size: " + TARGET_CACHE_SIZE);
        populatingTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::populateCache, 0L, 100L); // Every 5 seconds
    }

    /**
     * Stops the background caching task.
     */
    public void shutdown() {
        if (populatingTask != null) {
            populatingTask.cancel();
        }
    }

    /**
     * The core logic for the background task.
     */
    private void populateCache() {
        if (safeLocationCache.size() >= TARGET_CACHE_SIZE) {
            if (!cacheReadyMessageSent) {
                plugin.getLogger().info("Location cache is full and ready for a game!");
                // Broadcast a message to any online players with the admin permission
                Bukkit.getScheduler().runTask(plugin, () ->
                        Bukkit.broadcast(MiniMessage.miniMessage().deserialize("<dark_green>[DeathSwap] <green>Location cache is full. Ready to start."), "deathswap.admin")
                );
                cacheReadyMessageSent = true;
            }
            return; // Cache is full, do nothing.
        }

        // If cache becomes not full, allow ready message to be sent again next time it fills.
        cacheReadyMessageSent = false;
        findAndAddLocationToCache();
    }

    /**
     * Instantly gets a pre-found safe location from the cache.
     * @return A ready-to-use Location, or null if the cache is empty.
     */
    public Location getNextSafeLocation() {
        if (safeLocationCache.isEmpty()) {
            plugin.getLogger().warning("Location cache is empty! The game might lag while finding a new location on-demand.");
            return findLocationSynchronously(); // Fallback for emergencies
        }
        return safeLocationCache.poll();
    }

    private void findAndAddLocationToCache() {
        World world = Bukkit.getWorlds().getFirst();
        // Run this on an async thread to not lag the server
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (int i = 0; i < 500; i++) { // Give up after 500 random tries
                int x = random.nextInt(MAX_DISTANCE * 2) - MAX_DISTANCE;
                int z = random.nextInt(MAX_DISTANCE * 2) - MAX_DISTANCE;
                Location loc = checker.getSafeLocationInColumn(world, x, z);
                if (loc != null) {
                    safeLocationCache.add(loc);
                    plugin.getLogger().info("Found and cached a new location. Cache size: " + safeLocationCache.size());
                    return;
                }
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
        return null; // Very unlucky
    }
}
