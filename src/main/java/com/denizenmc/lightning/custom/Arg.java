package com.denizenmc.lightning.custom;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Defines a command argument, for example: { name: amount, type: number, min: 1, max: 64, default: 1 }.
record Arg(@NotNull String name, @NotNull Kind kind, double min, double max, @NotNull List<String> options,
           boolean optional, @Nullable String fallback) {

    enum Kind {
        PLAYER,
        NUMBER,
        DECIMAL,
        BOOLEAN,
        WORD,
        TEXT,
        CHOICE,
        WORLD
    }

    @NotNull ArgumentType<?> type() {
        return switch (kind) {
            case PLAYER -> ArgumentTypes.player();
            case NUMBER -> IntegerArgumentType.integer((int) Math.max(Integer.MIN_VALUE, min), (int) Math.min(Integer.MAX_VALUE, max));
            case DECIMAL -> DoubleArgumentType.doubleArg(min, max);
            case BOOLEAN -> BoolArgumentType.bool();
            case TEXT -> StringArgumentType.greedyString();
            case WORLD -> ArgumentTypes.world();
            case WORD, CHOICE -> StringArgumentType.word();
        };
    }

    @NotNull String usage() {
        return optional ? "[" + name + "]" : "<" + name + ">";
    }

    // Returns null if the argument definition is invalid, adding a reason to problems.
    static @Nullable Arg parse(@NotNull final Map<?, ?> written, @NotNull final List<String> problems) {
        // Supports the shorthand "- amount: number", where the key is the argument name.
        Map<?, ?> entry = written;
        if (!written.containsKey("name") && written.size() == 1) {
            Map.Entry<?, ?> only = written.entrySet().iterator().next();
            Map<String, Object> expanded = new java.util.HashMap<>();
            expanded.put("name", only.getKey());
            if (only.getValue() instanceof List<?> options) {
                expanded.put("type", "choice");
                expanded.put("options", options);
            } else {
                expanded.put("type", only.getValue());
            }
            entry = expanded;
        }
        Object rawName = entry.get("name");
        if (rawName == null) {
            problems.add("an argument has no name");
            return null;
        }
        String name = rawName.toString().trim().toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9_]+")) {
            problems.add("argument '" + name + "' can only use letters, numbers and underscores");
            return null;
        }

        Kind kind;
        try {
            kind = Kind.valueOf(String.valueOf(entry.containsKey("type") ? entry.get("type") : "word").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            problems.add("argument '" + name + "' has a type Lightning doesn't know: " + entry.get("type"));
            return null;
        }

        List<String> options = new ArrayList<>();
        if (entry.get("options") instanceof List<?> list) {
            for (Object option : list) options.add(String.valueOf(option).trim().toLowerCase(Locale.ROOT));
        }
        if (kind == Kind.CHOICE && options.isEmpty()) {
            problems.add("argument '" + name + "' is a choice but has no options");
            return null;
        }

        double min = number(entry.get("min"), kind == Kind.NUMBER ? Integer.MIN_VALUE : -Double.MAX_VALUE);
        double max = number(entry.get("max"), kind == Kind.NUMBER ? Integer.MAX_VALUE : Double.MAX_VALUE);
        Object fallback = entry.get("default");
        boolean optional = fallback != null || Boolean.TRUE.equals(entry.get("optional"));
        return new Arg(name, kind, min, max, List.copyOf(options), optional, fallback == null ? null : fallback.toString());
    }

    private static double number(final @Nullable Object value, final double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        if (value == null) return fallback;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}