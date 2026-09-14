package dev.elitedenizen.lightning.util.text;

import dev.elitedenizen.lightning.Lightning;
import dev.elitedenizen.lightning.command.CustomCommand;
import dev.elitedenizen.lightning.util.scheduling.TickConverter;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

public final class LightningExpansion extends PlaceholderExpansion {

    private final Lightning plugin;

    public LightningExpansion(Lightning plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "lightning";
    }

    @Override
    public String getAuthor() {
        return "EliteDenizen";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(org.bukkit.entity.Player player, String params) {
        if (player == null) return "";

        if (params.startsWith("uses_")) {
            String commandName = params.substring("uses_".length());
            return String.valueOf(plugin.getUsageTracker().getUseCount(player.getUniqueId(), commandName));
        }

        if (params.startsWith("cooldown_")) {
            String commandName = params.substring("cooldown_".length());
            CustomCommand cmd = plugin.findCommand(commandName);
            if (cmd == null) return "0";
            long remaining = plugin.getUsageTracker().getCooldownRemaining(player.getUniqueId(), cmd);
            return TickConverter.format(remaining);
        }

        if (params.startsWith("smallcaps_")) {
            return ColorFormatter.toSmallCaps(params.substring("smallcaps_".length()));
        }

        if (params.startsWith("format_")) {
            return ColorFormatter.colorize(params.substring("format_".length()));
        }

        return null;
    }
}
