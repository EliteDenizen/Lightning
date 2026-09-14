package dev.elitedenizen.lightning.action;

import dev.elitedenizen.lightning.action.types.*;
import dev.elitedenizen.lightning.condition.Condition;
import dev.elitedenizen.lightning.condition.ConditionLoader;
import dev.elitedenizen.lightning.util.config.ConfigReader;
import dev.elitedenizen.lightning.util.diagnostics.PluginLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class ActionLoader {

    private static TemplateManager templateManager;

    private ActionLoader() {}

    public static void setTemplateManager(TemplateManager manager) {
        templateManager = manager;
    }

    public static List<Action> loadActions(ConfigurationSection section) {
        if (section == null) return Collections.emptyList();
        Object raw = section.get("actions");
        if (!(raw instanceof List)) return Collections.emptyList();

        List<Action> actions = new ArrayList<>();
        for (Object obj : (List<?>) raw) {
            if (obj == null) continue;
            if (ConfigReader.has(obj, "template")) {
                expandTemplate(actions, obj);
            } else {
                Action a = parseAction(obj);
                if (a != null) actions.add(a);
            }
        }
        return actions;
    }

    public static Action parseAction(Object obj) {
        if (obj == null) return null;
        try {
            if (ConfigReader.has(obj, "delay")) return new DelayAction(ConfigReader.get(obj, "delay"));
            if (ConfigReader.has(obj, "message")) return parseMessage(obj);
            if (ConfigReader.has(obj, "randomMessage")) return parseRandomMessage(obj);
            if (ConfigReader.has(obj, "sound")) return parseSound(obj);
            if (ConfigReader.has(obj, "title")) return parseTitle(obj);
            if (ConfigReader.has(obj, "actionbar")) return parseActionBar(obj);
            if (ConfigReader.has(obj, "particle")) return parseParticle(obj);
            if (ConfigReader.has(obj, "command")) return parseCommand(obj);
            if (ConfigReader.has(obj, "broadcast")) return parseBroadcast(obj);
            if (ConfigReader.has(obj, "bossbar")) return parseBossBar(obj);
            if (ConfigReader.has(obj, "potion")) return parsePotion(obj);
            if (ConfigReader.has(obj, "chance")) return parseChance(obj);
            if (ConfigReader.has(obj, "xp")) return parseXp(obj);
            if (ConfigReader.has(obj, "closeInventory")) return parseCloseInventory(obj);
            if (ConfigReader.has(obj, "firework")) return parseFirework(obj);
            if (ConfigReader.has(obj, "dialog")) return parseDialog(obj);
        } catch (Exception e) {
            PluginLogger.error("Couldn't parse an action, here's what it looked like: {0}", e, String.valueOf(obj));
        }
        return null;
    }

    private static void expandTemplate(List<Action> into, Object obj) {
        if (templateManager == null) return;
        Object name = ConfigReader.get(obj, "template");
        if (name == null) return;
        List<Action> found = templateManager.get(name.toString());
        if (found.isEmpty()) {
            PluginLogger.warn("Template '{0}' produced no actions. Check it exists under 'templates:' "
                    + "in one of your actions/ files and is spelled correctly.", name);
        } else {
            PluginLogger.debug("Expanded template '{0}' into {1} action(s)", name, found.size());
        }
        into.addAll(found);
    }

    private static Condition readCondition(Object obj) {
        Object raw = extractConditionValue(obj);

        if (raw == null && obj instanceof Map) {
            for (Object value : ((Map<?, ?>) obj).values()) {
                raw = extractConditionValue(value);
                if (raw != null) break;
            }
        } else if (raw == null && obj instanceof ConfigurationSection) {
            ConfigurationSection sec = (ConfigurationSection) obj;
            for (String key : sec.getKeys(false)) {
                raw = extractConditionValue(sec.get(key));
                if (raw != null) break;
            }
        }

        if (raw == null) return Condition.none();
        return toCondition(raw);
    }

    private static Object extractConditionValue(Object obj) {
        Object raw = ConfigReader.get(obj, "conditions");
        if (raw == null) raw = ConfigReader.get(obj, "condition");
        return raw;
    }

    private static Condition toCondition(Object raw) {
        if (raw instanceof ConfigurationSection) return ConditionLoader.load((ConfigurationSection) raw);
        if (raw instanceof Map) {
            MemoryConfiguration temp = new MemoryConfiguration();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) raw).entrySet()) {
                if (e.getKey() != null) temp.set(e.getKey().toString(), e.getValue());
            }
            return ConditionLoader.load(temp);
        }
        return Condition.none();
    }

    private static Action parseMessage(Object obj) {
        Object raw = ConfigReader.get(obj, "message");
        MessageAction.Builder b = MessageAction.builder();

        if (raw instanceof String) {
            b.text((String) raw);
        } else if (raw instanceof List) {
            for (Object line : (List<?>) raw) {
                if (line != null) b.text(line.toString());
            }
        } else {
            Object text = ConfigReader.get(raw, "text");
            if (text instanceof List) {
                for (Object line : (List<?>) text) {
                    if (line != null) b.text(line.toString());
                }
            } else if (text != null) {
                b.text(text.toString());
            }
        }

        return b.condition(readCondition(obj)).build();
    }

    private static Action parseRandomMessage(Object obj) {
        Object raw = ConfigReader.get(obj, "randomMessage");
        RandomMessageAction.Builder b = RandomMessageAction.builder();
        if (raw instanceof List) {
            b.addAll((List<?>) raw);
        } else if (raw instanceof String) {
            b.add(raw.toString());
        }
        return b.condition(readCondition(obj)).build();
    }

    private static Action parseSound(Object obj) {
        Object raw = ConfigReader.get(obj, "sound");
        SoundAction.Builder b = SoundAction.builder();
        if (raw instanceof String) {
            b.name(raw.toString());
        } else {
            Object name = ConfigReader.get(raw, "name");
            if (name != null) b.name(name.toString());
            Object vol = ConfigReader.get(raw, "volume");
            if (vol != null) b.volume((float) ConfigReader.asDouble(vol));
            Object pitch = ConfigReader.get(raw, "pitch");
            if (pitch != null) b.pitch((float) ConfigReader.asDouble(pitch));
        }
        return b.condition(readCondition(obj)).build();
    }

    private static Action parseTitle(Object obj) {
        Object raw = ConfigReader.get(obj, "title");
        TitleAction.Builder b = TitleAction.builder();
        if (raw instanceof String) {
            b.title(raw.toString());
        } else {
            Object t = ConfigReader.get(raw, "title");
            if (t != null) b.title(t.toString());
            Object sub = ConfigReader.get(raw, "subtitle");
            if (sub != null) b.subtitle(sub.toString());
            Object fi = ConfigReader.get(raw, "fadeIn");
            if (fi != null) b.fadeIn(ConfigReader.asInt(fi));
            Object stay = ConfigReader.get(raw, "stay");
            if (stay != null) b.stay(ConfigReader.asInt(stay));
            Object fo = ConfigReader.get(raw, "fadeOut");
            if (fo != null) b.fadeOut(ConfigReader.asInt(fo));
        }
        return b.condition(readCondition(obj)).build();
    }

    private static Action parseActionBar(Object obj) {
        Object raw = ConfigReader.get(obj, "actionbar");
        ActionBarAction.Builder b = ActionBarAction.builder();
        if (raw instanceof String) {
            b.text(raw.toString());
        } else {
            Object text = ConfigReader.get(raw, "text");
            if (text != null) b.text(text.toString());
        }
        return b.condition(readCondition(obj)).build();
    }

    private static Action parseParticle(Object obj) {
        Object raw = ConfigReader.get(obj, "particle");
        ParticleAction.Builder b = ParticleAction.builder();

        if (raw instanceof String) {
            b.type(raw.toString());
            return b.condition(readCondition(obj)).build();
        }

        Object type = ConfigReader.get(raw, "type");
        if (type != null) b.type(type.toString());
        Object count = ConfigReader.get(raw, "count");
        if (count != null) b.count(ConfigReader.asInt(count));
        Object speed = ConfigReader.get(raw, "speed");
        if (speed != null) b.speed(ConfigReader.asDouble(speed));
        Object offset = ConfigReader.get(raw, "offset");
        if (offset instanceof List) {
            List<?> list = (List<?>) offset;
            if (list.size() >= 1) b.offsetX(ConfigReader.asDouble(list.get(0)));
            if (list.size() >= 2) b.offsetY(ConfigReader.asDouble(list.get(1)));
            if (list.size() >= 3) b.offsetZ(ConfigReader.asDouble(list.get(2)));
        } else {
            Object ox = ConfigReader.get(raw, "offsetX");
            if (ox != null) b.offsetX(ConfigReader.asDouble(ox));
            Object oy = ConfigReader.get(raw, "offsetY");
            if (oy != null) b.offsetY(ConfigReader.asDouble(oy));
            Object oz = ConfigReader.get(raw, "offsetZ");
            if (oz != null) b.offsetZ(ConfigReader.asDouble(oz));
        }
        Object color = ConfigReader.get(raw, "color");
        if (color != null) b.color(color.toString());
        Object size = ConfigReader.get(raw, "size");
        if (size != null) b.size(ConfigReader.asDouble(size));
        Object material = ConfigReader.get(raw, "material");
        if (material != null) b.material(material.toString());
        return b.condition(readCondition(obj)).build();
    }

    private static Action parseCommand(Object obj) {
        Object raw = ConfigReader.get(obj, "command");
        CommandAction.Builder b = CommandAction.builder();
        ConfigReader.eachString(ConfigReader.get(raw, "player"), b::addPlayerCommand);
        ConfigReader.eachString(ConfigReader.get(raw, "console"), b::addConsoleCommand);
        Object asOp = ConfigReader.get(raw, "asOp");
        if (asOp != null) b.asOp(ConfigReader.asBool(asOp));
        return b.condition(readCondition(obj)).build();
    }

    private static Action parseBroadcast(Object obj) {
        Object raw = ConfigReader.get(obj, "broadcast");
        BroadcastAction.Builder b = BroadcastAction.builder();
        if (raw instanceof String) {
            b.text(raw.toString());
        } else {
            Object text = ConfigReader.get(raw, "text");
            if (text != null) b.text(text.toString());
            Object perm = ConfigReader.get(raw, "permission");
            if (perm != null) b.permission(perm.toString());
        }
        return b.condition(readCondition(obj)).build();
    }

    private static Action parseBossBar(Object obj) {
        Object raw = ConfigReader.get(obj, "bossbar");
        BossBarAction.Builder b = BossBarAction.builder();
        Object title = ConfigReader.get(raw, "title");
        if (title != null) b.title(title.toString());
        Object color = ConfigReader.get(raw, "color");
        if (color != null) b.color(color.toString());
        Object style = ConfigReader.get(raw, "style");
        if (style != null) b.style(style.toString());
        Object progress = ConfigReader.get(raw, "progress");
        if (progress != null) b.progress((float) ConfigReader.asDouble(progress));
        Object duration = ConfigReader.get(raw, "duration");
        if (duration != null) b.duration(duration);
        return b.condition(readCondition(obj)).build();
    }

    private static Action parsePotion(Object obj) {
        Object raw = ConfigReader.get(obj, "potion");
        return PotionAction.parse(raw);
    }

    private static Action parseChance(Object obj) {
        Object raw = ConfigReader.get(obj, "chance");
        double pct = 50.0;
        List<Action> inner = new ArrayList<>();
        Object chanceVal = ConfigReader.get(raw, "percent");
        if (chanceVal == null) chanceVal = ConfigReader.get(raw, "chance");
        if (chanceVal != null) pct = ConfigReader.asDouble(chanceVal);
        Object actionsRaw = ConfigReader.get(raw, "actions");
        if (actionsRaw instanceof List) {
            for (Object entry : (List<?>) actionsRaw) {
                if (entry == null) continue;
                Action a = parseAction(entry);
                if (a != null) inner.add(a);
            }
        }
        return ChanceAction.builder()
                .chance(pct)
                .addAll(inner)
                .condition(readCondition(obj))
                .build();
    }

    private static Action parseXp(Object obj) {
        Object raw = ConfigReader.get(obj, "xp");
        return XpAction.parse(raw);
    }

    private static Action parseCloseInventory(Object obj) {
        Condition condition = readCondition(obj);
        return CloseInventoryAction.of(true, condition);
    }

    private static Action parseFirework(Object obj) {
        Object raw = ConfigReader.get(obj, "firework");
        FireworkAction.Builder b = FireworkAction.builder();

        Object type = ConfigReader.get(raw, "type");
        if (type != null) b.type(type.toString());

        Object colors = ConfigReader.get(raw, "colors");
        ConfigReader.eachString(colors, b::addColor);

        Object fade = ConfigReader.get(raw, "fadeColors");
        ConfigReader.eachString(fade, b::addFadeColor);

        Object flicker = ConfigReader.get(raw, "flicker");
        if (flicker != null) b.flicker(ConfigReader.asBool(flicker));

        Object trail = ConfigReader.get(raw, "trail");
        if (trail != null) b.trail(ConfigReader.asBool(trail));

        Object power = ConfigReader.get(raw, "power");
        if (power != null) b.power(ConfigReader.asInt(power));

        return b.condition(readCondition(obj)).build();
    }

    private static Action parseDialog(Object obj) {
        Object raw = ConfigReader.get(obj, "dialog");
        DialogAction.Builder b = DialogAction.builder();

        Object title = ConfigReader.get(raw, "title");
        if (title != null) b.title(title.toString());

        Object body = ConfigReader.get(raw, "body");
        if (body != null) b.body(body.toString());

        Object buttons = ConfigReader.get(raw, "buttons");
        if (buttons instanceof java.util.List) {
            for (Object entry : (java.util.List<?>) buttons) {
                Object label = ConfigReader.get(entry, "label");
                Object playerCmd = ConfigReader.get(entry, "player");
                Object consoleCmd = ConfigReader.get(entry, "console");
                b.addButton(
                        label != null ? label.toString() : "",
                        playerCmd != null ? playerCmd.toString() : null,
                        consoleCmd != null ? consoleCmd.toString() : null);
            }
        }

        return b.condition(readCondition(obj)).build();
    }
}
