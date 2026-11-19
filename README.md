## Lightning
[![Paper & Folia](https://tools.denizenmc.com/api/cloud/raw/dNCtFT6Rtt3N.png)](https://papermc.io) [![Java 21](https://tools.denizenmc.com/api/cloud/raw/g8AGSg245TBX.png)](https://docs.papermc.io/misc/java-install) [![Documentation](https://tools.denizenmc.com/api/cloud/raw/wEKeXC9SUNo3.png)](https://hub.denizenmc.com/docs/lightning) [![Discord](https://tools.denizenmc.com/api/cloud/raw/ESwZALwTz4Sp.png)](https://support.denizenmc.com/)

Lets you use [MiniMessage](https://docs.advntr.dev/minimessage/format) in messages from any plugin, including plugins that only support legacy `&` codes. See our [documentation](https://hub.denizenmc.com/docs/lightning) for more information.

On top of formatting, Lightning adds:
* **Sparks**: reusable tags for prefixes, colors, placeholders and translations
* **Bolts**: tags that send titles or action bars, play sounds, or hide a message
* **Rewires**: restyle hardcoded and vanilla messages where supported
* **Custom commands**: commands with arguments, cooldowns and permissions, written in YAML

Requires Paper or Folia 1.21+ and Java 21. See [here](https://docs.papermc.io/misc/java-install) how to update your installed Java version. [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi) is optional.

## Note on processing
Lightning only touches messages that contain its tags, so commands like `/home <name>` stay as they are. Player input is always treated as plain text, never as formatting.

## Usage
Once Lightning is installed, MiniMessage works in other plugins' configs:
```yaml
welcome: "<gradient:#FFE259:#FFA751>Welcome back, %player_name%!</gradient>"
home-set: "<actionbar><green>Home set.<sound:entity.player.levelup>"
```

## Releases
You can find official releases in the following places:
* Modrinth: https://modrinth.com/plugin/lightning-was-taken
* GitHub: https://github.com/EliteDenizen/Lightning
* DenizenMC: https://denizenmc.com/store/lightning

## Support
Questions and bug reports go to our [Discord server](https://support.denizenmc.com/).

## Statistics
Lightning collects anonymous statistics through Arrow Stats, an open-source statistics service. You can opt out in `plugins/Arrow Stats/config.yml`.
[![View Lightning statistics on Arrow Stats](https://arrowstats.org/plugin/21/image.png)](https://arrowstats.org/plugin/21)
