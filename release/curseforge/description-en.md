# Gambling Items

Turn your Minecraft inventory into the stakes of nine casino games. Trade up and upgrade your items on a portable terminal, carry a pocket casino, or build a gaming room with animated stations, tables and a slot machine.

## Two portable items

- **Exchange terminal:** the item games, as tabs — Trade Up, Upgrader, Cases and Case Battles.
- **Pocket casino:** the casino games, as tabs — Crash, Roulette, Blackjack, Bingo and Slots. It joins a table within 64 blocks, or opens one where you stand.

Each item reopens the last game you played. Switching tab keeps your cursor where it was.

## Nine games

- **Trade Up:** exchange five items of similar value for a reward from the displayed contract, with every chance shown before you play.
- **Upgrader:** stake an item and choose the one you want to win. The value of your stake and of your target set the odds, shown on a ring before you spin.
- **Cases:** open loot cases with keys dropped by mobs. The reel slows down like a real case opening and stops on what the server drew.
- **Case Battles:** two to four players open the same cases; the highest total takes everything.
- **Crash:** stake items, watch the shared multiplier climb and cash out before it crashes.
- **Roulette:** a European wheel with numbers 0–36 and every classic bet.
- **Blackjack:** play against the dealer, with 3:2 blackjack payouts and returned stakes on ties.
- **Bingo:** buy a card and follow a shared draw.
- **Slot Machine:** three animated reels with pixel-art symbols and a visible payout table.

## A casino interface

Every game uses the same layout: the game on the left, the odds on the right, your full inventory and a betting slip with one big button. Green stakes, gold pays. Reels tick as items pass the marker, winning lines light up, and wins and losses have their sound.

## Build a gaming room

Place game stations, roulette, blackjack and bingo tables, or a red-and-steel slot machine. Their screens show the games in progress to nearby players, and the tables can be played directly on the felt. Opened from a block, a game shows on its own, without tabs.

## Recipe-based values and modpack settings

The server builds an item catalogue from the loaded vanilla, mod and datapack recipes. Values account for ingredients, output quantities and alternative recipes.

Server owners can set explicit prices, namespace fallback values, item exclusions and a casino currency in `config/gamblingitems/games.json`. The generated `resolved-values.json` report shows the final prices. Run `/reload` to load updated settings; rounds in progress keep their settings.

Automatic prices are estimates: custom machines may use energy, fluids or recipe data the mod cannot read, and receive configurable fallback prices. Items with custom names, enchantments or stored contents are not accepted as ordinary stakes.

## Versions

One file per Minecraft version and loader, from **Minecraft 1.20.1 to 26.3**, for **Fabric** and **NeoForge** (Forge 47 on 1.20.1). Pick the file that matches your exact Minecraft version and loader.

- Fabric: 1.20.1–1.20.4, 1.20.6, 1.21–1.21.11, 26.1–26.3. Needs [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api).
- NeoForge: 1.20.4, 1.20.6, 1.21–1.21.11, 26.1–26.3; Forge on 1.20.1.
- Java 17 for 1.20.1–1.20.4, Java 21 for 1.20.6–1.21.11, Java 25 for 26.x.
- In multiplayer, install the same file on the server and on every client.

English and French translations are included.

**Authors:** Tidic & Romalaure  
**License:** All Rights Reserved
