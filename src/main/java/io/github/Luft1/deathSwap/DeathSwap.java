package io.github.Luft1.deathSwap;

import org.bukkit.*;
import org.bukkit.plugin.java.*;

public final class DeathSwap extends JavaPlugin {

    @Override
    public void onEnable() {
        // Initialize SwapManager first
        Bukkit.getLogger().info("death swap plugin starting");
        SwapManager.init(this);

        // Then use it
        DeathSwapCommand commandExecutor = new DeathSwapCommand();
        getCommand("DeathSwapStart").setExecutor(commandExecutor);
        getCommand("DeathSwapEnd").setExecutor(commandExecutor);

        getLogger().info("Registering events for SwapManager...");
        getServer().getPluginManager().registerEvents(SwapManager.get(), this);
        getLogger().info("Events registered!");

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        Bukkit.getLogger().info("death swap plugin terminating");
    }
}