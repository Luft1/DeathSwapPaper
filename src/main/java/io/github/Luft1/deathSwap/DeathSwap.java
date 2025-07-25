package io.github.Luft1.deathSwap;

import org.bukkit.command.CommandMap;
import org.bukkit.plugin.java.JavaPlugin;

public final class DeathSwap extends JavaPlugin {

    private SafeLocationFinder safeLocationFinder;

    @Override
    public void onEnable() {
        getLogger().info("death swap plugin starting");

        // Create, store, and initialize the location finder service.
        safeLocationFinder = new SafeLocationFinder(this);
        safeLocationFinder.initialize();

        // Pass the single instance to the SwapManager.
        SwapManager swapManager = new SwapManager(this, safeLocationFinder);

        DeathSwapCommand deathSwapCommand = new DeathSwapCommand(swapManager);
        CommandMap commandMap = getServer().getCommandMap();
        commandMap.register("deathswap", new CommandWrapper("deathswap", deathSwapCommand));
        getServer().getPluginManager().registerEvents(swapManager, this);
        getLogger().info("Events registered!");
    }

    @Override
    public void onDisable() {
        getLogger().info("death swap plugin terminating");
        if (safeLocationFinder != null) {
            safeLocationFinder.shutdown();
        }
    }
}
