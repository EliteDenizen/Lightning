package dev.elitedenizen.lightning.util.scheduling;

import dev.elitedenizen.lightning.Lightning;
import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;
import dev.elitedenizen.lightning.util.server.VersionAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class SchedulerBridge {

    private static Object globalScheduler;
    private static Object asyncScheduler;

    private static Method globalRun;
    private static Method globalRunDelayed;
    private static Method asyncRunNow;
    private static Method asyncRunDelayed;
    private static Method asyncRunAtFixedRate;
    private static Method playerGetScheduler;
    private static Method entityRun;
    private static Method entityRunDelayed;

    static {
        if (VersionAdapter.FOLIA) {
            try {
                Method getGlobal = Bukkit.class.getMethod("getGlobalRegionScheduler");
                globalScheduler = getGlobal.invoke(null);

                Method getAsync = Bukkit.class.getMethod("getAsyncScheduler");
                asyncScheduler = getAsync.invoke(null);

                Class<?> globalClass = globalScheduler.getClass();
                globalRun = globalClass.getMethod("run", Plugin.class, Consumer.class);
                globalRunDelayed = globalClass.getMethod("runDelayed", Plugin.class, Consumer.class, long.class);

                Class<?> asyncClass = asyncScheduler.getClass();
                asyncRunNow = asyncClass.getMethod("runNow", Plugin.class, Consumer.class);
                asyncRunDelayed = asyncClass.getMethod("runDelayed", Plugin.class, Consumer.class, long.class, TimeUnit.class);
                asyncRunAtFixedRate = asyncClass.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class, TimeUnit.class);

                playerGetScheduler = Player.class.getMethod("getScheduler");

                String entityClassName = "io.papermc.paper.threadedregions.scheduler.EntityScheduler";
                Class<?> entityClass = Class.forName(entityClassName);
                entityRun = entityClass.getMethod("run", Plugin.class, Consumer.class, Runnable.class);
                entityRunDelayed = entityClass.getMethod("runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);

                PluginLogger.debug("Hooked into Folia's scheduler through reflection, all good");
            } catch (Throwable e) {
                PluginLogger.error("Couldn't hook into Folia's scheduler, delayed and repeating tasks won't work right", e);
            }
        }
    }

    private SchedulerBridge() {}

    public static void runSync(Lightning plugin, Runnable task) {
        if (VersionAdapter.FOLIA) {
            foliaGlobalRun(plugin, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static void runAsync(Lightning plugin, Runnable task) {
        if (VersionAdapter.FOLIA) {
            foliaAsyncRun(plugin, task);
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    public static void runLater(Lightning plugin, Runnable task, long delayTicks) {
        long delay = Math.max(1, delayTicks);
        if (VersionAdapter.FOLIA) {
            try {
                globalRunDelayed.invoke(globalScheduler, plugin, (Consumer<Object>) st -> task.run(), delay);
            } catch (Throwable e) {
                PluginLogger.error("Couldn't schedule a delayed task through Folia", e);
            }
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        }
    }

    public static void runLaterAsync(Lightning plugin, Runnable task, long delayTicks) {
        long delay = Math.max(1, delayTicks);
        if (VersionAdapter.FOLIA) {
            try {
                asyncRunDelayed.invoke(asyncScheduler, plugin,
                        (Consumer<Object>) st -> task.run(), delay * 50L, TimeUnit.MILLISECONDS);
            } catch (Throwable e) {
                PluginLogger.error("Couldn't schedule a delayed async task through Folia", e);
            }
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delay);
        }
    }

    public static void runLaterForPlayer(Lightning plugin, Player player, Runnable task, long delayTicks) {
        if (!player.isOnline()) return;
        long delay = Math.max(1, delayTicks);
        if (VersionAdapter.FOLIA && playerGetScheduler != null && entityRunDelayed != null) {
            try {
                Object entityScheduler = playerGetScheduler.invoke(player);
                Consumer<Object> wrapped = st -> { if (player.isOnline()) task.run(); };
                entityRunDelayed.invoke(entityScheduler, plugin, wrapped, (Runnable) null, delay);
            } catch (Throwable e) {
                PluginLogger.error("Couldn't schedule that on {0}'s own region, falling back to the regular scheduler", e, player.getName());
                Bukkit.getScheduler().runTaskLater(plugin, () -> { if (player.isOnline()) task.run(); }, delay);
            }
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (player.isOnline()) task.run(); }, delay);
        }
    }

    public static void runForPlayer(Lightning plugin, Player player, Runnable task) {
        if (!player.isOnline()) return;
        if (VersionAdapter.FOLIA && playerGetScheduler != null && entityRun != null) {
            try {
                Object entityScheduler = playerGetScheduler.invoke(player);
                Consumer<Object> wrapped = st -> { if (player.isOnline()) task.run(); };
                entityRun.invoke(entityScheduler, plugin, wrapped, (Runnable) null);
            } catch (Throwable e) {
                PluginLogger.error("Couldn't run that on {0}'s own region, falling back to the regular scheduler", e, player.getName());
                Bukkit.getScheduler().runTask(plugin, () -> { if (player.isOnline()) task.run(); });
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> { if (player.isOnline()) task.run(); });
        }
    }

    public static CancellableTask runTimerAsync(Lightning plugin, Runnable task, long delayTicks, long periodTicks) {
        long delay = Math.max(1, delayTicks);
        long period = Math.max(1, periodTicks);
        if (VersionAdapter.FOLIA && asyncRunAtFixedRate != null) {
            try {
                Object handle = asyncRunAtFixedRate.invoke(asyncScheduler, plugin,
                        (Consumer<Object>) st -> task.run(),
                        delay * 50L, period * 50L, TimeUnit.MILLISECONDS);
                return foliaTaskHandle(handle);
            } catch (Throwable e) {
                PluginLogger.error("Couldn't set up a repeating async task through Folia", e);
                return () -> {};
            }
        }
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, period);
        return bukkitTask::cancel;
    }

    private static CancellableTask foliaTaskHandle(Object scheduledTask) {
        if (scheduledTask == null) return () -> {};
        try {
            Method cancel = scheduledTask.getClass().getMethod("cancel");
            return () -> {
                try {
                    cancel.invoke(scheduledTask);
                } catch (Throwable e) {
                    PluginLogger.error("Tried to cancel a Folia task and it didn't take", e);
                }
            };
        } catch (NoSuchMethodException e) {
            PluginLogger.error("Folia's task handle doesn't expose cancel() anymore, looks like their API shifted", e);
            return () -> {};
        }
    }

    public interface CancellableTask {
        void cancel();
    }

    public static void cancelAll(Plugin plugin) {
        if (!VersionAdapter.FOLIA) {
            Bukkit.getScheduler().cancelTasks(plugin);
        }
    }

    private static void foliaGlobalRun(Plugin plugin, Runnable task) {
        if (globalRun == null) return;
        try {
            globalRun.invoke(globalScheduler, plugin, (Consumer<Object>) st -> task.run());
        } catch (Throwable e) {
            PluginLogger.error("Couldn't run that on Folia's global region", e);
        }
    }

    private static void foliaAsyncRun(Plugin plugin, Runnable task) {
        if (asyncRunNow == null) return;
        try {
            asyncRunNow.invoke(asyncScheduler, plugin, (Consumer<Object>) st -> task.run());
        } catch (Throwable e) {
            PluginLogger.error("Couldn't hand that off to Folia's async scheduler", e);
        }
    }
}
