package dev.elitedenizen.lightning.action.types;

import dev.elitedenizen.lightning.action.Action;
import dev.elitedenizen.lightning.action.ActionContext;
import dev.elitedenizen.lightning.util.scheduling.TickConverter;

public final class DelayAction implements Action {

    private final long delayTicks;

    public DelayAction(Object value) {
        this.delayTicks = Math.max(0, TickConverter.toTicks(value));
    }

    @Override public void execute(ActionContext context) {}
    @Override public boolean isEnabled() { return true; }
    @Override public long getDelayTicks() { return delayTicks; }
    @Override public String getType() { return "delay"; }
}
