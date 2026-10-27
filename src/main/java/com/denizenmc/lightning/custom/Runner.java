package com.denizenmc.lightning.custom;

import com.denizenmc.lightning.Durations;
import com.denizenmc.lightning.Lightning;
import com.denizenmc.lightning.charge.Placeholders;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

// Executes an action list in order, resuming delayed steps on the appropriate thread, including on Folia.
final class Runner {

    private final Lightning plugin;
    private final Ledger ledger;

    Runner(@NotNull final Lightning plugin, @NotNull final Ledger ledger) {
        this.plugin = plugin;
        this.ledger = ledger;
    }

    void run(@NotNull final Spec spec, @NotNull final CommandSender sender, @NotNull final Map<String, String> values) {
        Player player = sender instanceof Player online ? online : null;
        if (spec.sender() == Spec.Sender.PLAYER && player == null) {
            notify(sender, spec, "players");
            return;
        }
        if (spec.sender() == Spec.Sender.CONSOLE && player != null) {
            notify(sender, spec, "console");
            return;
        }

        if (player != null && !player.hasPermission("lightning.bypass")) {
            long left = ledger.cooldownLeft(player.getUniqueId(), spec);
            if (left > 0) {
                notify(sender, spec, "cooldown", Placeholder.unparsed("time", Durations.format(left)));
                return;
            }
            if (ledger.limitReached(player.getUniqueId(), spec)) {
                notify(sender, spec, "limit");
                return;
            }
        }
        if (player != null) ledger.record(player.getUniqueId(), spec);

        values.put("player", sender.getName());
        if (player != null) {
            values.put("world", player.getWorld().getName());
            values.put("uuid", player.getUniqueId().toString());
        }
        execute(spec.actions(), 0, new Context(sender, player, values), () -> {
        });
    }

    // Messages defined for a command take precedence over those in config.yml.
    void notify(@NotNull final CommandSender sender, @NotNull final Spec spec, @NotNull final String key, @NotNull final TagResolver... values) {
        String custom = spec.messages().get(key);
        sender.sendMessage(custom == null ? plugin.message(key, values) : plugin.compose(custom, values));
    }

    private void execute(final List<Step> steps, final int from, final Context context, final Runnable done) {
        for (int i = from; i < steps.size(); i++) {
            Step step = steps.get(i);
            int next = i + 1;
            switch (step) {
                case Step.Stop stop -> {
                    return;
                }
                case Step.Wait wait -> {
                    later(context, wait.millis(), () -> execute(steps, next, context, done));
                    return;
                }
                case Step.Branch branch -> {
                    boolean passed = Condition.test(prepare(branch.condition(), context, context.player, false), context.sender);
                    execute(passed ? branch.then() : branch.otherwise(), 0, context, () -> execute(steps, next, context, done));
                    return;
                }
                case Step.Pick pick -> {
                    List<Step> chosen = pick.options().get(ThreadLocalRandom.current().nextInt(pick.options().size()));
                    execute(chosen, 0, context, () -> execute(steps, next, context, done));
                    return;
                }
                case Step.Chance chance -> {
                    boolean hit = ThreadLocalRandom.current().nextDouble(100) < chance.percent();
                    execute(hit ? chance.then() : chance.otherwise(), 0, context, () -> execute(steps, next, context, done));
                    return;
                }
                default -> act(step, context);
            }
        }
        done.run();
    }

    private void act(final Step step, final Context context) {
        switch (step) {
            case Step.Say say -> plugin.charger().send(context.sender, prepare(say.text(), context, context.player, true), false);
            case Step.Console console -> dispatch(command(prepare(console.command(), context, context.player, false)));
            case Step.Perform perform -> {
                if (context.player != null) context.player.performCommand(command(prepare(perform.command(), context, context.player, false)));
            }
            case Step.Broadcast broadcast -> {
                for (Player reader : Bukkit.getOnlinePlayers()) {
                    if (broadcast.permission() != null && !reader.hasPermission(broadcast.permission())) continue;
                    plugin.charger().send(reader, prepare(broadcast.text(), context, reader, true), false);
                }
            }
            default -> {
            }
        }
    }

    // Resolve configured placeholders before inserting argument values. Player input is never passed to
    // PlaceholderAPI, interpreted as a MiniMessage tag, or allowed to introduce command selectors such as @a.
    private String prepare(final String template, final Context context, final @Nullable Player reader, final boolean message) {
        String text = Placeholders.apply(reader, template);

        // Resolve placeholders in a single pass so inserted values are never processed again.
        StringBuilder out = new StringBuilder(text.length() + 32);
        int at = 0;
        while (at < text.length()) {
            int open = text.indexOf('{', at);
            int close = open < 0 ? -1 : text.indexOf('}', open);
            if (close < 0) {
                out.append(text, at, text.length());
                break;
            }
            String value = context.values.get(text.substring(open + 1, close));
            if (value == null) {
                out.append(text, at, open + 1);
                at = open + 1;
                continue;
            }
            out.append(text, at, open).append(message ? value.replace("\\", "\\\\").replace("<", "\\<") : value.replace("@", ""));
            at = close + 1;
        }
        return out.toString();
    }

    private static String command(final String text) {
        return text.startsWith("/") ? text.substring(1) : text;
    }

    private void dispatch(final String command) {
        if (Bukkit.isGlobalTickThread()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        else Bukkit.getGlobalRegionScheduler().execute(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
    }

    private void later(final Context context, final long millis, final Runnable task) {
        long ticks = Math.max(1, millis / 50);
        if (context.player != null) context.player.getScheduler().runDelayed(plugin, scheduled -> task.run(), null, ticks);
        else Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduled -> task.run(), ticks);
    }

    private record Context(@NotNull CommandSender sender, @Nullable Player player, @NotNull Map<String, String> values) {
    }
}