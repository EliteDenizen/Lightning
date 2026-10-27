package com.denizenmc.lightning.charge;

import com.denizenmc.lightning.Lightning;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.Pattern;

// Remembers potentially unsafe player input so it can be kept as literal text when processed later.
public final class Guard {

    private static final int LIMIT = 2048;
    private static final Pattern TAGS = Pattern.compile("<[^<>]*>");
    private static final Pattern PLACEHOLDERS = Pattern.compile("%[^%\\s]+%");

    private final Lightning plugin;
    private final Deque<Memory> memories = new ConcurrentLinkedDeque<>();

    public Guard(@NotNull final Lightning plugin) {
        this.plugin = plugin;
    }

    public void remember(@Nullable final String input) {
        if (input == null || !plugin.settings().guard()) return;
        if (input.indexOf('<') < 0 && input.indexOf('%') < 0) return;
        if (visible(input) < 3) return;

        long now = System.currentTimeMillis();
        Memory oldest;
        while ((oldest = memories.peekFirst()) != null && oldest.expires() < now) memories.pollFirst();
        memories.addLast(new Memory(input, now + plugin.settings().guardMemory()));
        if (memories.size() > LIMIT) memories.pollFirst();
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
            if (!result.contains(memory.input())) continue;
            result = result.replace(memory.input(), memory.input().replace("<", "\\<"));
            if (memory.input().indexOf('%') >= 0) blocksPlaceholders = true;
        }
        return new Defused(result, blocksPlaceholders);
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

    private record Memory(String input, long expires) {
    }
}