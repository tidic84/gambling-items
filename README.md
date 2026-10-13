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

## Installation

1. Download the file matching your **exact Minecraft version and mod loader**.
2. Install Fabric, NeoForge or Forge as appropriate for that file. Fabric also requires a compatible **Fabric API**.
3. Put the mod JAR in your instance's `mods` directory. Replace the old JAR when updating.
4. For multiplayer, install the matching mod and dependencies on both the server and every client.

English and French interfaces are included. Loader availability varies by Minecraft version; use the download page to choose a release. Build targets and dependencies are listed in [ports/versions.json](ports/versions.json).

**Version 0.2.1 fixes:** the Forge/NeoForge 1.20.1 startup crash and Minecraft 26.3 Upgrader target selection and roulette left-click issues. These fixes apply to their respective version-specific downloads.

## Configuration

Server settings live in `config/gamblingitems/games.json`. Adjust item values, game settings and exclusions to suit your modpack. The catalogue can estimate values from vanilla and modded crafting recipes; explicit configured values take priority.

An optional casino currency lets you assign values to one or more vanilla or modded items. It is disabled by default. Selected casino games then accept and pay that currency; exchange games keep using the item catalogue. Restart the server after changing currency settings.

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
- [Architecture and porting](ARCHITECTURE.md) (French).
- [CurseForge release preparation](release/curseforge/UPLOAD-FR.md) (French).
- [Minecraft 1.20.1 startup crash diagnosis](docs/launch-crash-1.20.1.md) (French).
- [Minecraft 26.3 mouse input fix](docs/mouse-input-26.3.md).

Authors: **Tidic & Romalaure**. [All Rights Reserved](LICENSE). Third-party components retain their own licenses.
