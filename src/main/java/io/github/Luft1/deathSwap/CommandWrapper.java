package io.github.Luft1.deathSwap;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CommandWrapper extends Command {

    private final DeathSwapCommand executor;

    public CommandWrapper(@NotNull String name, @NotNull DeathSwapCommand executor) {
        super(name);
        this.executor = executor;
        this.setPermission("deathswap.admin"); // Set permission programmatically
        this.setAliases(List.of("ds")); // Set aliases programmatically
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        return executor.onCommand(sender, this, commandLabel, args);
    }

    @NotNull
    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException {
        return executor.onTabComplete(sender, this, alias, args);
    }
}