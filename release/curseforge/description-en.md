# Gambling Items

Turn your Minecraft inventory into the stakes for nine games. Craft a dedicated handheld device to play anywhere, or build a gaming room with animated stations, tables and a slot machine.

## Nine games, nine handheld items

- **Upgrader:** choose the item you want to win. Your stake and the target's value determine the chance shown before you play.
- **Trade Up:** exchange five items of similar value for a reward from the displayed contract.
- **Cases:** open six tiers of loot cases using keys dropped by mobs.
- **Crash:** place item stakes, watch the shared multiplier rise and cash out before it crashes.
- **Roulette:** play a European wheel with numbers 0–36 and multiple types of bets.
- **Blackjack:** play against the dealer, with 3:2 blackjack payouts and returned stakes on ties.
- **Case Battles:** two to four players open the same cases; the highest total wins the rewards.
- **Bingo:** buy a card and follow a shared draw.
- **Slot Machine:** spin three animated reels with pixel-art symbols and a visible payout table.

Each game has its own craftable round token with a distinct emblem, such as cards for blackjack, a wheel for roulette and triple sevens for slots. Tokens are small in hand and held like ordinary items. Right-click one to open that game without placing a block. A portable terminal also provides access to all nine games.

For Crash, roulette, bingo and case battles, handheld devices join an existing table within 64 blocks or create one at your position. Nearby players can join with their own devices. A case battle still needs multiple participants.

## Build a gaming room

Place game stations, roulette and blackjack tables, a bingo table, or a red-and-steel slot machine. Shared displays show ongoing games to nearby players. Portable devices and blocks use the same per-player winnings storage for their corresponding game.

## Choose your Upgrader reward

Put a stack into the stake slot, search for a target, and select it. Search supports item names, identifiers such as `minecraft:diamond`, and mod namespaces such as `@yourmod`.

The target's value must exceed the value of your stake. A more valuable target gives a lower chance. For example, at a 90% configured return rate, a stake worth 1 has a 9% chance of winning a target worth 10, or a 4.5% chance of winning one worth 20.

## Recipe-based values and modpack settings

The server builds an item catalogue from loaded vanilla, mod and datapack recipes, including smithing transformations. Values account for ingredients, output quantities and alternative recipes. More than 1,000 items are available in the tested vanilla configuration.

Server owners can set explicit prices, namespace fallback values and item exclusions in `config/gamblingitems/games.json`. The generated `resolved-values.json` report shows the final prices and identifies estimated values. Run `/reload` and reopen a game to load updated settings; active rounds keep their existing settings.

Automatic prices are estimates. Custom machines may use energy, fluids or recipe data that the mod cannot interpret. Unknown resources receive configurable fallback prices. Dynamic recipes without an identifiable output are reported. Items with custom names, enchantments, stored contents or extra components are not accepted as ordinary stakes. Case keys are kept outside the value market.

## Installation

1. Use **Minecraft Java 1.21.1** with **Java 21** and **Fabric Loader 0.16.14 or newer**.
2. Install [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api), version **0.116.7+1.21.1 or a newer version compatible with Minecraft 1.21.1**.
3. Put the Gambling Items JAR in the `mods` folder.
4. For multiplayer, install the mod and Fabric API on both the server and every client.

English and French translations are included. This build targets Fabric 1.21.1; Forge, NeoForge and other Minecraft versions are not supported.

## Beta status

This is a beta release. Core rule tests and Fabric server integration tests pass. Broader modpack compatibility and manual client gameplay testing are still needed. Back up an existing world before updating a beta.

**Author:** Tidic & Romalaure  
**License:** All Rights Reserved
