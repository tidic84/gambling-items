# Gambling Items 0.2.2

## Fixes

- Fixed game buttons and portable-device tabs not responding on Minecraft 1.20.1, including starting Slots with diamonds or other valued items and collecting winnings.
- Fixed large Upgrader target selections and the other games' actions being truncated by the Minecraft 1.20.1 button packet. All nine games now use a full-width action packet on that version.
- Game menus now validate their owner and access before processing portable-device tabs.

## Enchanted books in the Upgrader

- Enchanted books can now be selected as rewards and used as stakes when they match a configured variant.
- Includes eight default variants: Mending I, Sharpness V, Unbreaking III, Efficiency V, Fortune III, Silk Touch I, Looting III and Protection IV.
- Search for books by their enchantment names. Servers can configure prices, levels and combinations in `upgrader.enchantedBooks` in `config/gamblingitems/games.json`.
- Existing configuration files are migrated automatically while preserving custom settings.

Install the matching Minecraft and loader build on both the server and every client. Fabric builds require Fabric API. Replace the previous mod JAR; do not install both versions together.

The separate report that only a multiplayer host can use a portable device is still under investigation. This release does not claim to resolve that report.
