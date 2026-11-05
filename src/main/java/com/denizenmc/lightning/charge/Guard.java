package com.denizenmc.lightning.charge;

import com.denizenmc.lightning.Lightning;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Deque;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Remembers potentially unsafe player input so it can be kept as literal text when processed later.
public final class Guard {

    private static final int LIMIT = 2048;
    private static final int MAX_INPUT = 1024;
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Pattern TAGS = Pattern.compile("<[^<>]*>");
    private static final Pattern TAG = Pattern.compile("<([a-zA-Z_][a-zA-Z0-9_-]*):[^<>]*>");
    private static final Pattern PLACEHOLDERS = Pattern.compile("%[^%\\s]+%");

    // Tags that do more than change how text looks. A player who can sneak one of these into a message
    // could make other players run commands, show fake hover text, or trigger effects for everyone, so
    // they are remembered even when the rest of the input is too short to be remembered on its own.
    // Only written with arguments: a bare tag such as <face> is too common in real messages to block.
    private static final Set<String> RISKY = Set.of(
            "click", "insert", "insertion", "hover", "nbt", "data", "selector", "sel", "score",
            "actionbar", "title", "sound");

    private final Lightning plugin;
    private final Deque<Memory> memories = new ConcurrentLinkedDeque<>();

    public Guard(@NotNull final Lightning plugin) {
        this.plugin = plugin;
    }

    public void remember(@Nullable final String input) {
        if (input == null || input.isEmpty() || !plugin.settings().guard()) return;
        if (input.indexOf('<') < 0 && input.indexOf('%') < 0) return;
        String text = input.length() > MAX_INPUT ? input.substring(0, MAX_INPUT) : input;

        long expires = System.currentTimeMillis() + plugin.settings().guardMemory();
        prune(System.currentTimeMillis());
        if (visible(text) >= 3) store(text, expires);

        // Remember risky tags on their own as well. Plugins often reformat what a player typed, for example by
        // trimming it or adding their own text around it, so the full input may never appear unchanged.
        Matcher matcher = TAG.matcher(text);
        while (matcher.find()) {
            if (RISKY.contains(matcher.group(1).toLowerCase(Locale.ROOT))) store(matcher.group(), expires);
        }
    }

    private void store(final String input, final long expires) {
        // Messages are matched against the text Lightning reads, so use the same escaping the serializer applies.
        String escaped = MINI.serialize(Component.text(input));
        String readable = Sense.unescape(escaped);
        boolean placeholders = input.indexOf('%') >= 0;
        if (readable.equals(escaped) && !placeholders) return;
        memories.addLast(new Memory(readable, escaped, placeholders, expires));
        while (memories.size() > LIMIT) memories.pollFirst();
    }

    private void prune(final long now) {
        Memory oldest;
        while ((oldest = memories.peekFirst()) != null && oldest.expires() < now) memories.pollFirst();
    }

    @NotNull Defused defuse(@NotNull final String text) {
        if (memories.isEmpty()) return new Defused(text, false);
        long now = System.currentTimeMillis();
        String result = text;
        boolean blocksPlaceholders = false;

        Iterator<Memory> iterator = memories.iterator();
        while (iterator.hasNext()) {
            Memory memory = iterator.next();
            if (memory.expires() < now) {
                iterator.remove();
                continue;
            }
            if (!result.contains(memory.readable())) continue;
            result = escape(result, memory);
            if (memory.placeholders()) blocksPlaceholders = true;
        }
        return new Defused(result, blocksPlaceholders);
    }

    // Replaces each occurrence with its escaped form. An occurrence whose first '<' is already escaped, because
    // another memory or the sending plugin put a backslash before it, keeps that backslash as the escape.
    // Escaping it a second time would turn "\<tag>" into "\\<tag>": a literal backslash followed by a live tag.
    private static String escape(final String text, final Memory memory) {
        String readable = memory.readable();
        String escaped = memory.escaped();
        StringBuilder out = new StringBuilder(text.length() + 16);
        int from = 0;
        int at;
        while ((at = text.indexOf(readable, from)) >= 0) {
            int slashes = 0;
            for (int i = at - 1; i >= 0 && text.charAt(i) == '\\'; i--) slashes++;
            boolean escapedAlready = slashes % 2 == 1 && readable.charAt(0) == '<' && escaped.startsWith("\\<");
            out.append(text, from, at).append(escapedAlready ? escaped.substring(1) : escaped);
            from = at + readable.length();
        }
        return out.append(text, from, text.length()).toString();
    }

    // Ignore standalone tags and placeholders. Remembering them could escape legitimate plugin messages,
    // and by themselves they do not contain enough text to pose the same risk.
    private static int visible(final String input) {
        String stripped = TAGS.matcher(input).replaceAll("");
        stripped = PLACEHOLDERS.matcher(stripped).replaceAll("");
        int count = 0;
        for (int i = 0; i < stripped.length(); i++) {
            if (!Character.isWhitespace(stripped.charAt(i))) count++;
        }
        return count;
    }

    public void clear() {
        memories.clear();
    }

    record Defused(@NotNull String text, boolean blocksPlaceholders) {
    }

    // readable is how the input appears in the text Lightning reads; escaped is the form MiniMessage shows literally.
    private record Memory(String readable, String escaped, boolean placeholders, long expires) {
    }
}
