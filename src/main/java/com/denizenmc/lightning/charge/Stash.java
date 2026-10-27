package com.denizenmc.lightning.charge;

import com.denizenmc.lightning.tag.Faces;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

// MiniMessage cannot preserve custom heads and sprites when converting components to text.
// This class replaces them with temporary markers and restores the original components afterwards.
final class Stash {

    private static final char OPEN = '\uE100';
    private static final char CLOSE = '\uE101';
    private static final Pattern MARKER = Pattern.compile(OPEN + "(\\d+)" + CLOSE);

    private final List<Component> objects = new ArrayList<>();
    private final Component component;

    private Stash(final Component input) {
        this.component = holds(input) ? out(input) : input;
    }

    static @NotNull Stash of(@NotNull final Component input) {
        return new Stash(input);
    }

    @NotNull Component component() {
        return component;
    }

    @NotNull Component restore(@NotNull final Component output) {
        if (objects.isEmpty()) return output;
        return output.replaceText(TextReplacementConfig.builder()
                .match(MARKER)
                .replacement((match, builder) -> {
                    int index = Integer.parseInt(match.group(1));
                    return index < objects.size() ? objects.get(index) : Component.empty();
                })
                .build());
    }

    private Component out(final Component input) {
        List<Component> children = new ArrayList<>(input.children().size());
        for (Component child : input.children()) children.add(out(child));
        if (Faces.isObject(input)) {
            objects.add(input.children(List.of()));
            return Component.text(OPEN + String.valueOf(objects.size() - 1) + CLOSE).children(children);
        }
        return input.children(children);
    }

    private static boolean holds(final Component component) {
        if (Faces.isObject(component)) return true;
        for (Component child : component.children()) {
            if (holds(child)) return true;
        }
        return false;
    }
}