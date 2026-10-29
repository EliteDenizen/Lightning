package com.denizenmc.lightning.rewire;

import org.jetbrains.annotations.NotNull;

// Limits how much work a regular expression may do on a single message.
// A badly written rule could otherwise backtrack for seconds on unusual text and stall the network thread.
final class Bounded implements CharSequence {

    private static final int BUDGET = 1_000_000;

    private final CharSequence text;
    private final int[] reads;

    Bounded(@NotNull final CharSequence text) {
        this(text, new int[1]);
    }

    private Bounded(final CharSequence text, final int[] reads) {
        this.text = text;
        this.reads = reads;
    }

    @Override
    public char charAt(final int index) {
        if (++reads[0] > BUDGET) throw new IllegalStateException("The rule took too long to match this message.");
        return text.charAt(index);
    }

    @Override
    public int length() {
        return text.length();
    }

    @Override
    public @NotNull CharSequence subSequence(final int start, final int end) {
        return new Bounded(text.subSequence(start, end), reads);
    }

    @Override
    public @NotNull String toString() {
        return text.toString();
    }
}