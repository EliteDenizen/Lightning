package com.denizenmc.lightning.listener;

import com.denizenmc.lightning.Lightning;
import com.denizenmc.lightning.charge.Charged;
import com.denizenmc.lightning.charge.Surface;
import com.github.retrooper.packetevents.protocol.dialog.CommonDialogData;
import com.github.retrooper.packetevents.protocol.dialog.ConfirmationDialog;
import com.github.retrooper.packetevents.protocol.dialog.Dialog;
import com.github.retrooper.packetevents.protocol.dialog.DialogListDialog;
import com.github.retrooper.packetevents.protocol.dialog.MultiActionDialog;
import com.github.retrooper.packetevents.protocol.dialog.NoticeDialog;
import com.github.retrooper.packetevents.protocol.dialog.ServerLinksDialog;
import com.github.retrooper.packetevents.protocol.dialog.body.DialogBody;
import com.github.retrooper.packetevents.protocol.dialog.body.ItemDialogBody;
import com.github.retrooper.packetevents.protocol.dialog.body.PlainMessage;
import com.github.retrooper.packetevents.protocol.dialog.body.PlainMessageDialogBody;
import com.github.retrooper.packetevents.protocol.dialog.button.ActionButton;
import com.github.retrooper.packetevents.protocol.dialog.button.CommonButtonData;
import com.github.retrooper.packetevents.protocol.dialog.input.BooleanInputControl;
import com.github.retrooper.packetevents.protocol.dialog.input.Input;
import com.github.retrooper.packetevents.protocol.dialog.input.TextInputControl;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

// Rebuilds each dialog with its text processed, including titles, messages, buttons, and tooltips.
final class DialogCharger {

    private final Lightning plugin;
    private final Player viewer;
    private boolean changed;

    DialogCharger(final Lightning plugin, final @Nullable Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    boolean changed() {
        return changed;
    }

    Dialog charge(final Dialog dialog) {
        if (dialog instanceof NoticeDialog notice) {
            return new NoticeDialog(common(notice.getCommon()), button(notice.getAction()));
        }
        if (dialog instanceof ConfirmationDialog confirmation) {
            return new ConfirmationDialog(common(confirmation.getCommon()), button(confirmation.getYesButton()), button(confirmation.getNoButton()));
        }
        if (dialog instanceof MultiActionDialog multi) {
            ActionButton exit = multi.getExitAction() == null ? null : button(multi.getExitAction());
            return new MultiActionDialog(common(multi.getCommon()), multi.getActions().stream().map(this::button).toList(), exit, multi.getColumns());
        }
        if (dialog instanceof ServerLinksDialog links) {
            ActionButton exit = links.getExitAction() == null ? null : button(links.getExitAction());
            return new ServerLinksDialog(common(links.getCommon()), exit, links.getColumns(), links.getButtonWidth());
        }
        if (dialog instanceof DialogListDialog list) {
            ActionButton exit = list.getExitAction() == null ? null : button(list.getExitAction());
            return new DialogListDialog(common(list.getCommon()), list.getDialogs(), exit, list.getColumns(), list.getButtonWidth());
        }
        return dialog;
    }

    // Text and checkbox labels are charged; sliders and option lists keep their labels unchanged.
    private Input input(final Input input) {
        if (input.getControl() instanceof TextInputControl text) {
            return new Input(input.getKey(), new TextInputControl(text.getWidth(), text(text.getLabel()), text.isLabelVisible(),
                    text.getInitial(), text.getMaxLength(), text.getMultiline()));
        }
        if (input.getControl() instanceof BooleanInputControl toggle) {
            return new Input(input.getKey(), new BooleanInputControl(text(toggle.getLabel()), toggle.isInitial(),
                    toggle.getOnTrue(), toggle.getOnFalse()));
        }
        return input;
    }

    private CommonDialogData common(final CommonDialogData common) {
        Component external = common.getExternalTitle() == null ? null : text(common.getExternalTitle());
        return new CommonDialogData(text(common.getTitle()), external, common.isCanCloseWithEscape(), common.isPause(),
                common.getAfterAction(), common.getBody().stream().map(this::body).toList(), common.getInputs().stream().map(this::input).toList());
    }

    private DialogBody body(final DialogBody body) {
        if (body instanceof PlainMessageDialogBody plain) return new PlainMessageDialogBody(message(plain.getMessage()));
        if (body instanceof ItemDialogBody item && item.getDescription() != null) {
            return new ItemDialogBody(item.getItem(), message(item.getDescription()), item.isShowDecorations(), item.isShowTooltip(),
                    item.getWidth(), item.getHeight());
        }
        return body;
    }

    private PlainMessage message(final PlainMessage message) {
        return new PlainMessage(text(message.getContents()), message.getWidth());
    }

    private ActionButton button(final ActionButton button) {
        CommonButtonData data = button.getButton();
        Component tooltip = data.getTooltip() == null ? null : text(data.getTooltip());
        return new ActionButton(new CommonButtonData(text(data.getLabel()), tooltip, data.getWidth()), button.getAction());
    }

    private Component text(final Component original) {
        Charged charged = plugin.charger().charge(original, viewer, Surface.DIALOGS);
        plugin.inspector().observe(viewer, Surface.DIALOGS, original, charged);
        if (charged == null) return original;
        changed = true;
        return charged.component();
    }
}