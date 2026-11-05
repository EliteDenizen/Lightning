package com.denizenmc.lightning.charge;

import org.jetbrains.annotations.NotNull;

// Escapes text so MiniMessage shows it exactly as written.
// MiniMessage treats a backslash as an escape character, so escaping only '<' is not enough:
// "\<click:...>" would become "\\<click:...>", which MiniMessage reads as a literal backslash followed by a live tag.
public final class Literal {

    private Literal() {
    }

    public static @NotNull String escape(@NotNull final String text) {
        if (text.indexOf('\\') < 0 && text.indexOf('<') < 0) return text;
        return text.replace("\\", "\\\\").replace("<", "\\<");
    }
}
