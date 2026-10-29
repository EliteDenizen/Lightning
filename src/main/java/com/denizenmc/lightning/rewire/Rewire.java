package com.denizenmc.lightning.rewire;

import com.denizenmc.lightning.charge.Surface;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Stores a rewire rule and handles matching messages against its configured criteria.
record Rewire(@NotNull String name, @NotNull Kind kind, @NotNull String pattern, @Nullable Pattern regex, int arity,
              @Nullable String to, @NotNull Set<Surface> surfaces) {

    enum Kind {
        TEXT,
        CONTAINS,
        REGEX,
        LANG,
        // These rules replace only the matched section, preserving the surrounding text and formatting.
        REPLACE,
        PATTERN
    }

    boolean partial() {
        return kind == Kind.REPLACE || kind == Kind.PATTERN;
    }

    boolean drop() {
        return to == null;
    }

    // Returns {0}, {1}, and subsequent values for a match. Matched text is escaped to prevent it from being parsed as MiniMessage tags.
    @Nullable List<String> test(@NotNull final String plain) {
        switch (kind) {
            case TEXT -> {
                return plain.strip().equalsIgnoreCase(pattern) ? List.of(escape(plain)) : null;
            }
            case CONTAINS -> {
                return plain.toLowerCase(Locale.ROOT).contains(pattern) ? List.of(escape(plain)) : null;
            }
            case REGEX -> {
                Matcher matcher = regex.matcher(new Bounded(plain));
                if (!matcher.find()) return null;
                List<String> values = new ArrayList<>(matcher.groupCount() + 1);
                for (int group = 0; group <= matcher.groupCount(); group++) {
                    String value = matcher.group(group);
                    values.add(value == null ? "" : escape(value));
                }
                return values;
            }
            default -> {
                return null;
            }
        }
    }

    // Extracts translation arguments from a plain English message for use as {1}, {2}, and subsequent values.
    @Nullable List<String> translated(@NotNull final String plain) {
        if (regex == null) return null;
        Matcher matcher = regex.matcher(new Bounded(plain));
        if (!matcher.find()) return null;
        List<String> values = new ArrayList<>(arity + 1);
        values.add(escape(matcher.group()));
        for (int argument = 0; argument < arity; argument++) {
            String value;
            try {
                value = matcher.group("a" + argument);
            } catch (IllegalArgumentException e) {
                value = null;
            }
            values.add(value == null ? "" : escape(value));
        }
        return values;
    }

    private static String escape(final String text) {
        return text.replace("<", "\\<");
    }
}