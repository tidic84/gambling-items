# Gambling Items 0.2.0-beta.7

Minecraft Java 1.21.1 · Fabric · Java 21

## Casino interfaces

- New casino look for every game window: night-blue room, rounded cards, a green button for what you stake, gold for what you win.
- Minecraft's own font at native GUI size; the window is only scaled down when it would not fit.
- Every game uses the same layout: the game on the left, odds on the right, your full 9x4 inventory and a betting slip with one big action button.
- Two portable items, each with its games as tabs: the **Exchange terminal** (Trade Up, Upgrader, Cases, Case battles) and the new **Pocket casino** (Crash, roulette, blackjack, bingo, slots). Each reopens the last game played.
- The Pocket casino joins a nearby table, or opens one where you stand.
- Switching tab keeps the mouse cursor where it was.
- Opened from their block, games show on their own, without tabs.
- Reels slow down like a real case opening, tick as items pass the marker, and the winning line lights up in the odds list.
- Win and loss sounds; the action button collects the reward once the reel stops.
- Odds lists scroll with the mouse wheel; the Upgrader lists the best prizes first and reachable targets by odds once an item is staked.

## Removed

- The nine single-game handheld items (`*_item`) and their recipes, replaced by the two portable items above; existing copies disappear from worlds.

## New: casino currency

- Server owners can now pick a currency for the casino games (Crash, roulette, blackjack, bingo and slots) in `config/gamblingitems/games.json`.
- A currency is one or more items, vanilla or modded, each with its own worth, for example a silver coin worth 1 and a gold coin worth 10.
- With a currency enabled, only its items can be staked and every win is paid in them, dearest coin first. No more random item payouts.
- Each casino game can be switched on or off individually. Upgrader, Trade Up, cases and case battles keep using item values.
- Disabled by default: existing servers keep playing with priced items. Older configuration files are migrated automatically.

## Other changes

- Roulette and blackjack table model updates and various fixes since 0.2.0-beta.1.

## Requirements and limits

- Fabric Loader 0.16.14+ and Fabric API 0.116.7+1.21.1, on Minecraft 1.21.1.
- Install on both client and server for multiplayer.
- A win below the cheapest currency item cannot be paid and is rounded down.
- Restart the server after changing the currency: shared tables keep the settings they were created with.
- Beta: manual client gameplay and broader modpack testing remain outstanding.
