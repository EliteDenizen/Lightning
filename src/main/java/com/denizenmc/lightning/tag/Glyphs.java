package com.denizenmc.lightning.tag;

import com.denizenmc.lightning.Settings;
import com.denizenmc.lightning.charge.ChargeState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.tag.Modifying;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

// Provides tags for text transformations, line breaks, and dynamic values.
public final class Glyphs {

    private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
    private static final Tag NOTHING = Tag.selfClosingInserting(Component.empty());
    private static final Map<String, DateTimeFormatter> FORMATS = new ConcurrentHashMap<>();

    private Glyphs() {
    }

    public static @NotNull TagResolver resolver(@NotNull final Settings settings) {
        return TagResolver.resolver(
                TagResolver.resolver(Set.of("smallcaps", "sc"), (args, context) -> rewrite(Glyphs::smallCaps)),
                TagResolver.resolver(Set.of("upper"), (args, context) -> rewrite(text -> text.toUpperCase(Locale.ROOT))),
                TagResolver.resolver(Set.of("lower"), (args, context) -> rewrite(text -> text.toLowerCase(Locale.ROOT))),
                TagResolver.resolver("n", Tag.selfClosingInserting(Component.newline())),
                TagResolver.resolver("charge", NOTHING),
                // Centering is handled before parsing; this resolver only registers the tag.
                TagResolver.resolver("center", NOTHING),
                TagResolver.resolver("clock", (args, context) -> {
                    ChargeState state = ChargeState.current();
                    if (state != null) state.markTicking();
                    String pattern = args.hasNext() ? args.pop().value() : "HH:mm";
                    try {
                        DateTimeFormatter format = FORMATS.computeIfAbsent(pattern, DateTimeFormatter::ofPattern);
                        return Tag.selfClosingInserting(Component.text(ZonedDateTime.now(settings.zone()).format(format)));
                    } catch (RuntimeException e) {
                        return Tag.selfClosingInserting(Component.text("?"));
                    }
                }));
    }

    public static @NotNull String smallCaps(@NotNull final String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int index = Character.toLowerCase(c) - 'a';
            out.append(index >= 0 && index < 26 ? SMALL_CAPS.charAt(index) : c);
        }
        return out.toString();
    }

    private static Tag rewrite(final UnaryOperator<String> change) {
        return (Modifying) (current, depth) -> current instanceof TextComponent text
                ? text.content(change.apply(text.content())).children(List.of())
                : current.children(List.of());
    }
}
