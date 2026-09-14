package dev.elitedenizen.lightning.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

final class CommandHandle extends Command {

    private final CommandHandler handler;

    CommandHandle(String name, CommandHandler handler) {
        super(name);
        this.handler = handler;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        handler.onCommand(sender, label, args);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (!handler.getDefinition().allowsTabComplete()) return Collections.emptyList();
        return handler.onTabComplete(sender, alias, args);
    }
}
