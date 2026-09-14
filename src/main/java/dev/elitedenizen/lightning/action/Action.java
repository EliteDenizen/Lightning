package dev.elitedenizen.lightning.action;

public interface Action {
    void execute(ActionContext context);
    boolean isEnabled();
    long getDelayTicks();
    String getType();

    default boolean shouldExecute(ActionContext context) { return isEnabled(); }
}
