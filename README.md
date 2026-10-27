# Lightning ⚡

Lightning lets you customize messages across your server, including messages from plugins that were never designed to support MiniMessage. Add gradients, hex colors, reusable text components, interactive messages, and custom commands without modifying individual plugins.

Define reusable styles, create message actions, and build your own command workflows with simple YAML configuration.

Cook with Lightning.

![Lightning on ArrowStats](https://arrowstats.org/plugin/21/image.png)

## Features

### Universal MiniMessage Support
Use MiniMessage formatting in supported messages from virtually any plugin, including those limited to legacy color codes. Add gradients, hex colors, hover events, and click actions without editing each plugin individually.

### Server-Wide Message Customization
Customize chat, action bars, titles, boss bars, tab lists, scoreboards, menus, items, holograms, dialogs, kick and ban screens, and server list messages.

### Smart Tag Detection
Lightning processes messages containing supported tags while leaving ordinary text untouched. Commands such as `/home <name>` remain intact.

### Sparks: Reusable Text Components
Create custom tags for prefixes, color schemes, formatted placeholders, dynamic values, rotating tips, and localized messages. Define them once and reuse them throughout your server.

### Bolts: Message Actions
Go beyond formatting with tags that trigger actions, including action bar messages, titles, sounds, and message suppression.

### Player Head Integration
Use the `<face>` tag to display a player's own head directly in supported text components. Requires Minecraft 1.21.9 or newer.

### Rewires: Override Existing Messages
Restyle messages you cannot easily edit through a plugin's configuration, including vanilla server screens and hardcoded plugin messages where supported.

### Custom Commands
Create your own commands using YAML. Configure arguments, tab completion, subcommands, cooldowns, execution limits, and configurable actions.

### Built-In Configuration Tools
Trace messages back to their source, identify broken tags across plugin configurations, convert legacy `&` color codes to MiniMessage with automatic backups, and inspect how messages are processed.

### Safe and Lightweight
Player-supplied text remains plain text rather than being interpreted as formatting instructions. Ordinary messages avoid unnecessary processing, and reusable results are cached to reduce overhead.

## Requirements

- Paper or Folia 1.21 or newer
- Java 21
- PlaceholderAPI (optional)

## Installation

1. Download the Lightning JAR.
2. Place it in your server's `plugins/` folder.
3. Restart your server.

PacketEvents is downloaded on first startup, so no separate installation is required.

## Configuration Examples

### MiniMessage in Any Plugin's Configuration

With Lightning installed, supported messages in other plugins' configurations can use MiniMessage formatting.

```yaml
welcome: "<gradient:#FFE259:#FFA751>Welcome back, %player_name%!</gradient>"
home-set: "<actionbar><green>Home set.<sound:entity.player.levelup>"
balance: "<spark:prefix> You have <spark:balance>"
```

### Reusable Sparks

Define reusable text components in `plugins/Lightning/sparks/` and use them throughout your configurations.

```yaml
balance: "<spark:prefix> You have <spark:balance>"
```

### Custom Commands

Define your own commands, arguments, and actions through YAML configuration.

```yaml
discord:
  description: Get the Discord link
  actions:
    - "<spark:prefix> Join us at discord.gg/example"
```

## Commands

| Command | Description |
|---|---|
| `/lightning reload` | Reload Lightning's configuration and resources. |
| `/lightning parse [surface] <text>` | Preview how text will be processed. |
| `/lightning sparks` | List available sparks. |
| `/lightning inspect [chat\|actionbar]` | Trace messages back to their source. |
| `/lightning check [plugin]` | Find unsupported or incorrectly written tags. |
| `/lightning convert <plugin>` | Convert legacy `&` color codes to MiniMessage, creating a backup. |
| `/lightning debug` | Show why Lightning processes or skips messages in the console. |

All commands require `lightning.admin`. You can also use `/lt`.

## Documentation

Read the full guide at [hub.denizenmc.com/docs/lightning](https://hub.denizenmc.com/docs/lightning).

## Building

Run `./gradlew build`. The compiled JAR will be available in `build/libs/`.

Dependencies are not shaded into the JAR. Paper's plugin loader handles PacketEvents at runtime.

## Support

Need help, found a bug, or have a feature request?

- [Documentation](https://hub.denizenmc.com/docs/lightning)
- [Support Discord](https://support.denizenmc.com)

## License

Lightning is licensed under **GPL-3.0-or-later**. See [LICENSE](LICENSE) for details.
