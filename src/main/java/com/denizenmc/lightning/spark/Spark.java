package com.denizenmc.lightning.spark;

import com.denizenmc.lightning.charge.ChargeState;
import com.denizenmc.lightning.charge.Placeholders;
import net.kyori.adventure.text.minimessage.tag.Tag;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public sealed interface Spark permits Spark.Text, Spark.Color, Spark.Formula, Spark.Scale, Spark.Cycle, Spark.Pick, Spark.Translated, Spark.Ranked {

    @NotNull Tag render(@NotNull List<String> values, @Nullable ChargeState state);

    record Text(@NotNull String text) implements Spark {
        @Override
        public @NotNull Tag render(@NotNull final List<String> values, @Nullable final ChargeState state) {
            return Tag.preProcessParsed(prepare(text, values, state));
        }
    }

    record Color(@NotNull Tag style) implements Spark {
        @Override
        public @NotNull Tag render(@NotNull final List<String> values, @Nullable final ChargeState state) {
            return style;
        }
    }

    record Formula(@NotNull String expression, @NotNull String format) implements Spark {
        @Override
        public @NotNull Tag render(@NotNull final List<String> values, @Nullable final ChargeState state) {
            try {
                return Tag.preProcessParsed(shape(Expression.evaluate(number(prepare(expression, values, state))), format));
            } catch (RuntimeException e) {
                return Tag.preProcessParsed("?");
            }
        }
    }

    // Selects text based on a number's value. Steps are checked in order, and the first matching threshold wins.
    record Scale(@NotNull String value, @NotNull List<Step> steps, @NotNull String otherwise, @NotNull String format) implements Spark {
        @Override
        public @NotNull Tag render(@NotNull final List<String> values, @Nullable final ChargeState state) {
            double number;
            try {
                number = Expression.evaluate(number(prepare(value, values, state)));
            } catch (RuntimeException e) {
                return Tag.preProcessParsed("?");
            }
            String text = otherwise;
            for (Step step : steps) {
                if (number < step.below()) {
                    text = step.text();
                    break;
                }
            }
            return Tag.preProcessParsed(prepare(text.replace("{value}", shape(number, format)), values, state));
        }
    }

    record Cycle(@NotNull List<String> frames, long every) implements Spark {
        @Override
        public @NotNull Tag render(@NotNull final List<String> values, @Nullable final ChargeState state) {
            if (state != null) state.markTicking();
            int frame = (int) ((System.currentTimeMillis() / every) % frames.size());
            return Tag.preProcessParsed(prepare(frames.get(frame), values, state));
        }
    }

    record Pick(@NotNull List<String> options) implements Spark {
        @Override
        public @NotNull Tag render(@NotNull final List<String> values, @Nullable final ChargeState state) {
            if (state != null) state.markLive();
            return Tag.preProcessParsed(prepare(options.get(ThreadLocalRandom.current().nextInt(options.size())), values, state));
        }
    }

    // Selects text for the viewer's game language, then falls back to the language code and the default entry.
    record Translated(@NotNull Map<String, String> texts) implements Spark {
        @Override
        public @NotNull Tag render(@NotNull final List<String> values, @Nullable final ChargeState state) {
            if (state != null) state.markPersonal();
            Player viewer = state == null ? null : state.viewer();
            String text = null;
            if (viewer != null) {
                String locale = viewer.locale().toString().toLowerCase(Locale.ROOT);
                text = texts.get(locale);
                int split = locale.indexOf('_');
                if (text == null && split > 0) text = texts.get(locale.substring(0, split));
            }
            if (text == null) text = texts.getOrDefault("default", "");
            return Tag.preProcessParsed(prepare(text, values, state));
        }
    }

    // Selects the first tier matching the viewer's permissions. Results are not cached so permission changes take effect immediately.
    record Ranked(@NotNull List<Tier> tiers, @NotNull String fallback) implements Spark {
        @Override
        public @NotNull Tag render(@NotNull final List<String> values, @Nullable final ChargeState state) {
            if (state != null) state.markLive();
            Player viewer = state == null ? null : state.viewer();
            String text = fallback;
            if (viewer != null) {
                for (Tier tier : tiers) {
                    if (viewer.hasPermission(tier.permission())) {
                        text = tier.text();
                        break;
                    }
                }
            }
            return Tag.preProcessParsed(prepare(text, values, state));
        }
    }

    record Tier(@NotNull String permission, @NotNull String text) {
    }

    record Step(double below, @NotNull String text) {
    }

    // Replaces numbered arguments, then resolves any PlaceholderAPI placeholders in the selected text.
    private static String prepare(final String text, final List<String> values, final @Nullable ChargeState state) {
        String result = text;
        if (!values.isEmpty() && result.indexOf('{') >= 0) {
            for (int i = 0; i < values.size(); i++) result = result.replace("{" + i + "}", values.get(i));
        }
        if (result.indexOf('%') >= 0 && Placeholders.enabled()) {
            if (state != null) state.markLive();
            result = Placeholders.apply(state == null ? null : state.viewer(), result);
        }
        return result;
    }

    // Removes common number separators so expressions can parse formatted placeholder values.
    private static String number(final String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != ',' && c != ' ' && c != '_') out.append(c);
        }
        return out.toString();
    }

    private static String shape(final double value, final String format) {
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "compact" -> compact(value);
            case "duration" -> duration((long) value);
            default -> new DecimalFormat(format, DecimalFormatSymbols.getInstance(Locale.ROOT)).format(value);
        };
    }

    private static String compact(final double value) {
        double size = Math.abs(value);
        String[] units = {"", "K", "M", "B", "T"};
        int unit = 0;
        while (size >= 1000 && unit < units.length - 1) {
            size /= 1000;
            unit++;
        }
        String number = new DecimalFormat(unit == 0 ? "#,##0.##" : "0.#", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(size);
        return (value < 0 ? "-" : "") + number + units[unit];
    }

    // Formats seconds using up to two units, such as 2h 15m or 3d 4h.
    private static String duration(final long seconds) {
        long[] sizes = {86_400, 3_600, 60, 1};
        String[] names = {"d", "h", "m", "s"};
        StringBuilder out = new StringBuilder();
        long left = Math.max(0, seconds);
        int shown = 0;
        for (int i = 0; i < sizes.length && shown < 2; i++) {
            long amount = left / sizes[i];
            left %= sizes[i];
            if (amount == 0 && out.isEmpty()) continue;
            if (amount == 0) break;
            if (!out.isEmpty()) out.append(' ');
            out.append(amount).append(names[i]);
            shown++;
        }
        return out.isEmpty() ? "0s" : out.toString();
    }
}
