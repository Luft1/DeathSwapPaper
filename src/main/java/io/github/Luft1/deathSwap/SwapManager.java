
package io.github.Luft1.deathSwap;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class SwapManager implements Listener {
    private final DeathSwap SwapPlugin;
    private final SafeLocationFinder finder;
    private boolean roundInProgress = false;
    private final ArrayList<Player> contestants = new ArrayList<>();
    private final ArrayList<Player> spectators = new ArrayList<>();
    private final HashSet<BukkitTask> bukkitTasks = new HashSet<>();
    private final int MAX_TIME_BETWEEN_SWAPS = 120;

    // This map will store who swapped with whom.
    // Key: The player who was teleported (the one who might die).
    // Value: The player whose location they were sent to.
    private final Map<Player, Player> swapDestinations = new HashMap<>();

    // --- Timer Fields ---
    private BukkitTask timerTask;
    private long lastSwapTime;
    private int secondsUntilNextSwap;


    public SwapManager(DeathSwap plugin, SafeLocationFinder locationFinder) {
        this.SwapPlugin = plugin;
        this.finder = locationFinder;
    }

    private Player[] createDerangedPlayerArray(Player[] playerList) {
        Random random = new Random();
        Player[] shuffled = playerList.clone();
        if (playerList.length < 2) return shuffled; // No derangement possible with < 2 players

        do {
            for (int i = shuffled.length - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        bukkitTasks.add(new BukkitRunnable() {
            @Override
            public void run() {
                Player player = event.getPlayer();
                if (!contestants.contains(player) && !spectators.contains(player)) {
                    addSpectator(player);
                }
            }
        }.runTaskLater(SwapPlugin, 5L));
    }

    private void removeContestant(Player player) {
        contestants.remove(player);
        swapDestinations.remove(player); // Clean up the map when a player is removed
        if (roundInProgress && contestants.size() <= 1) {
            endRound();
        }
    }

    private void removeSpectator(Player player) {
        spectators.remove(player);
    }

    private void addSpectator(Player player) {
        player.getInventory().clear();
        spectators.add(player);
        player.setGameMode(GameMode.SPECTATOR);
        if (roundInProgress) {
            player.sendMessage("You are now spectating the round!");
        }
    }

    private void addContestant(Player player) {
        Location safeLocation = finder.getNextSafeLocation();
        // Load the chunk asynchronously before teleporting
        safeLocation.getWorld().getChunkAtAsync(safeLocation).thenRun(() -> {
            player.teleportAsync(safeLocation).thenRun(() -> {
                player.sendMessage("This is your starting location");
                player.setGameMode(GameMode.SURVIVAL);
                player.getInventory().clear();
                player.setHealth(20);
                player.setFoodLevel(20);
                player.setSaturation(5);
                contestants.add(player);
            });
        });
    }


    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        removeContestant(event.getPlayer());
        removeSpectator(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player eliminatedPlayer = event.getEntity();
        if (contestants.contains(eliminatedPlayer)) {
            // Find who owned the location where the player died
            Player locationOwner = swapDestinations.get(eliminatedPlayer);

            if (locationOwner != null) {
                String message = String.format(
                        "<red>%s</red> has been eliminated after swapping to <aqua>%s</aqua>'s location. <gray>(%d contestants remain)</gray>",
                        eliminatedPlayer.getName(),
                        locationOwner.getName(),
                        contestants.size() - 1 // Subtract 1 because the player is about to be removed
                );
                Bukkit.broadcast(MiniMessage.miniMessage().deserialize(message));
                // TODO: Reward locationOwner with a totem of undying
            } else {
                // Fallback message if for some reason the swap data isn't available
                String fallbackMessage = String.format(
                        "<red>%s</red> has been eliminated. <gray>(%d contestants remain)</gray>",
                        eliminatedPlayer.getName(),
                        contestants.size() - 1
                );
                Bukkit.broadcast(MiniMessage.miniMessage().deserialize(fallbackMessage));
            }

            removeContestant(eliminatedPlayer);
            addSpectator(eliminatedPlayer);
        }
    }

    public void clearAllScheduledSwaps() {
        if (!bukkitTasks.isEmpty()) {
            bukkitTasks.forEach(BukkitTask::cancel);
            bukkitTasks.clear();
        }
        stopTimer(); // Centralized cleanup
    }

    public void endRound() {
        clearAllScheduledSwaps();
        roundInProgress = false;
        swapDestinations.clear(); // Clear the map at the end of the round

        if (contestants.size() == 1) {
            Player winner = contestants.getFirst();
            Bukkit.broadcast(MiniMessage.miniMessage().deserialize(String.format("<gold>%s won the round!</gold>", winner.getName())));
        } else {
            Bukkit.broadcast(MiniMessage.miniMessage().deserialize("<green>The round has ended in a tie!</green>"));
        }

        // Move all remaining contestants to spectators
        new ArrayList<>(contestants).forEach(p -> {
            removeContestant(p);
            addSpectator(p);
        });
    }

    private void swapPlayers() {
        if (!roundInProgress || contestants.size() < 2) {
            return;
        }

        Player[] oldPlayerOrder = contestants.toArray(new Player[0]);
        Player[] newPlayerOrder = createDerangedPlayerArray(oldPlayerOrder);
        Location[] originalPlayerLocations = Arrays.stream(oldPlayerOrder).map(Player::getLocation).toArray(Location[]::new);

        swapDestinations.clear(); // Clear old swap data before creating new assignments

        List<CompletableFuture<Boolean>> teleportFutures = new ArrayList<>();

        for (int i = 0; i < oldPlayerOrder.length; i++) {
            Player playerToTeleport = newPlayerOrder[i];
            Player originalOwnerOfLocation = oldPlayerOrder[i];
            Location targetLocation = originalPlayerLocations[i];

            swapDestinations.put(playerToTeleport, originalOwnerOfLocation);

            CompletableFuture<Boolean> future = targetLocation.getWorld().getChunkAtAsync(targetLocation)
                    .thenCompose(chunk -> {
                        playerToTeleport.sendMessage("swapping to " + originalOwnerOfLocation.getName() + "'s location");
                        return playerToTeleport.teleportAsync(targetLocation);
                    });

            teleportFutures.add(future);
        }

        CompletableFuture.allOf(teleportFutures.toArray(new CompletableFuture[0])).thenRun(() -> {
            this.lastSwapTime = System.currentTimeMillis(); // Reset after swap
            scheduleNextSwap();
        });
    }


    public void startRound() {
        if (Bukkit.getOnlinePlayers().size() < 2) {
            Bukkit.broadcast(MiniMessage.miniMessage().deserialize("<red>Not enough players to start a round! At least 2 are required.</red>"));
            return;
        }

        clearAllScheduledSwaps(); // Clear any old tasks
        roundInProgress = true;
        Bukkit.broadcast(MiniMessage.miniMessage().deserialize("<green>The round is starting now!</green>"));
        contestants.clear();
        spectators.clear();
        swapDestinations.clear();

        for (Player player : Bukkit.getOnlinePlayers()) {
            addContestant(player);
        }

        // Initial delay before the first swap
        bukkitTasks.add(new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (roundInProgress) {
                            lastSwapTime = System.currentTimeMillis();
                            startTimer(); // Start the timer
                            scheduleNextSwap(); // Schedule the first swap
                        }
                    }
                }.runTaskLater(SwapPlugin, 60L) // 3-second delay before anything happens
        );
    }

    private void scheduleNextSwap() {
        this.secondsUntilNextSwap = getRandomSwapTimeWeighted();
        int ticksBeforeNextSwap = this.secondsUntilNextSwap * 20;
        Bukkit.getLogger().info("Next swap scheduled in " + secondsUntilNextSwap + " seconds.");

        BukkitTask swapTask = new BukkitRunnable() {
            @Override
            public void run() {
                swapPlayers();
            }
        }.runTaskLater(SwapPlugin, ticksBeforeNextSwap);
        bukkitTasks.add(swapTask);
    }

    private void stopTimer() {
        if (timerTask != null) {
            timerTask.cancel();
            bukkitTasks.remove(timerTask);
            timerTask = null;
        }
    }

    private void startTimer() {
        stopTimer(); // Ensure no other timer is running

        timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!roundInProgress) {
                    this.cancel(); // Stop if the round has ended
                    return;
                }

                long elapsedMillis = System.currentTimeMillis() - lastSwapTime;
                long elapsedSeconds = elapsedMillis / 1000;

                // Prevent division by zero if the next swap time isn't set yet
                if (MAX_TIME_BETWEEN_SWAPS <= 0) return;

                // Determine the color and hazard level based on how close the next swap is.
                double dangerRatio = (double) elapsedSeconds / MAX_TIME_BETWEEN_SWAPS;
                String color;
                String hazardLevel;
                if (dangerRatio > 0.8) {
                    color = "<red>";
                    hazardLevel = "DANGER";
                } else if (dangerRatio > 0.5) {
                    color = "<yellow>";
                    hazardLevel = "UNSAFE";
                } else {
                    color = "<green>";
                    hazardLevel = "SAFE";
                }

                // Format the message to include the timer and the hazard level
                String timerMessage = String.format("%s%s | Time: %02d:%02d",
                        color, hazardLevel, elapsedSeconds / 60, elapsedSeconds % 60);

                // Send the formatted timer to all contestants.
                for (Player player : contestants) {
                    player.sendActionBar(MiniMessage.miniMessage().deserialize(timerMessage));
                }
            }
        }.runTaskTimer(SwapPlugin, 0L, 20L); // Run every second

        bukkitTasks.add(timerTask);
    }

    private int getRandomSwapTimeWeighted() {
        Random random = new Random();
        float SWAP_PROBABILITY_WEIGHT = 2;
        return (int) Math.round(Math.pow(random.nextDouble(), 1.0 / SWAP_PROBABILITY_WEIGHT) * (MAX_TIME_BETWEEN_SWAPS - 1)) + 1;
    }
}