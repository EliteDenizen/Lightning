package com.denizenmc.lightning.listener;

import com.denizenmc.lightning.Lightning;
import com.denizenmc.lightning.charge.Charged;
import com.denizenmc.lightning.charge.Charger;
import com.denizenmc.lightning.charge.Surface;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.component.ComponentType;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemLore;
import com.github.retrooper.packetevents.protocol.dialog.Dialog;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerDisconnect;
import com.github.retrooper.packetevents.wrapper.login.server.WrapperLoginServerDisconnect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerActionBar;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBossBar;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDisconnect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerListHeaderAndFooter;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerResetScore;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerScoreboardObjective;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetCursorItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetTitleSubtitle;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetTitleText;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerShowDialog;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateScore;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

// Handles outgoing packets containing player-facing text on the Netty thread.
public final class SurfaceListener extends PacketListenerAbstract {

    private static final List<ComponentType<Component>> ITEM_NAMES = List.of(ComponentTypes.CUSTOM_NAME, ComponentTypes.ITEM_NAME);

    private final Lightning plugin;

    public SurfaceListener(@NotNull final Lightning plugin) {
        super(PacketListenerPriority.HIGH);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSend(@NotNull final PacketSendEvent event) {
        if (event.isCancelled()) return;
        PacketTypeCommon type = event.getPacketType();
        Player viewer = event.getPlayer();

        try {
            if (type == PacketType.Play.Server.SYSTEM_CHAT_MESSAGE) chat(event, viewer);
            else if (type == PacketType.Play.Server.ACTION_BAR) actionbar(event, viewer);
            else if (type == PacketType.Play.Server.SET_TITLE_TEXT || type == PacketType.Play.Server.SET_TITLE_SUBTITLE) {
                if (reaches(Surface.TITLES)) title(event, viewer);
            } else if (type == PacketType.Play.Server.BOSS_BAR) {
                if (reaches(Surface.BOSSBARS)) bossbar(event, viewer);
            } else if (type == PacketType.Play.Server.PLAYER_LIST_HEADER_AND_FOOTER) {
                if (reaches(Surface.TABLIST)) tablist(event, viewer);
            } else if (type == PacketType.Play.Server.TEAMS) team(event, viewer);
            else if (type == PacketType.Play.Server.SCOREBOARD_OBJECTIVE) objective(event, viewer);
            else if (type == PacketType.Play.Server.UPDATE_SCORE) score(event, viewer);
            else if (type == PacketType.Play.Server.RESET_SCORE) resetScore(event, viewer);
            else if (type == PacketType.Play.Server.OPEN_WINDOW) {
                if (reaches(Surface.INVENTORIES)) inventory(event, viewer);
            } else if (type == PacketType.Play.Server.WINDOW_ITEMS || type == PacketType.Play.Server.SET_SLOT
                    || type == PacketType.Play.Server.SET_CURSOR_ITEM || type == PacketType.Play.Server.SET_PLAYER_INVENTORY) {
                items(event, viewer);
            } else if (type == PacketType.Play.Server.ENTITY_METADATA) entities(event, viewer);
            else if (type == PacketType.Play.Server.DESTROY_ENTITIES) destroyed(event, viewer);
            else if (type == PacketType.Play.Server.DISCONNECT || type == PacketType.Login.Server.DISCONNECT
                    || type == PacketType.Configuration.Server.DISCONNECT) {
                if (reaches(Surface.KICKS)) kick(event, viewer);
            } else if (type == PacketType.Play.Server.SHOW_DIALOG) dialog(event, viewer);
        } catch (RuntimeException | StackOverflowError | LinkageError e) {
            plugin.debug("Skipped a " + type + " packet: " + e.getMessage());
        }
    }

    private void chat(final PacketSendEvent event, final Player viewer) {
        WrapperPlayServerSystemChatMessage packet = new WrapperPlayServerSystemChatMessage(event);
        Surface surface = packet.isOverlay() ? Surface.ACTIONBAR : Surface.CHAT;
        if (!plugin.settings().reaches(surface)) return;

        Component original = packet.getMessage();
        if (Inspector.isReport(original) || Charger.isOwn(original)) return;

        Charged charged = plugin.charger().charge(original, viewer, surface);
        if (plugin.inspector().wants(viewer, surface, original)) {
            Component report = plugin.inspector().report(original, charged, surface);
            later(viewer, player -> player.sendMessage(report));
        }
        if (charged == null) return;
        if (viewer != null) charged.effects().forEach(effect -> later(viewer, effect));

        if (charged.silent()) {
            event.setCancelled(true);
            return;
        }

        Component message = charged.component();
        switch (charged.delivery()) {
            case ACTIONBAR -> {
                event.setCancelled(true);
                if (viewer != null) later(viewer, player -> player.sendActionBar(message));
            }
            case TITLE -> {
                event.setCancelled(true);
                if (viewer != null) later(viewer, player -> player.showTitle(Title.title(message, Component.empty())));
            }
            case IN_PLACE -> {
                packet.setMessage(message);
                event.markForReEncode(true);
            }
        }
    }

    private void actionbar(final PacketSendEvent event, final Player viewer) {
        WrapperPlayServerActionBar packet = new WrapperPlayServerActionBar(event);
        Component original = packet.getActionBarText();
        Charged charged = charge(original, viewer, Surface.ACTIONBAR);
        if (original != null && plugin.inspector().wants(viewer, Surface.ACTIONBAR, original)) {
            Component report = plugin.inspector().report(original, charged, Surface.ACTIONBAR);
            later(viewer, player -> player.sendMessage(report));
        }
        if (charged == null) return;
        packet.setActionBarText(charged.component());
        event.markForReEncode(true);
    }

    private void title(final PacketSendEvent event, final Player viewer) {
        if (event.getPacketType() == PacketType.Play.Server.SET_TITLE_TEXT) {
            WrapperPlayServerSetTitleText packet = new WrapperPlayServerSetTitleText(event);
            Charged charged = charge(packet.getTitle(), viewer, Surface.TITLES);
            if (charged == null) return;
            packet.setTitle(charged.component());
        } else {
            WrapperPlayServerSetTitleSubtitle packet = new WrapperPlayServerSetTitleSubtitle(event);
            Charged charged = charge(packet.getSubtitle(), viewer, Surface.TITLES);
            if (charged == null) return;
            packet.setSubtitle(charged.component());
        }
        event.markForReEncode(true);
    }

    private void bossbar(final PacketSendEvent event, final Player viewer) {
        WrapperPlayServerBossBar packet = new WrapperPlayServerBossBar(event);
        WrapperPlayServerBossBar.Action action = packet.getAction();
        String slot = "bossbar:" + packet.getUUID();
        if (action == WrapperPlayServerBossBar.Action.REMOVE) {
            forget(viewer, slot);
            return;
        }
        if (action != WrapperPlayServerBossBar.Action.ADD && action != WrapperPlayServerBossBar.Action.UPDATE_TITLE) return;

        Component original = packet.getTitle();
        Charged charged = charge(original, viewer, Surface.BOSSBARS);
        if (charged == null) {
            forget(viewer, slot);
            return;
        }
        packet.setTitle(charged.component());
        event.markForReEncode(true);

        if (!charged.ticking() || viewer == null) {
            forget(viewer, slot);
            return;
        }
        UUID id = packet.getUUID();
        plugin.pulse().track(event.getUser(), viewer, Surface.BOSSBARS, slot, List.of(original), List.of(charged.component()), fresh -> {
            WrapperPlayServerBossBar update = new WrapperPlayServerBossBar(id, WrapperPlayServerBossBar.Action.UPDATE_TITLE);
            update.setTitle(fresh.get(0));
            return update;
        });
    }

    private void tablist(final PacketSendEvent event, final Player viewer) {
        WrapperPlayServerPlayerListHeaderAndFooter packet = new WrapperPlayServerPlayerListHeaderAndFooter(event);
        Component[] originals = {packet.getHeader(), packet.getFooter()};
        Charged header = charge(originals[0], viewer, Surface.TABLIST);
        Charged footer = charge(originals[1], viewer, Surface.TABLIST);
        if (header != null) packet.setHeader(header.component());
        if (footer != null) packet.setFooter(footer.component());
        if (header != null || footer != null) event.markForReEncode(true);

        if (!ticking(header, footer) || viewer == null) {
            forget(viewer, "tablist");
            return;
        }
        List<Component> shown = Arrays.asList(shown(header, originals[0]), shown(footer, originals[1]));
        plugin.pulse().track(event.getUser(), viewer, Surface.TABLIST, "tablist", Arrays.asList(originals), shown,
                fresh -> new WrapperPlayServerPlayerListHeaderAndFooter(fresh.get(0), fresh.get(1)));
    }

    private void team(final PacketSendEvent event, final Player viewer) {
        if (!plugin.settings().reaches(Surface.SCOREBOARDS)) return;
        WrapperPlayServerTeams packet = new WrapperPlayServerTeams(event);
        String slot = "team:" + packet.getTeamName();
        if (packet.getTeamMode() == WrapperPlayServerTeams.TeamMode.REMOVE) {
            forget(viewer, slot);
            return;
        }

        Optional<WrapperPlayServerTeams.ScoreBoardTeamInfo> found = packet.getTeamInfo();
        if (found.isEmpty()) return;
        WrapperPlayServerTeams.ScoreBoardTeamInfo info = found.get();
        Component[] originals = {info.getPrefix(), info.getSuffix(), info.getDisplayName()};
        Charged prefix = charge(originals[0], viewer, Surface.SCOREBOARDS);
        Charged suffix = charge(originals[1], viewer, Surface.SCOREBOARDS);
        Charged name = charge(originals[2], viewer, Surface.SCOREBOARDS);
        if (prefix != null) info.setPrefix(prefix.component());
        if (suffix != null) info.setSuffix(suffix.component());
        if (name != null) info.setDisplayName(name.component());
        if (prefix != null || suffix != null || name != null) event.markForReEncode(true);

        if (!ticking(prefix, suffix, name) || viewer == null) {
            forget(viewer, slot);
            return;
        }
        String team = packet.getTeamName();
        List<Component> shown = Arrays.asList(shown(prefix, originals[0]), shown(suffix, originals[1]), shown(name, originals[2]));
        plugin.pulse().track(event.getUser(), viewer, Surface.SCOREBOARDS, slot, Arrays.asList(originals), shown, fresh -> {
            info.setPrefix(fresh.get(0));
            info.setSuffix(fresh.get(1));
            info.setDisplayName(fresh.get(2));
            return new WrapperPlayServerTeams(team, WrapperPlayServerTeams.TeamMode.UPDATE, info, List.of());
        });
    }

    private void objective(final PacketSendEvent event, final Player viewer) {
        if (!plugin.settings().reaches(Surface.SCOREBOARDS)) return;
        WrapperPlayServerScoreboardObjective packet = new WrapperPlayServerScoreboardObjective(event);
        String name = packet.getName();
        if (packet.getMode() == WrapperPlayServerScoreboardObjective.ObjectiveMode.REMOVE) {
            forget(viewer, "objective:" + name);
            if (viewer != null) plugin.pulse().forgetStarting(viewer.getUniqueId(), "score:" + name + ":");
            return;
        }

        Component original = packet.getDisplayName();
        Charged charged = charge(original, viewer, Surface.SCOREBOARDS);
        if (charged == null) {
            forget(viewer, "objective:" + name);
            return;
        }
        packet.setDisplayName(charged.component());
        event.markForReEncode(true);

        if (!charged.ticking() || viewer == null) {
            forget(viewer, "objective:" + name);
            return;
        }
        var render = packet.getRenderType();
        var format = packet.getScoreFormat();
        plugin.pulse().track(event.getUser(), viewer, Surface.SCOREBOARDS, "objective:" + name, List.of(original), List.of(charged.component()),
                fresh -> new WrapperPlayServerScoreboardObjective(name, WrapperPlayServerScoreboardObjective.ObjectiveMode.UPDATE, fresh.get(0), render, format));
    }

    private void score(final PacketSendEvent event, final Player viewer) {
        if (!plugin.settings().reaches(Surface.SCOREBOARDS)) return;
        WrapperPlayServerUpdateScore packet = new WrapperPlayServerUpdateScore(event);
        String slot = "score:" + packet.getObjectiveName() + ":" + packet.getEntityName();

        Component original = packet.getEntityDisplayName();
        Charged charged = charge(original, viewer, Surface.SCOREBOARDS);
        if (charged == null) {
            forget(viewer, slot);
            return;
        }
        packet.setEntityDisplayName(charged.component());
        event.markForReEncode(true);

        if (!charged.ticking() || viewer == null) {
            forget(viewer, slot);
            return;
        }
        String entity = packet.getEntityName();
        String objective = packet.getObjectiveName();
        var action = packet.getAction();
        int value = packet.getValue().orElse(0);
        var format = packet.getScoreFormat();
        plugin.pulse().track(event.getUser(), viewer, Surface.SCOREBOARDS, slot, List.of(original), List.of(charged.component()),
                fresh -> new WrapperPlayServerUpdateScore(entity, action, objective, value, fresh.get(0), format));
    }

    // Clear tracking for removed scores so Pulse cannot restore them.
    private void resetScore(final PacketSendEvent event, final Player viewer) {
        if (viewer == null) return;
        WrapperPlayServerResetScore packet = new WrapperPlayServerResetScore(event);
        String objective = packet.getObjective();
        if (objective == null) plugin.pulse().forgetStarting(viewer.getUniqueId(), "score:");
        else plugin.pulse().forget(viewer.getUniqueId(), "score:" + objective + ":" + packet.getTargetName());
    }

    private void inventory(final PacketSendEvent event, final Player viewer) {
        WrapperPlayServerOpenWindow packet = new WrapperPlayServerOpenWindow(event);
        Charged charged = charge(packet.getTitle(), viewer, Surface.INVENTORIES);
        if (charged == null) return;
        packet.setTitle(charged.component());
        event.markForReEncode(true);
    }

    private void items(final PacketSendEvent event, final Player viewer) {
        if (!plugin.settings().reaches(Surface.ITEMS)) return;
        // Creative clients can send items back to the server, potentially saving modified formatting.
        if (viewer != null && viewer.getGameMode() == GameMode.CREATIVE) {
            plugin.debug("Left items unchanged for " + viewer.getName() + " because they're in creative mode.");
            return;
        }

        PacketTypeCommon type = event.getPacketType();
        boolean changed = false;
        if (type == PacketType.Play.Server.WINDOW_ITEMS) {
            WrapperPlayServerWindowItems packet = new WrapperPlayServerWindowItems(event);
            for (ItemStack item : packet.getItems()) changed |= item(item, viewer);
            Optional<ItemStack> carried = packet.getCarriedItem();
            if (carried.isPresent() && item(carried.get(), viewer)) changed = true;
        } else if (type == PacketType.Play.Server.SET_SLOT) {
            changed = item(new WrapperPlayServerSetSlot(event).getItem(), viewer);
        } else if (type == PacketType.Play.Server.SET_CURSOR_ITEM) {
            changed = item(new WrapperPlayServerSetCursorItem(event).getStack(), viewer);
        } else {
            changed = item(new WrapperPlayServerSetPlayerInventory(event).getStack(), viewer);
        }
        if (changed) event.markForReEncode(true);
    }

    private boolean item(final @Nullable ItemStack item, final Player viewer) {
        if (item == null || item.isEmpty()) return false;
        boolean changed = false;
        boolean named = false;

        for (ComponentType<Component> type : ITEM_NAMES) {
            Optional<Component> name = item.getComponent(type);
            if (name.isEmpty()) continue;
            named = true;
            Charged charged = charge(name.get(), viewer, Surface.ITEMS);
            if (charged == null) continue;
            item.setComponent(type, charged.component());
            changed = true;
        }

        // Unnamed items use vanilla translation keys. Supply that name so item rewires can match
        // entries such as block.minecraft.sand and rename otherwise unmodified stacks.
        if (!named && plugin.rewires().watching(Surface.ITEMS) > 0) {
            Component vanilla = vanillaName(item);
            Charged charged = vanilla == null ? null : charge(vanilla, viewer, Surface.ITEMS);
            if (charged != null) {
                item.setComponent(ComponentTypes.ITEM_NAME, charged.component());
                changed = true;
            }
        }

        Optional<ItemLore> lore = item.getComponent(ComponentTypes.LORE);
        if (lore.isPresent()) {
            List<Component> lines = new ArrayList<>(lore.get().getLines());
            boolean touched = false;
            for (int i = 0; i < lines.size(); i++) {
                Charged charged = charge(lines.get(i), viewer, Surface.ITEMS);
                if (charged == null) continue;
                lines.set(i, charged.component());
                touched = true;
            }
            if (touched) {
                item.setComponent(ComponentTypes.LORE, new ItemLore(lines));
                changed = true;
            }
        }
        return changed;
    }

    private static @Nullable Component vanillaName(final ItemStack item) {
        Material material = Material.matchMaterial(item.getType().getName().toString());
        return material == null ? null : Component.translatable(material.translationKey());
    }

    private void entities(final PacketSendEvent event, final Player viewer) {
        if (!plugin.settings().reaches(Surface.ENTITIES)) return;
        WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
        int entity = packet.getEntityId();
        List<EntityData<?>> data = packet.getEntityMetadata();
        List<EntityData<?>> result = new ArrayList<>(data.size());
        boolean changed = false;

        for (EntityData<?> entry : data) {
            int index = entry.getIndex();
            String slot = "entity:" + entity + ":" + index;
            Component text = null;
            boolean optional = false;
            if (entry.getType() == EntityDataTypes.ADV_COMPONENT && entry.getValue() instanceof Component value) {
                text = value;
            } else if (entry.getType() == EntityDataTypes.OPTIONAL_ADV_COMPONENT
                    && entry.getValue() instanceof Optional<?> value && value.orElse(null) instanceof Component inside) {
                text = inside;
                optional = true;
            }

            Charged charged = text == null ? null : charge(text, viewer, Surface.ENTITIES);
            if (charged == null) {
                if (text != null) forget(viewer, slot);
                result.add(entry);
                continue;
            }
            result.add(entityData(index, optional, charged.component()));
            changed = true;

            if (!charged.ticking() || viewer == null) {
                forget(viewer, slot);
                continue;
            }
            boolean wrapped = optional;
            plugin.pulse().track(event.getUser(), viewer, Surface.ENTITIES, slot, List.of(text), List.of(charged.component()),
                    fresh -> new WrapperPlayServerEntityMetadata(entity, List.of(entityData(index, wrapped, fresh.get(0)))));
        }

        if (!changed) return;
        packet.setEntityMetadata(result);
        event.markForReEncode(true);
    }

    private void destroyed(final PacketSendEvent event, final Player viewer) {
        if (viewer == null) return;
        for (int id : new WrapperPlayServerDestroyEntities(event).getEntityIds()) {
            plugin.pulse().forgetStarting(viewer.getUniqueId(), "entity:" + id + ":");
        }
    }

    // Login disconnects can occur before a Player exists, such as when a server is full
    // or a player is banned. Rewires and Sparks still run, but player-specific context is unavailable.
    private void kick(final PacketSendEvent event, final @Nullable Player viewer) {
        PacketTypeCommon type = event.getPacketType();
        if (type == PacketType.Login.Server.DISCONNECT) {
            WrapperLoginServerDisconnect packet = new WrapperLoginServerDisconnect(event);
            Charged charged = charge(packet.getReason(), null, Surface.KICKS);
            plugin.debug("Saw a login disconnect" + (charged == null ? ", nothing matched: " : ", charged: ") + Inspector.describe(packet.getReason())
                    + " (" + plugin.rewires().watching(Surface.KICKS) + " rewires watch kicks)");
            if (charged == null) return;
            packet.setReason(charged.component());
        } else if (type == PacketType.Configuration.Server.DISCONNECT) {
            WrapperConfigServerDisconnect packet = new WrapperConfigServerDisconnect(event);
            Charged charged = charge(packet.getReason(), null, Surface.KICKS);
            if (charged == null) return;
            packet.setReason(charged.component());
        } else {
            WrapperPlayServerDisconnect packet = new WrapperPlayServerDisconnect(event);
            Charged charged = charge(packet.getReason(), viewer, Surface.KICKS);
            if (charged == null) return;
            packet.setReason(charged.component());
        }
        event.markForReEncode(true);
    }

    private void dialog(final PacketSendEvent event, final Player viewer) {
        if (!plugin.settings().reaches(Surface.DIALOGS)) return;
        WrapperPlayServerShowDialog packet = new WrapperPlayServerShowDialog(event);
        DialogCharger charger = new DialogCharger(plugin, viewer);
        Dialog dialog = packet.getDialog();
        Dialog charged = charger.charge(dialog);
        plugin.debug("Saw a " + (dialog == null ? "missing" : dialog.getClass().getSimpleName()) + " dialog, " + (charger.changed() ? "charged it." : "nothing to charge."));
        if (!charger.changed()) return;
        packet.setDialog(charged);
        event.markForReEncode(true);
    }

    private @Nullable Charged charge(final @Nullable Component text, final Player viewer, final Surface surface) {
        if (text == null || !plugin.settings().reaches(surface)) return null;
        Charged charged = plugin.charger().charge(text, viewer, surface);
        plugin.inspector().observe(viewer, surface, text, charged);
        return charged;
    }

    private static EntityData<?> entityData(final int index, final boolean optional, final Component text) {
        return optional
                ? new EntityData<>(index, EntityDataTypes.OPTIONAL_ADV_COMPONENT, Optional.of(text))
                : new EntityData<>(index, EntityDataTypes.ADV_COMPONENT, text);
    }

    private static boolean ticking(final Charged... results) {
        for (Charged result : results) {
            if (result != null && result.ticking()) return true;
        }
        return false;
    }

    private static Component shown(final @Nullable Charged charged, final Component original) {
        return charged == null ? original : charged.component();
    }

    private boolean reaches(final Surface surface) {
        return plugin.settings().reaches(surface);
    }

    private void forget(final @Nullable Player viewer, final String slot) {
        if (viewer != null) plugin.pulse().forget(viewer.getUniqueId(), slot);
    }

    private void later(final Player viewer, final Consumer<Player> task) {
        viewer.getScheduler().run(plugin, scheduled -> task.accept(viewer), null);
    }
}