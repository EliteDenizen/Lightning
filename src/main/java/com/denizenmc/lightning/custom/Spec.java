package com.denizenmc.lightning.custom;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

// Defines a command or subcommand from a file in commands/.
record Spec(@NotNull String path, @NotNull String name, @NotNull List<String> aliases, @NotNull String description,
            @Nullable String permission, @NotNull Sender sender, long cooldown, @Nullable Limit limit, @Nullable String usage,
            @NotNull Map<String, String> messages, @NotNull List<Arg> args, @NotNull List<Step> actions,
            @NotNull Map<String, Spec> subcommands) {

    enum Sender {
        ANY,
        PLAYER,
        CONSOLE
    }

    // Limits uses per window; a window of 0 means the limit never resets.
    record Limit(int amount, long window) {
    }

    int required() {
        int required = 0;
        for (Arg arg : args) {
            if (!arg.optional()) required++;
        }
        return required;
    }

    @NotNull String usage(@NotNull final String label) {
        if (usage != null) return usage;
        if (actions.isEmpty() && !subcommands.isEmpty()) return "/" + label + " <" + String.join("|", subcommands.keySet()) + ">";
        StringBuilder built = new StringBuilder("/").append(label);
        for (Arg arg : args) built.append(' ').append(arg.usage());
        return built.toString();
    }
}