package io.github.Luft1.deathSwap;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public class SafeLocationChecker {

    private static final EnumSet<Material> UNSAFE_GROUND = EnumSet.of(
            Material.LAVA, Material.MAGMA_BLOCK, Material.FIRE,
            Material.CAMPFIRE, Material.CACTUS, Material.NETHER_PORTAL, Material.AIR
    );

    // --- SOLUTION ---
    // Replaced EnumSet<Biome> with a standard HashSet<Biome>
    private static final Set<Biome> UNSAFE_BIOMES = new HashSet<>();

    // Statically initialize the set since we can't use .of() anymore
    static {
        UNSAFE_BIOMES.add(Biome.OCEAN);
        UNSAFE_BIOMES.add(Biome.DEEP_OCEAN);
        UNSAFE_BIOMES.add(Biome.COLD_OCEAN);
        UNSAFE_BIOMES.add(Biome.DEEP_COLD_OCEAN);
        UNSAFE_BIOMES.add(Biome.LUKEWARM_OCEAN);
        UNSAFE_BIOMES.add(Biome.DEEP_LUKEWARM_OCEAN);
        UNSAFE_BIOMES.add(Biome.FROZEN_OCEAN);
        UNSAFE_BIOMES.add(Biome.DEEP_FROZEN_OCEAN);
    }

    public Location getSafeLocationInColumn(World world, int x, int z) {
        Block groundBlock = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);

        // This check now uses the HashSet, but the logic remains the same.
        if (UNSAFE_BIOMES.contains(groundBlock.getBiome())) {
            return null;
        }

        if (isGroundSafe(groundBlock)) {
            Location playerFeetLocation = groundBlock.getLocation().add(0.5, 1.0, 0.5);
            Block feetBlock = playerFeetLocation.getBlock();
            Block headBlock = feetBlock.getRelative(BlockFace.UP);

            if (feetBlock.isPassable() && headBlock.isPassable()) {
                return playerFeetLocation;
            }
        }
        return null;
    }

    private boolean isGroundSafe(Block block) {
        return !UNSAFE_GROUND.contains(block.getType());
    }

}