# Gambling Items 0.2.1 — Minecraft 1.20.1 Forge / NeoForge

Fixes a client startup crash in the 0.2.0 release for Minecraft 1.20.1.

The mouse mixin configuration now references its bundled refmap, allowing Forge and NeoForge to resolve the production method and field names. No additional mod dependency is required for this fix.

The build now verifies that the production JAR declares and includes the required mouse mixin mappings. Replace the previous Gambling Items JAR; do not install both versions together.
