package dev.elitedenizen.lightning.action.types;

import dev.elitedenizen.lightning.action.Action;
import dev.elitedenizen.lightning.action.ActionContext;
import dev.elitedenizen.lightning.condition.Condition;
import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;
import dev.elitedenizen.lightning.util.text.ColorFormatter;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction.CustomClickAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class DialogAction implements Action {

    private final String title;
    private final String body;
    private final List<Button> buttons;
    private final boolean enabled;
    private final Condition condition;

    private DialogAction(Builder b) {
        this.title = b.title != null ? b.title : "";
        this.body = b.body != null ? b.body : "";
        this.buttons = new ArrayList<>(b.buttons);
        this.enabled = b.enabled && !this.title.isEmpty() && !this.buttons.isEmpty();
        this.condition = b.condition != null ? b.condition : Condition.none();
    }

    @Override
    public void execute(ActionContext context) {
        if (!enabled || !condition.passes(context)) return;
        Player player = context.getPlayer();
        if (player == null || !player.isOnline()) return;

        try {
            Component titleComponent = ColorFormatter.parse(context.applyPlaceholders(title), player);
            Component bodyComponent = body.isEmpty()
                    ? Component.empty()
                    : ColorFormatter.parse(context.applyPlaceholders(body), player);

            io.papermc.paper.dialog.Dialog dialog = Dialog.create(factory -> {
                DialogBase.Builder base = DialogBase.builder(titleComponent);
                if (!body.isEmpty()) base.body(List.of(DialogBody.plainMessage(bodyComponent)));

                ActionButton[] actionButtons = buttons.stream()
                        .map(btn -> btn.toActionButton(context))
                        .toArray(ActionButton[]::new);

                factory.empty()
                        .base(base.build())
                        .type(buttons.size() == 1
                                ? DialogType.notice(actionButtons[0])
                                : DialogType.confirmation(actionButtons[0], actionButtons[1]));
            });

            player.showDialog(dialog);
        } catch (Throwable e) {
            PluginLogger.warn("Couldn't show a dialog to {0}, this server might be older than Paper 1.21.7 "
                    + "which is what dialogs need: {1}", player.getName(), e.getMessage());
        }
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public long getDelayTicks() { return 0; }
    @Override public String getType() { return "dialog"; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String title;
        private String body;
        private final List<Button> buttons = new ArrayList<>();
        private boolean enabled = true;
        private Condition condition;

        private Builder() {}

        public Builder title(String t) { this.title = t; return this; }
        public Builder body(String b) { this.body = b; return this; }
        public Builder addButton(String label, String playerCommand, String consoleCommand) {
            buttons.add(new Button(label, playerCommand, consoleCommand));
            return this;
        }
        public Builder enabled(boolean e) { this.enabled = e; return this; }
        public Builder condition(Condition c) { this.condition = c; return this; }
        public DialogAction build() { return new DialogAction(this); }
    }

    private static final class Button {
        private final String label;
        private final String playerCommand;
        private final String consoleCommand;

        private Button(String label, String playerCommand, String consoleCommand) {
            this.label = label != null ? label : "";
            this.playerCommand = playerCommand;
            this.consoleCommand = consoleCommand;
        }

        private ActionButton toActionButton(ActionContext context) {
            Component labelComponent = ColorFormatter.parse(context.applyPlaceholders(label), context.getPlayer());
            CustomClickAction click = io.papermc.paper.registry.data.dialog.action.DialogAction.customClick(
                    (response, audience) -> runCommands(context),
                    ClickCallback.Options.builder().build());
            return ActionButton.builder(labelComponent).action(click).build();
        }

        private void runCommands(ActionContext context) {
            Player player = context.getPlayer();
            try {
                if (playerCommand != null && !playerCommand.isEmpty() && player != null) {
                    player.performCommand(context.applyPlaceholders(stripSlash(playerCommand)));
                }
                if (consoleCommand != null && !consoleCommand.isEmpty()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), context.applyPlaceholders(stripSlash(consoleCommand)));
                }
            } catch (Exception e) {
                PluginLogger.error("A dialog button's command failed to run", e);
            }
        }

        private static String stripSlash(String cmd) {
            return cmd.startsWith("/") ? cmd.substring(1) : cmd;
        }
    }
}
