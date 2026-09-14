package dev.elitedenizen.lightning.action.types;

import dev.elitedenizen.lightning.action.Action;
import dev.elitedenizen.lightning.action.ActionContext;
import dev.elitedenizen.lightning.condition.Condition;
import org.bukkit.entity.Player;

public final class CloseInventoryAction implements Action {

    private final boolean enabled;
    private final Condition condition;

    private CloseInventoryAction(boolean enabled, Condition condition) {
        this.enabled = enabled;
        this.condition = condition != null ? condition : Condition.none();
    }

    @Override
    public void execute(ActionContext context) {
        if (!enabled || !condition.passes(context)) return;
        Player player = context.getPlayer();
        if (player == null || !player.isOnline()) return;
        player.closeInventory();
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public long getDelayTicks() { return 0; }
    @Override public String getType() { return "closeInventory"; }

    public static CloseInventoryAction of(boolean enabled, Condition condition) {
        return new CloseInventoryAction(enabled, condition);
    }
}
