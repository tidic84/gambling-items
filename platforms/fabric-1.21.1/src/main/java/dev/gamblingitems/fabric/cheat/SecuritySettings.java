package dev.gamblingitems.fabric.cheat;

import dev.gamblingitems.core.cheat.CheatRules;
import dev.gamblingitems.core.cheat.CheatRules.Tool;
import java.util.EnumMap;
import java.util.Map;

/**
 * How closely this server watches its casino.
 *
 * @param enabled        false turns every trick off: the tools still exist but do nothing
 * @param rules          detection, suspicion and bans
 * @param baseChance     how likely each tool is to be spotted on a cold player, in basis points
 * @param heat           how much suspicion each tool adds when it goes unnoticed
 * @param cameraRadius   how far a security camera sees, in blocks
 * @param watchLeadTicks how long before the crash a rigged watch vibrates
 */
public record SecuritySettings(boolean enabled, CheatRules.Settings rules, Map<Tool, Integer> baseChance,
                               Map<Tool, Integer> heat, int cameraRadius, int watchLeadTicks) {
    public SecuritySettings {
        baseChance = Map.copyOf(baseChance);
        heat = Map.copyOf(heat);
        for (Tool tool : Tool.values()) {
            int chance = baseChance.getOrDefault(tool, -1), warmth = heat.getOrDefault(tool, -1);
            if (chance < 0 || chance > CheatRules.CERTAIN || warmth < 0 || warmth > CheatRules.MAX_HEAT) {
                throw new IllegalArgumentException("Invalid tuning for the cheat tool " + tool.id());
            }
        }
        if (cameraRadius < 0 || cameraRadius > 32) throw new IllegalArgumentException("Camera radius must be 0 to 32");
        if (watchLeadTicks < 1 || watchLeadTicks > 200) throw new IllegalArgumentException("Watch lead must be 1 to 200 ticks");
    }

    public static SecuritySettings defaults() {
        Map<Tool, Integer> chances = new EnumMap<>(Tool.class), heat = new EnumMap<>(Tool.class);
        for (Tool tool : Tool.values()) {
            chances.put(tool, tool.defaultBaseChance());
            heat.put(tool, tool.defaultHeat());
        }
        return new SecuritySettings(true, CheatRules.Settings.defaults(), chances, heat, 10, 10);
    }

    public int baseChance(Tool tool) { return baseChance.get(tool); }
    public int heat(Tool tool) { return heat.get(tool); }
}
