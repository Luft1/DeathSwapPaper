package io.github.Luft1.deathSwap;

import org.bukkit.command.*;
import org.bukkit.entity.*;

public class DeathSwapCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be executed by a player!");
            return true;
        }

        Player player = (Player) sender;
        if (!player.isOp()) {
            player.sendMessage("You don't have permission to use this command!");
            return true;
        }

        switch (command.getName().toLowerCase()) {
            case "deathswapstart":
                SwapManager.get().startRound();
                break;
            case "deathswapend":
                SwapManager.get().endRound();
                break;
        }
        return true;
    }
}