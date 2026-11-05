package com.denizenmc.lightning.tag;

import com.denizenmc.lightning.charge.ChargeState;
import com.denizenmc.lightning.charge.Charged;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.tag.Inserting;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

// Tags that do something beyond the text. They only fire on chat, anywhere else they quietly vanish.
public final class Bolts {

    private static final Tag NOTHING = Tag.selfClosingInserting(Component.empty());

    private Bolts() {
    }

    public static @NotNull TagResolver resolver() {
        return TagResolver.resolver(
                TagResolver.resolver("actionbar", Bolts::actionbar),
                TagResolver.resolver("title", Bolts::title),
                TagResolver.resolver("sound", Bolts::sound),
                TagResolver.resolver("silent", (args, context) -> {
                    ChargeState state = active();
                    if (state != null) state.silence();
                    return NOTHING;
                }));
    }

    private static Tag actionbar(final ArgumentQueue args, final Context context) {
        ChargeState state = active();
        if (state == null) return NOTHING;
        if (!args.hasNext()) {
            state.deliver(Charged.Delivery.ACTIONBAR);
            return NOTHING;
        }
        Component text = context.deserialize(args.pop().value());
        return once(state, player -> player.sendActionBar(text));
    }

    private static Tag title(final ArgumentQueue args, final Context context) {
        ChargeState state = active();
        if (state == null) return NOTHING;
        if (!args.hasNext()) {
            state.deliver(Charged.Delivery.TITLE);
            return NOTHING;
        }
        Component main = context.deserialize(args.pop().value());
        Component sub = args.hasNext() ? context.deserialize(args.pop().value()) : Component.empty();
        Title.Times times = Title.Times.times(ticks(args, 10), ticks(args, 70), ticks(args, 20));
        Title title = Title.title(main, sub, times);
        return once(state, player -> player.showTitle(title));
    }

    private static Tag sound(final ArgumentQueue args, final Context context) {
        ChargeState state = active();
        if (state == null || !args.hasNext()) return NOTHING;
        try {
            Key key = Key.key(args.pop().lowerValue());
            float volume = args.hasNext() ? Float.parseFloat(args.pop().value()) : 1F;
            float pitch = args.hasNext() ? Float.parseFloat(args.pop().value()) : 1F;
            Sound sound = Sound.sound(key, Sound.Source.MASTER, volume, pitch);
            return once(state, player -> player.playSound(sound, Sound.Emitter.self()));
        } catch (RuntimeException ignored) {
            // Invalid sound arguments are ignored so they do not interrupt message delivery.
            return NOTHING;
        }
    }

    // MiniMessage resolves every tag twice: once while looking for tags that rewrite the input, and again
    // while building the component. Registering the effect in the resolver would play each sound and show
    // each title twice, so it is registered when the tag's content is inserted, which happens once.
    private static Tag once(final ChargeState state, final java.util.function.Consumer<org.bukkit.entity.Player> effect) {
        return new Inserting() {
            @Override
            public @NotNull Component value() {
                state.effect(effect);
                return Component.empty();
            }

            @Override
            public boolean allowsChildren() {
                return false;
            }
        };
    }

    private static @Nullable ChargeState active() {
        ChargeState state = ChargeState.current();
        return state != null && state.boltsAllowed() ? state : null;
    }

    private static Duration ticks(final ArgumentQueue args, final int fallback) {
        int ticks = fallback;
        if (args.hasNext()) {
            try {
                ticks = Integer.parseInt(args.pop().value());
            } catch (NumberFormatException ignored) {
            }
        }
        return Duration.ofMillis(Math.max(0, Math.min(ticks, 72_000)) * 50L);
    }
}
