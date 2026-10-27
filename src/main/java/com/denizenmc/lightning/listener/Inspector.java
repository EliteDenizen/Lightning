package com.denizenmc.lightning.listener;

import com.denizenmc.lightning.Lightning;
import com.denizenmc.lightning.charge.Charged;
import com.denizenmc.lightning.charge.Surface;
import com.denizenmc.lightning.rewire.Rewires;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Adds a small ⚡ report to inspected messages, showing where they came from and how Lightning handled them.
public final class Inspector {

    private static final String MARKER = "lightning:inspect";
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final Lightning plugin;
    private final Map<UUID, Watch> watching = new ConcurrentHashMap<>();

    public Inspector(@NotNull final Lightning plugin) {
        this.plugin = plugin;
    }

    public boolean toggle(@NotNull final UUID id, @NotNull final Surface surface) {
        Watch current = watching.get(id);
        if (current != null && current.surface == surface) {
            watching.remove(id);
            return false;
        }
        watching.put(id, new Watch(surface));
        return true;
    }

    public void forget(@NotNull final UUID id) {
        watching.remove(id);
    }

    // Action bars update frequently, so report only text changes. Chat reports each new message,
    // while other surfaces report each distinct text once to avoid repeated entries during refreshes.
    boolean wants(@Nullable final Player player, @NotNull final Surface surface, @NotNull final Component original) {
        if (player == null || watching.isEmpty()) return false;
        Watch watch = watching.get(player.getUniqueId());
        if (watch == null || watch.surface != surface) return false;
        String plain = PLAIN.serialize(original);
        if (plain.isBlank()) return false;
        if (surface == Surface.CHAT) {
            if (plain.equals(watch.last)) return false;
            watch.last = plain;
            return true;
        }
        synchronized (watch.seen) {
            if (!watch.seen.add(plain)) return false;
            if (watch.seen.size() > 200) watch.seen.remove(watch.seen.iterator().next());
        }
        return true;
    }

    // Sends a report when the player is inspecting this surface.
    void observe(@Nullable final Player player, @NotNull final Surface surface, @NotNull final Component original, @Nullable final Charged charged) {
        if (!wants(player, surface, original)) return;
        Component report = report(original, charged, surface);
        player.getScheduler().run(plugin, task -> player.sendMessage(report), null);
    }

    // Mark reports so the listener does not inspect or charge its own output.
    static boolean isReport(@NotNull final Component component) {
        return MARKER.equals(component.insertion());
    }

    @NotNull Component report(@NotNull final Component original, @Nullable final Charged charged, @NotNull final Surface surface) {
        String plain = PLAIN.serialize(original);
        List<String> keys = keys(original, new ArrayList<>());
        Rewires.Match rewire = plugin.rewires().match(original, surface);

        TextComponent details = Component.text()
                .append(row("Surface", surface.key())).append(Component.newline())
                .append(row("Translation", keys.isEmpty() ? "none" : String.join(", ", keys))).append(Component.newline())
                .append(row("Rewire", rewire == null ? "none" : rewire.rule())).append(Component.newline())
                .append(row("Charged", charged == null ? "no" : "yes")).append(Component.newline()).append(Component.newline())
                .append(Component.text(plain, NamedTextColor.GRAY)).append(Component.newline()).append(Component.newline())
                .append(Component.text("What Lightning reads:", NamedTextColor.GRAY)).append(Component.newline())
                .append(Component.text(abbreviate(plugin.charger().source(original), 300), NamedTextColor.DARK_GRAY)).append(Component.newline()).append(Component.newline())
                .append(Component.text("Use the buttons to copy.", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC))
                .build();

        String preview = plain.length() > 40 ? plain.substring(0, 40) + "..." : plain;
        TextComponent.Builder report = Component.text()
                .insertion(MARKER)
                .append(Component.text("⚡ " + (surface == Surface.CHAT ? "" : surface.key() + " "), NamedTextColor.YELLOW).hoverEvent(details))
                .append(Component.text(preview + " ", NamedTextColor.DARK_GRAY).hoverEvent(details))
                .append(button("Copy text", plain));
        if (!keys.isEmpty()) report.append(Component.space()).append(button("Copy key", keys.get(0)));
        return report.build();
    }

    // Give each button its own click event for clients that don't inherit clicks from parent components.
    private static Component button(final String label, final String value) {
        return Component.text("[" + label + "]", NamedTextColor.YELLOW)
                .hoverEvent(Component.text("Click to copy: " + (value.length() > 60 ? value.substring(0, 60) + "..." : value), NamedTextColor.GRAY))
                .clickEvent(ClickEvent.copyToClipboard(value));
    }

    // Formats a component for console output, including any translation keys it contains.
    public static @NotNull String describe(@NotNull final Component component) {
        List<String> keys = keys(component, new ArrayList<>());
        String plain = PLAIN.serialize(component).replace("\n", "\\n");
        return "\"" + plain + "\"" + (keys.isEmpty() ? "" : " (translation: " + String.join(", ", keys) + ")");
    }

    private static String abbreviate(final String text, final int length) {
        return text.length() > length ? text.substring(0, length) + "..." : text;
    }

    private static Component row(final String label, final String value) {
        return Component.text(label + ": ", NamedTextColor.GRAY).append(Component.text(value, NamedTextColor.WHITE));
    }

    private static List<String> keys(final Component component, final List<String> out) {
        if (component instanceof TranslatableComponent translatable) out.add(translatable.key());
        for (Component child : component.children()) keys(child, out);
        return out;
    }

    private static final class Watch {

        private final Surface surface;
        private final java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        private volatile String last;

        private Watch(final Surface surface) {
            this.surface = surface;
        }
    }
}