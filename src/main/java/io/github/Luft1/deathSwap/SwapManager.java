package io.github.Luft1.deathSwap;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.scheduler.*;

import java.util.*;


public class SwapManager implements Listener {
    private static SwapManager instance;
    private final DeathSwap SwapPlugin;
    private boolean roundInProgress = false;
    private ArrayList<Player> contestants = new ArrayList<>();
    private ArrayList<Player> spectators = new ArrayList<>();
    private HashSet<BukkitTask> bukkitTasks = new HashSet<BukkitTask>();

    private SwapManager(DeathSwap plugin) {
        SwapPlugin = plugin;
    }
    // 1 is completely unweighted. A higher number means that the swap is more likely to occur later, closer to the max swap time.

    public static void init(DeathSwap plugin) {
        if (instance == null) {
            instance = new SwapManager(plugin);
        }
    }

    private Player[] createDerangedPlayerArray(Player[] playerList) {
        Random random = new Random();
        Player[] shuffled = playerList.clone();

        // Keep shuffling until we have a valid derangement
        do {
            // Fisher-Yates shuffle
            for (int i = shuffled.length - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                // Swap elements
                Player temp = shuffled[i];
                shuffled[i] = shuffled[j];
                shuffled[j] = temp;
            }
        } while (!isValidDerangement(playerList, shuffled));

        return shuffled;
    }

    private boolean isValidDerangement(Player[] original, Player[] shuffled) {
        for (int i = 0; i < original.length; i++) {
            if (original[i].equals(shuffled[i])) {
                return false;
            }
        }
        return true;
    }


    private Location getRandomSafeLocation() {
        boolean validLocation = false;
        Location newLocation = null; // Initialize to null
        Random random = new Random();
        World overWorld = Bukkit.getWorlds().getFirst(); // Assuming you want the overworld

        // Define the boundaries for random teleport
        int maxDistance = 10000; // Maximum distance from spawn
        Bukkit.getLogger().info("Starting search for safe location");
        while (!validLocation) { // Removed 'do' keyword, it's 'while (condition)'
            int x = random.nextInt(maxDistance * 2) - maxDistance; // Random between -maxDistance and +maxDistance
            int z = random.nextInt(maxDistance * 2) - maxDistance;

            int y = overWorld.getHighestBlockYAt(x, z);
            newLocation = new Location(overWorld, x, y, z);

            // --- Validation Logic ---

            // 1. Check if the block *at* the location is not water/lava
            // This 'y' is the highest solid block. Players should spawn *on* this.
            Block blockAtFeet = newLocation.getBlock();

            // 2. Check the block *above* the location to ensure there's air for the player
            // Players are 2 blocks tall, so check the block they stand on and the one above.
            Block blockAboveFeet = newLocation.add(0, 1, 0).getBlock(); // Temporarily move up to check
            newLocation.subtract(0, 1, 0); // Move back down for the actual spawn location

            // You could also check the biome for additional filtering, though material check is often sufficient
            Biome biome = blockAtFeet.getBiome();

            // Define materials considered "water" or "lava"
            // Add other "liquid" materials if needed (e.g., Powdered Snow, some modded liquids)
            if (blockAtFeet.getType() != Material.WATER &&
                    blockAtFeet.getType() != Material.LAVA &&
                    blockAtFeet.getType() != Material.BUBBLE_COLUMN && // Consider these too
                    blockAtFeet.getType() != Material.SEAGRASS &&
                    blockAtFeet.getType() != Material.TALL_SEAGRASS &&
                    blockAboveFeet.getType() == Material.AIR) { // Ensure there's air above

                // Further refine: Avoid spawning on dangerous blocks like magma blocks if desired
                if (blockAtFeet.getType() != Material.MAGMA_BLOCK &&
                        blockAtFeet.getType() != Material.FIRE &&
                        blockAtFeet.getType() != Material.CAMPFIRE) {

                    // You might also want to avoid certain biomes like deep oceans
                    // This is more of a "nice-to-have" and depends on your game's feel
                    if (biome != Biome.DEEP_OCEAN &&
                            biome != Biome.OCEAN &&
                            biome != Biome.COLD_OCEAN &&
                            biome != Biome.LUKEWARM_OCEAN &&
                            biome != Biome.FROZEN_OCEAN) {

                        validLocation = true;
                    }
                }
            }
        }
        Bukkit.getLogger().info("Search for a safe location finished");
        return newLocation;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        bukkitTasks.add(new BukkitRunnable() {
            @Override
            public void run() {
                Bukkit.broadcastMessage(event.getPlayer().getName() + " has joined the game!");
                Player player = event.getPlayer();
                if (!contestants.contains(player) && !spectators.contains(player)) {
                    addSpectator(player);
                }
            }
        }.runTaskLater(SwapPlugin, 5L)
        );

    };

    private void removeContestant(Player player) {
        contestants.remove(player);
        if (roundInProgress && contestants.size() == 1) {
            endRound();
        }
    }

    private void removeSpectator(Player player) {
        spectators.remove(player);
    }

    private void addSpectator(Player player) {
        player.getInventory().clear();
        spectators.add(player);
        player.setGameMode( GameMode.SPECTATOR);
        if (roundInProgress) {
            player.sendMessage("You are now spectating the round!");
            // no tp stick needed; the spectate gamemode already provides a tp to player feature
        }
    }

    private void teleportPlayerSafely(Player player, Location targetLocation) {
        // Get the chunk where the player will be teleported
        Chunk targetChunk = targetLocation.getChunk();
        World world = targetLocation.getWorld();

        // 1. Force load the chunk(s)
        // Load the target chunk and potentially a few surrounding chunks for a smoother experience.
        // A common pattern is to load a 3x3 square of chunks around the target.
        int centerChunkX = targetChunk.getX();
        int centerChunkZ = targetChunk.getZ();

        for (int xOffset = -1; xOffset <= 1; xOffset++) {
            for (int zOffset = -1; zOffset <= 1; zOffset++) {
                world.loadChunk(centerChunkX + xOffset, centerChunkZ + zOffset, true); // Load and generate if needed
            }
        }

        // 2. Schedule the teleport to happen after a short delay
        // This gives the server a few ticks to process the chunk loading.
        // 5-10 ticks (0.25 - 0.5 seconds) is usually sufficient.
        bukkitTasks.add(new BukkitRunnable() {
            @Override
            public void run() {
                // Double-check if the chunk is actually loaded (for robustness, though less critical with load(true))
                if (targetChunk.isLoaded()) {
                    player.teleport(targetLocation);
                    player.sendMessage("You have been death-swapped to a new location!");
                } else {
                    // Fallback in case chunk somehow didn't load (very rare if load(true) is used)
                    player.sendMessage("Failed to load destination chunk. Please notify an admin.");
                    // You might want to try again, or teleport them to spawn
                }
            }
        }.runTaskLater(SwapPlugin, 10L)
        ); // 'this' refers to your main plugin instance, 10L is 10 ticks
    }

    private void addContestant(Player player) {

        player.setGameMode( GameMode.SURVIVAL);
        contestants.add(player);
        player.getInventory().clear();
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setSaturation(5);

        teleportPlayerSafely(player, getRandomSafeLocation());
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        if (spectators.contains(event.getPlayer())) {
            spectators.remove(event.getPlayer());
        } else {
            removeContestant(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        if (contestants.contains(event.getEntity())) {
            removeContestant(event.getEntity());
            addSpectator(event.getEntity());
            Bukkit.broadcastMessage(contestants.size() + " contestants remain.");
            // TODO: Reward a token of undying to the player whose location this player swapped to.
        }
    }

    public void clearAllScheduledSwaps() {
        if (bukkitTasks.isEmpty()) {
            // No tasks to clear
            return;
        }

        // Option 1: Using for-each loop (recommended for clarity and safety)
        for (BukkitTask task : bukkitTasks) {
            task.cancel(); // Cancel the individual task
        }
        bukkitTasks.clear(); // Clear the HashSet after cancelling all tasks

        // Option 2: Using forEach with a lambda (more concise)
        // bukkitTasks.forEach(BukkitTask::cancel); // This is a method reference to call cancel() on each task
        // bukkitTasks.clear(); // Clear the HashSet after cancelling all tasks

        // Note: You might consider an Iterator if you need to remove elements conditionally
        // during iteration, but for cancelling all and then clearing, a simple for-each or forEach is fine.
    }

    public void endRound() {
        clearAllScheduledSwaps();
        Bukkit.broadcastMessage("A round has concluded!");
        roundInProgress = false;
        if (contestants.size() == 1) {
            contestants.getFirst().sendMessage("You won the round!");
            Bukkit.broadcastMessage(contestants.getFirst().getName() + " won the round!");
        } else {
            Bukkit.broadcastMessage("The round ended in a tie!");
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (contestants.contains(player)) {
                removeContestant(player);
                addSpectator(player);
            }
        }
    }

    private void swapPlayers() {

        if (!roundInProgress) {
            return;
        }

        Player[] oldPlayerOrder = contestants.toArray(new Player[0]);
        Player[] newPlayerOrder = createDerangedPlayerArray(contestants.toArray(new Player[0]));
        Location[] originalPlayerLocations = Arrays.stream(oldPlayerOrder).map(Player::getLocation).toArray(Location[]::new);

        for (int i = 0; i < newPlayerOrder.length; i++) {
            teleportPlayerSafely(newPlayerOrder[i], originalPlayerLocations[i]);
        }

        scheduleNextSwap();
    }

    public void startRound() {
        roundInProgress = true;
        if (Bukkit.getOnlinePlayers().size() < 2) {
            Bukkit.broadcastMessage("Not enough players to start a round! At least 2 players are required.");
            return;
        }

        Bukkit.broadcastMessage("A round has begun!");
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (contestants.contains(player)) {
                removeContestant(player);
            }
            if (spectators.contains(player)) {
                removeSpectator(player);
            }
            addContestant(player);
        }

        scheduleNextSwap();

    }

    private void scheduleNextSwap() {
        int secondsBeforeNextSwap = getRandomSwapTimeWeighted();
        int ticksBeforeNextSwap = ticksFromSeconds(secondsBeforeNextSwap);
        Bukkit.getLogger().info("Next swap scheduled in" + String.valueOf(secondsBeforeNextSwap));


        bukkitTasks.add(
                new BukkitRunnable() {
                @Override
                public void run() {
                    swapPlayers();
                }
            }.runTaskLater(SwapPlugin, ticksBeforeNextSwap)
        );
    }

    private int ticksFromSeconds(int seconds) {
        return seconds * 20;
    }

    private int getRandomSwapTimeWeighted() {
        Random random = new Random();
        int MAX_TIME_BETWEEN_SWAPS = 60;
        //This must be a number greater than 0.
        float SWAP_PROBABILITY_WEIGHT = 2;
        return (int) Math.pow(random.nextInt(0, (int) Math.pow(MAX_TIME_BETWEEN_SWAPS, SWAP_PROBABILITY_WEIGHT)), 1.00/ SWAP_PROBABILITY_WEIGHT);
    }

    public static SwapManager get() {
        if (instance == null) {
            throw new IllegalStateException("SwapManager has not been initialized!");
        }
        return instance;

    }
}