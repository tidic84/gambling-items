# Minecraft 26.3 mouse input fix

Minecraft 26.3 changed its input backend to SDL. The left mouse button identifier is 1 in 26.3, whereas previous versions used 0. The Upgrader and Roulette screens compared the incoming button against the literal 0, so left clicks were ignored when choosing an upgrade target or placing a roulette bet.

The canonical screen code now compares against `InputConstants.MOUSE_BUTTON_LEFT`, allowing each target Minecraft version to supply its own identifier.

The 0.2.1 release was checked in isolated Minecraft 26.3 clients:

- Fabric Loader 0.19.5 with Fabric API 0.160.5+26.3.
- NeoForge 26.3.0.57-beta.

On both loaders, a stack of 64 diamonds could select a netherite block target at a displayed 25.81% chance, and the upgrade round completed. Roulette accepted a 640-value bet on red and started its countdown.

The published 26.3 JARs were created by applying a targeted ASM 9.9.1 bytecode patch to the audited 0.2.0 releases. Only `UpgradeScreen.class`, `RouletteScreen.class` and the mod version metadata changed. The canonical source fix is included here for future builds. These release artifacts were not produced by a complete Gradle rebuild.

These checks cover the reported interactions in singleplayer with the integrated server. They do not constitute a full multiplayer or all-games regression test.
