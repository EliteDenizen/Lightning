package com.denizenmc.lightning.charge;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class ChargeState {

    private static final ThreadLocal<ChargeState> CURRENT = new ThreadLocal<>();
    private static final int SPARK_BUDGET = 64;

    private final Surface surface;
    private final Player viewer;
    private final List<Consumer<Player>> effects = new ArrayList<>(2);
    private Charged.Delivery delivery = Charged.Delivery.IN_PLACE;
    private boolean silent;
    private boolean live;
    private boolean personal;
    private boolean ticking;
    private int sparksUsed;

    private ChargeState(final Surface surface, final Player viewer) {
        this.surface = surface;
        this.viewer = viewer;
    }

    static @NotNull ChargeState open(@NotNull final Surface surface, @Nullable final Player viewer) {
        ChargeState state = new ChargeState(surface, viewer);
        CURRENT.set(state);
        return state;
    }

    static void close() {
        CURRENT.remove();
    }

    public static @Nullable ChargeState current() {
        return CURRENT.get();
    }

    public @Nullable Player viewer() {
        return viewer;
    }

    public boolean boltsAllowed() {
        return surface.bolts();
    }

    // Marks results that depend on time, randomness, or other changing state so they aren't cached.
    public void markLive() {
        live = true;
    }

    // Marks results that depend on the viewer, allowing them to be cached separately per player.
    public void markPersonal() {
        personal = true;
    }

    // Marks time-dependent results for periodic updates through Pulse.
    public void markTicking() {
        ticking = true;
        live = true;
    }

    public void effect(@NotNull final Consumer<Player> effect) {
        effects.add(effect);
        live = true;
    }

    public void deliver(@NotNull final Charged.Delivery delivery) {
        this.delivery = delivery;
        live = true;
    }

    public void silence() {
        silent = true;
        live = true;
    }

    // Limits recursive spark processing to prevent infinite loops.
    public boolean spendSpark() {
        return ++sparksUsed <= SPARK_BUDGET;
    }

    boolean live() {
        return live;
    }

    boolean personal() {
        return personal;
    }

    boolean ticking() {
        return ticking;
    }

    boolean silent() {
        return silent;
    }

    Charged.Delivery delivery() {
        return delivery;
    }

    List<Consumer<Player>> effects() {
        return effects;
    }
}