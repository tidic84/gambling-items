package dev.gamblingitems.fabric.block;

import dev.gamblingitems.core.GameMode;

/**
 * Anything a game can be played on: the wall monitor of a station, or a table standing on the floor.
 * A menu only needs to know which game the block in front of the player hosts.
 */
public interface GameSurface {
    GameMode mode();
}
