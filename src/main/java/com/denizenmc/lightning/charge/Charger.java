package com.denizenmc.lightning.charge;

import com.denizenmc.lightning.Lightning;
import com.denizenmc.lightning.Settings;
import com.denizenmc.lightning.rewire.Rewires;
import com.denizenmc.lightning.tag.Bolts;
import com.denizenmc.lightning.tag.Glyphs;
import com.denizenmc.lightning.tag.SparkTag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Charger {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final String OWN = "\u2063";
    private static final Object UNTOUCHED = new Object();
    private static final Object DROPPED = new Object();

    private static final TagResolver SAFE_STANDARD = TagResolver.resolver(
            StandardTags.color(),
            StandardTags.decorations(),
            StandardTags.gradient(),
            StandardTags.rainbow(),
            StandardTags.transition(),
            StandardTags.reset(),
            StandardTags.font(),
            StandardTags.newline(),
            StandardTags.keybind(),
            StandardTags.translatable()
    );

    private final Lightning plugin;
    private final Map<String, Component> parsed = new ConcurrentHashMap<>();
    private final Map<Input, Object> known = new ConcurrentHashMap<>();

    private volatile TagResolver full = TagResolver.standard();
    private volatile TagResolver safe = SAFE_STANDARD;
    private volatile boolean placeholders;

    public Charger(@NotNull final Lightning plugin) {
        this.plugin = plugin;
    }

    public void rebuild() {
        TagResolver ours = TagResolver.resolver(
                Glyphs.resolver(plugin.settings()),
                plugin.faces().resolver(),
                SparkTag.resolver(plugin.sparks())
        );

        full = TagResolver.resolver(TagResolver.standard(), ours, Bolts.resolver());
        safe = TagResolver.resolver(SAFE_STANDARD, ours);
        placeholders = plugin.settings().placeholderApi()
                && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

        Placeholders.enable(placeholders);
        forget();
    }

    public void forget() {
        parsed.clear();
        known.clear();
    }

    /**
     * Charges text another plugin is about to display.
     *
     * @return the charged result, or {@code null} when the text should pass through unchanged
     */
    public @Nullable Charged charge(
            @Nullable final Component input,
            @Nullable final Player viewer,
            @NotNull final Surface surface
    ) {
        // Lightning's own output is already charged and must never be processed twice.
        if (input == null || isOwn(input)) return null;

        // Plugins often resend identical components, so cache their complete results.
        Input key = new Input(surface.safe(), surface.bolts(), input);
        Object answer = known.get(key);

        if (answer == UNTOUCHED) return null;
        if (answer == DROPPED) return Charged.dropped();
        if (answer instanceof Component component) return Charged.inPlace(component);

        Outcome outcome = work(input, viewer, surface);
        if (outcome.lasting()) {
            remember(
                    known,
                    key,
                    outcome.charged() == null
                            ? UNTOUCHED
                            : outcome.charged().silent()
                            ? DROPPED
                            : outcome.charged().component()
            );
        }

        return outcome.charged();
    }

    /**
     * Charges and sends one of Lightning's own messages, including bolt effects.
     * Used by custom commands.
     */
    public void send(
            @NotNull final CommandSender to,
            @NotNull final String text,
            final boolean placeholders
    ) {
        Player viewer = to instanceof Player player ? player : null;
        String resolved = placeholders ? Placeholders.apply(viewer, text) : text;

        // Cache parsed output when it doesn't depend on the viewer or changing state.
        String key = "o" + resolved;
        Component cached = parsed.get(key);

        if (cached != null) {
            if (!blank(cached)) to.sendMessage(own(cached));
            return;
        }

        resolved = Centering.apply(
                resolved,
                plugin.settings().centerWidth(),
                line -> measure(line, viewer, full)
        );

        ChargeState state = ChargeState.open(Surface.CHAT, viewer);
        Component output;

        try {
            output = deserialize(resolved, viewer, full);
        } catch (RuntimeException e) {
            output = Component.text(text);
        } finally {
            ChargeState.close();
        }

        if (viewer != null) state.effects().forEach(effect -> effect.accept(viewer));
        if (state.silent()) return;

        if (!state.live() && !state.personal() && state.delivery() == Charged.Delivery.IN_PLACE) {
            remember(parsed, key, output);
        }

        switch (state.delivery()) {
            case ACTIONBAR -> to.sendActionBar(output);
            case TITLE -> to.showTitle(Title.title(output, Component.empty()));

            // Bolt-only lines have no visible chat content to send.
            case IN_PLACE -> {
                if (!blank(output)) to.sendMessage(own(output));
            }
        }
    }

    // Used by /lightning check to distinguish registered tags from typos.
    public boolean knows(@NotNull final String tag) {
        return full.has(tag);
    }

    // Marks finished output so the listener won't charge it again, even when it intentionally contains tag text.
    public static @NotNull Component own(@NotNull final Component component) {
        return Component.text().insertion(OWN).append(component).build();
    }

    private static boolean blank(final Component component) {
        if (component instanceof TextComponent text && !text.content().isEmpty()) return false;
        if (!(component instanceof TextComponent)) return false;

        for (Component child : component.children()) {
            if (!blank(child)) return false;
        }

        return true;
    }

    // The listener skips components carrying this marker because Lightning has already processed them.
    public static boolean isOwn(@NotNull final Component component) {
        return OWN.equals(component.insertion());
    }

    // Parses Lightning's own text, such as command feedback. Bolt effects remain disabled here.
    public @NotNull Component render(@NotNull final String text, @Nullable final Player viewer) {
        return viewer == null
                ? MINI.deserialize(text, full)
                : MINI.deserialize(text, viewer, full);
    }

    private Outcome work(final Component input, final Player viewer, final Surface surface) {
        Rewires.Match rewired = plugin.rewires().match(input, surface, viewer);

        if (rewired != null) {
            if (rewired.drop()) return new Outcome(Charged.dropped(), true);
            // Placeholders were already resolved for this viewer, so the result must not be shared with others.
            Outcome outcome = parse(rewired.text(), viewer, surface, false);
            return rewired.placeholders() ? new Outcome(outcome.charged(), false) : outcome;
        }

        // Apply replacement and pattern rules before processing the resulting component's tags.
        Rewires.Patched patched = plugin.rewires().patch(input, surface, viewer);
        Outcome outcome = tags(patched.component(), viewer, surface);

        if (!patched.changed()) return outcome;
        if (outcome.charged() == null) {
            return new Outcome(Charged.inPlace(patched.component()), !patched.live());
        }

        return new Outcome(outcome.charged(), outcome.lasting() && !patched.live());
    }

    private Outcome tags(final Component input, final Player viewer, final Surface surface) {
        if (!Sense.worthLooking(input, placeholders)) return new Outcome(null, true);

        Settings settings = plugin.settings();
        // Some plugins send text as many small pieces that share the same style, which can split a tag
        // like <spark:ess.text> across pieces. Merging identical neighbours first keeps such tags whole.
        // Added this patch after encountering an issue in a plugin that split a single line into
        // 20+ components, all sharing the same style.
        Stash stash = Stash.of(input.compact());
        String serialized = MINI.serialize(stash.component());

        if (settings.markerOnly() && !serialized.contains("\\<charge>")) {
            return new Outcome(null, true);
        }

        boolean tagged = Sense.hasWrittenTag(serialized, surface.safe() ? safe : full);
        boolean resolvePlaceholders = placeholders && Sense.hasPlaceholder(serialized);

        if (!tagged && !resolvePlaceholders) return new Outcome(null, true);

        String unescaped = Sense.unescape(serialized);
        Guard.Defused defused = plugin.guard().defuse(unescaped);

        Outcome outcome = restore(
                parse(defused.text(), viewer, surface, resolvePlaceholders && !defused.blocksPlaceholders()),
                stash
        );

        outcome = again(outcome, viewer, surface);

        // Input affected by Guard depends on recent player messages and must not be cached.
        return defused.text().equals(unescaped)
                ? outcome
                : new Outcome(outcome.charged(), false);
    }

    // A plugin may split MiniMessage tags into text fragments during its own parsing.
    // A second pass catches any remaining Lightning tags and applies Guard again.
    private Outcome again(final Outcome first, final Player viewer, final Surface surface) {
        Charged charged = first.charged();

        if (charged == null
                || charged.silent()
                || charged.delivery() != Charged.Delivery.IN_PLACE) {
            return first;
        }

        Component output = charged.component();
        if (!Sense.worthLooking(output, false)) return first;

        Stash stash = Stash.of(output.compact());
        String serialized = MINI.serialize(stash.component());

        if (!Sense.hasWrittenTag(serialized, surface.safe() ? safe : full)) return first;

        Outcome second = restore(
                parse(plugin.guard().defuse(Sense.unescape(serialized)).text(), viewer, surface, false),
                stash
        );

        if (second.charged() == null) return first;

        List<java.util.function.Consumer<Player>> effects = new java.util.ArrayList<>(charged.effects());
        effects.addAll(second.charged().effects());

        Charged merged = new Charged(
                second.charged().component(),
                second.charged().delivery(),
                second.charged().silent(),
                charged.ticking() || second.charged().ticking(),
                effects
        );

        return new Outcome(merged, first.lasting() && second.lasting());
    }

    private Outcome parse(
            final String source,
            final Player viewer,
            final Surface surface,
            final boolean resolvePlaceholders
    ) {
        TagResolver resolver = surface.safe() ? safe : full;
        String text = resolvePlaceholders ? Placeholders.apply(viewer, source) : source;

        // Resolve placeholders before building the cache key so identical results can reuse parsed output.
        String key = (surface.safe() ? "s" : "f") + text;
        Component cached = parsed.get(key);

        if (cached != null) return new Outcome(Charged.inPlace(cached), !resolvePlaceholders);

        if (viewer != null) {
            Component mine = parsed.get(key + '\u0000' + viewer.getUniqueId());
            if (mine != null) return new Outcome(Charged.inPlace(mine), false);
        }

        text = Centering.apply(
                text,
                plugin.settings().centerWidth(),
                line -> measure(line, viewer, resolver)
        );

        ChargeState state = ChargeState.open(surface, viewer);
        Component output;

        try {
            output = deserialize(text, viewer, resolver);
        } catch (RuntimeException e) {
            plugin.debug("Left a " + surface.key() + " line alone because it didn't parse: " + e.getMessage());
            return new Outcome(null, false);
        } finally {
            ChargeState.close();
        }

        if (surface == Surface.ITEMS && plugin.settings().plainItemNames()) {
            output = output.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        }

        if (!state.live()) {
            if (!state.personal()) {
                remember(parsed, key, output);
            } else if (viewer != null) {
                remember(parsed, key + '\u0000' + viewer.getUniqueId(), output);
            }
        }

        Charged charged = new Charged(
                output,
                state.delivery(),
                state.silent(),
                state.ticking(),
                state.effects()
        );

        return new Outcome(charged, !resolvePlaceholders && !state.live() && !state.personal());
    }

    private static Outcome restore(final Outcome outcome, final Stash stash) {
        Charged charged = outcome.charged();
        if (charged == null) return outcome;

        return new Outcome(
                new Charged(
                        stash.restore(charged.component()),
                        charged.delivery(),
                        charged.silent(),
                        charged.ticking(),
                        charged.effects()
                ),
                outcome.lasting()
        );
    }

    // Returns Lightning's readable source representation, with extracted components restored for inspection.
    public @NotNull String source(@NotNull final Component component) {
        return MINI.serialize(Stash.of(component).component());
    }

    // Measure centered text without allowing bolt effects to fire during measurement.
    private Component measure(final String line, final Player viewer, final TagResolver resolver) {
        ChargeState.open(Surface.MOTD, viewer);

        try {
            return deserialize(line, viewer, resolver);
        } catch (RuntimeException e) {
            return Component.text(line);
        } finally {
            ChargeState.close();
        }
    }

    private static Component deserialize(
            final String text,
            final Player viewer,
            final TagResolver resolver
    ) {
        return viewer == null
                ? MINI.deserialize(text, resolver)
                : MINI.deserialize(text, viewer, resolver);
    }

    private <K, V> void remember(final Map<K, V> cache, final K key, final V value) {
        int limit = plugin.settings().cacheSize();
        if (limit == 0) return;
        if (cache.size() >= limit) cache.clear();
        cache.put(key, value);
    }

    // Surfaces with identical tag rules can share cached results.
    private record Input(boolean safe, boolean bolts, Component component) {
    }

    // Indicates whether a result remains valid until the next reload.
    private record Outcome(@Nullable Charged charged, boolean lasting) {
    }
}