# Gambling Items 0.2.0-beta.2

Minecraft Java 1.21.1 · Fabric · Java 21

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
