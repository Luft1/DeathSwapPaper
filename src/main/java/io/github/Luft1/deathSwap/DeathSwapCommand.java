package io.github.Luft1.deathSwap;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class DeathSwapCommand implements CommandExecutor, TabCompleter {

    // Store the SwapManager instance
    private final SwapManager swapManager;

    // Constructor to receive the SwapManager instance
    public DeathSwapCommand(SwapManager swapManager) {
        this.swapManager = swapManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("deathswap.admin")) {
            sender.sendMessage("You don't have permission to use this command!");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("Usage: /deathswap <start|end>");
            return false;
        }

        switch (args[0].toLowerCase()) {
            case "start":
                // Use the instance variable
                swapManager.startRound();
                break;
            case "end":
                // Use the instance variable
                swapManager.endRound();
                break;
            default:
                sender.sendMessage("Unknown subcommand. Usage: /deathswap <start|end>");
                break;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>();
            subcommands.add("start");
            subcommands.add("end");
            return subcommands;
        }
        return new ArrayList<>();
    }
}

