package io.github.Luft1.deathSwap;

import org.bukkit.*;
import org.bukkit.block.*;

import java.util.*;
import java.util.concurrent.*;

public class SafeLocationFinder {

    private static final Random random = new Random();
    // Pre-define unsafe blocks for faster checking. Using an EnumSet is highly efficient.
    private static final EnumSet<Material> unsafeBlocks = EnumSet.of(
            Material.LAVA,
            Material.WATER,
            Material.MAGMA_BLOCK,
            Material.FIRE,
            Material.CAMPFIRE,
            Material.CACTUS
    );

    /**
     * Asynchronously finds a random, safe location for teleportation.
     * This method is non-blocking and will not lag the server.
     *
     * @param world The world to search in.
     * @param maxDistance The maximum distance from (0,0) to search.
     * @return A CompletableFuture that will complete with a safe Location.
     */
    public CompletableFuture<Location> findRandomSafeLocationAsync(World world, int maxDistance) {
        CompletableFuture<Location> future = new CompletableFuture<>();

        // Pick random coordinates
        int x = random.nextInt(maxDistance * 2) - maxDistance;
        int z = random.nextInt(maxDistance * 2) - maxDistance;

        // Request the chunk asynchronously. This is the key to avoid lag.
        // Paper is recommended for its improved getChunkAtAsync implementation.
        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
            // This code runs once the chunk is loaded, without blocking the main thread.
            int y = world.getHighestBlockYAt(x, z);
            Location potentialLocation = new Location(world, x + 0.5, y + 1, z + 0.5); // Center the location

            if (isLocationSafe(potentialLocation)) {
                // If it's safe, complete the future with the location.
                future.complete(potentialLocation);
            } else {
                // If not safe, recursively try again.
                // This will chain another async search without blocking.
                findRandomSafeLocationAsync(world, maxDistance).thenAccept(future::complete);
            }
        });

        return future;
    }

    /**
     * Checks if a given location is safe for a player to spawn at.
     * A location is safe if the block below is solid and not harmful,
     * and the two blocks at the location are air.
     *
     * @param location The location to check.
     * @return true if the location is safe, false otherwise.
     */
    private boolean isLocationSafe(Location location) {
        // We check the block the player will stand ON, not in.
        // The location passed in should be for the player's feet (y+1 from the highest block).
        Block groundBlock = location.clone().subtract(0, 1, 0).getBlock();
        Block feetBlock = location.getBlock();
        Block headBlock = location.clone().add(0, 1, 0).getBlock();

        // Ensure the player has 2 blocks of air to stand in and isn't submerged.
        if (!feetBlock.isPassable() || !headBlock.isPassable()) {
            return false;
        }

        // Ensure the ground is solid and not a liquid or dangerous block.
        if (!groundBlock.getType().isSolid() || unsafeBlocks.contains(groundBlock.getType())) {
            return false;
        }

        // You could add biome checks here if you still want them.
        // Biome biome = groundBlock.getBiome();
        // if (biome == Biome.OCEAN || biome == Biome.DEEP_OCEAN) return false;

        return true;
    }
}