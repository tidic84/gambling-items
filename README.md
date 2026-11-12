# Gambling Items

Nine item-based games for Minecraft Java Edition, with portable devices, animated machines and shared multiplayer tables.

[Download on CurseForge](https://www.curseforge.com/minecraft/mc-mods/gambling-items) · [Join the Discord](https://discord.gg/cDzqrK5mN6)

## Games

The **Exchange terminal** opens Trade Up, Upgrader, Cases and Case Battles. The **Pocket casino** opens Crash, Roulette, Blackjack, Bingo and Slots. Place stations and tables to build a gaming room with displays visible to nearby players.

| Game | How it works |
| --- | --- |
| Trade Up | Exchange five items of comparable value for a reward from the displayed contract. |
| Upgrader | Choose a more valuable item from the searchable catalogue; inspect the success chance before committing your stake. |
| Cases | Open a case with a matching key. Six rarities have their own rewards and displayed probabilities. |
| Case Battles | Two to four players open cases together; the highest total takes the rewards. |
| Crash | Follow a shared multiplier and cash out before the round crashes. |
| Roulette | Bet on a European wheel with numbers, colours, dozens and columns. |
| Blackjack | Play against the dealer with Hit, Stand and Double Down. A natural blackjack pays 3:2. |
| Bingo | Buy a card and follow the shared draw. |
| Slots | Spin three reels and check the payout table beside them. |

Games use Minecraft items. The server handles outcomes, stakes and unclaimed rewards. Shared rounds continue when an interface closes.

### Cheating and casino security

Four craftable tricks bend one casino game each. Carry one and a red button appears in the header of its game, showing the risk of being spotted.

| Tool | Game | Effect |
| --- | --- | --- |
| Marked Card Lens | Blackjack | Reads the dealer's hole card and the next card of the shoe, once per hand. |
| Roulette Magnet | Roulette | Drags the ball one pocket aside when that pays you more. |
| Reel Shim | Slots | Your next pull keeps the better of two lines. |
| Rigged Watch | Crash | Vibrates just before the round crashes. |

Every use is a server-side detection roll. The chance grows with the player's suspicion, which builds up with each unnoticed trick and cools down over time, and with every **Security Camera** within sight. A caught cheater loses the tool and the stake of the round, is announced to nearby players and is banned from every game for a while. Operators can read a file with `/gamblingitems security status <player>` and clear it with `/gamblingitems security pardon <player>`.

### Prediction market

Bet YES or NO on what the players will do: earn an advancement, die a number of times, mine diamonds, kill mobs, or a question written on a paper named at an anvil. Open it from the **Predictions** tab of the pocket casino or from an **Odds Board**, which also shows the busiest questions to the room.

Prices come from an automated market maker (logarithmic market scoring rule), so the price of YES reads as a probability and a question can be traded even by a single player. Each purchase gives a physical ticket that can be traded, stored, stolen and sold back before the deadline. The game settles questions about players by itself. A written question is answered by its creator, can be disputed for a few minutes, and is then decided by an operator with `/gamblingitems market resolve <id> yes|no|cancel`. Anyone can open a question by depositing its subsidy; the creator earns the trading fees and what the market maker did not lose. The house also opens questions about online players on its own.

A player who bets on their own failure, such as YES on their own deaths, may have the winning ticket seized by the gaming commission and be banned.

## Installation

1. Download the file matching your **exact Minecraft version and mod loader**.
2. Install Fabric, NeoForge or Forge as appropriate for that file. Fabric also requires a compatible **Fabric API**.
3. Put the mod JAR in your instance's `mods` directory. Replace the old JAR when updating.
4. For multiplayer, install the matching mod and dependencies on both the server and every client.

English and French interfaces are included. Loader availability varies by Minecraft version; use the download page to choose a release. Build targets and dependencies are listed in [ports/versions.json](ports/versions.json).

**Version 0.2.1 fixes:** the Forge/NeoForge 1.20.1 startup crash and Minecraft 26.3 Upgrader target selection and roulette left-click issues. These fixes apply to their respective version-specific downloads.

**Version 0.2.2:** fixes game buttons and portable-device tabs on Minecraft 1.20.1, validates access before tab changes, and adds configurable enchanted books to the Upgrader. Update both the server and every client together. See the [0.2.2 changelog](release/curseforge/changelog-0.2.2.md).

## Configuration

Server settings live in `config/gamblingitems/games.json`. Adjust item values, game settings and exclusions to suit your modpack. The catalogue can estimate values from vanilla and modded crafting recipes; explicit configured values take priority.

An optional casino currency lets you assign values to one or more vanilla or modded items. It is disabled by default. Selected casino games then accept and pay that currency; exchange games keep using the item catalogue. Restart the server after changing currency settings.

The Upgrader supports enchanted books such as Mending and Sharpness V, including configured combinations. Set their enchantments, levels and prices in `upgrader.enchantedBooks`; eight examples are added automatically to existing configurations. Books can be selected as rewards or used as stakes when they exactly match a configured variant.

The `security` section tunes cheat detection: each tool's base chance and suspicion, the weight of suspicion and cameras, the ban length and the camera range. Set `enabled` to `false` to turn every trick off. The `predictionMarket` section sets the value of a winning share, the creator fee, the smallest subsidy, the dispute windows, the gaming commission and the questions opened by the house (`houseMarkets`).

See [portable devices and item values](ITEMS_AND_VALUES.md) for recipes and the limits of automatic valuation. This detailed guide is currently in French.

## Development

The canonical development target is **Minecraft 1.21.1 with Java 21**. Install a JDK 21 and run these commands from the repository root:

```powershell
# Build the Minecraft-independent core and run its tests
.\gradlew.bat build

# Build Fabric or NeoForge 1.21.1
.\gradlew.bat buildFabric
.\gradlew.bat buildNeoForge

# Launch a development client
.\gradlew.bat runClient
.\gradlew.bat runNeoForgeClient

# Launch a development server; accept the Minecraft EULA yourself first
.\gradlew.bat runServer
.\gradlew.bat runNeoForgeServer

# Run port resource checks (requires Python)
python -m unittest discover -s tools/tests
```

On Linux and macOS, use `bash ./gradlew` instead of `.\gradlew.bat`. The wrapper downloads Gradle; platform builds also download Minecraft dependencies.

The Fabric JAR is written to `platforms/fabric-1.21.1/build/libs/`; the NeoForge JAR is written to `platforms/neoforge-1.21.1/build/libs/`. Use the JAR without the `-sources` suffix. Both include the core; do not install a separate core JAR.

### Other Minecraft versions

Port builds generate sources from the canonical tree using [tools/port.py](tools/port.py). Edit canonical sources and version conditionals rather than generated files. Install the target JDK: Java 17 for older 1.20 versions, Java 21 for 1.20.6 and 1.21 versions, and Java 25 for 26.x.

For Minecraft 26.3, use a compatible Gradle runtime and a JDK 25 toolchain:

```powershell
.\ports\gradlew.bat -p ports/fabric26 -Pminecraft_version=26.3 -Ppython=python build
.\ports\gradlew.bat -p ports/neoforge26 -Pminecraft_version=26.3 -Ppython=python build
```

Port output is configured in [ports/common.gradle](ports/common.gradle), under `~/.cache/gambling-items-ports/` by default.

## Repository layout

| Path | Contents |
| --- | --- |
| `core/src/` | Java game rules and tests without Minecraft dependencies. |
| `platforms/fabric-1.21.1/` | Fabric integration and canonical shared Minecraft code. |
| `platforms/neoforge-1.21.1/` | NeoForge integration using the shared game sources. |
| `platforms/forge-1.20.1/` | Loader-specific integration for the legacy Forge port. |
| `ports/` | Version-specific build projects and dependency matrix. |
| `tools/` | Source generation and resource verification. |
| `release/curseforge/` | Release instructions and changelogs. |

Open `platforms/fabric-1.21.1` in your IDE for Minecraft development, or the repository root to work on the standalone core.

## Further documentation

- [Game design](CONCEPTION.md) (French).
- [Cheating and prediction market](docs/triche-et-pronostics.md) (French).
- [Architecture and porting](ARCHITECTURE.md) (French).
- [CurseForge release preparation](release/curseforge/UPLOAD-FR.md) (French).
- [Minecraft 1.20.1 startup crash diagnosis](docs/launch-crash-1.20.1.md) (French).
- [Minecraft 26.3 mouse input fix](docs/mouse-input-26.3.md).

Authors: **Tidic & Romalaure**. [All Rights Reserved](LICENSE). Third-party components retain their own licenses.
