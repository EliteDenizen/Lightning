package com.denizenmc.lightning.charge;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;

public record Charged(
        @NotNull Component component,
        @NotNull Delivery delivery,
        boolean silent,
        boolean ticking,
        @NotNull List<Consumer<Player>> effects
) {

    public enum Delivery {
        IN_PLACE,
        ACTIONBAR,
        TITLE
    }

    static @NotNull Charged dropped() {
        return new Charged(Component.empty(), Delivery.IN_PLACE, true, false, List.of());
    }

    static @NotNull Charged inPlace(@NotNull final Component component) {
        return new Charged(component, Delivery.IN_PLACE, false, false, List.of());
    }
}