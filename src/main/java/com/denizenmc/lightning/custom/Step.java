package com.denizenmc.lightning.custom;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// Represents one step in a command's action list.
sealed interface Step {

    // Sends a message to the command sender, processed like other Lightning text.
    record Say(@NotNull String text) implements Step {
    }

    record Console(@NotNull String command) implements Step {
    }

    record Perform(@NotNull String command) implements Step {
    }

    record Broadcast(@NotNull String text, @Nullable String permission) implements Step {
    }

    record Wait(long millis) implements Step {
    }

    record Branch(@NotNull String condition, @NotNull List<Step> then, @NotNull List<Step> otherwise) implements Step {
    }

    record Chance(double percent, @NotNull List<Step> then, @NotNull List<Step> otherwise) implements Step {
    }

    record Stop() implements Step {
    }

    // Randomly selects an option, which can contain a single step or a sequence of steps.
    record Pick(@NotNull List<List<Step>> options) implements Step {
    }
}