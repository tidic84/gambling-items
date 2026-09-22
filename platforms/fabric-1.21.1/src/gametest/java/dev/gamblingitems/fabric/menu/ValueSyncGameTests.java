package dev.gamblingitems.fabric.menu;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.SimpleContainerData;

/**
 * A data slot travels as a short. These tests hold every value a screen shows to what actually
 * survives that trip, so a win never comes back as a negative number.
 */
public class ValueSyncGameTests implements FabricGameTest {
    /** What the client ends up with: the server slots, truncated the way the packet truncates them. */
    private static SimpleContainerData received(SimpleContainerData sent) {
        SimpleContainerData client = new SimpleContainerData(sent.getCount());
        for (int index = 0; index < sent.getCount(); index++) client.set(index, (short) sent.get(index));
        return client;
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aValueSurvivesTheTripToTheScreen(GameTestHelper helper) {
        long[] values = {0, 1, 999, 32_767, 32_768, 50_000, 1_000_000, 10_000_000, ValueSync.MAX};
        for (long value : values) {
            SimpleContainerData data = new SimpleContainerData(ValueSync.SLOTS);
            ValueSync.write(data, 0, value);
            for (int index = 0; index < ValueSync.SLOTS; index++) {
                helper.assertTrue(data.get(index) >= Short.MIN_VALUE && data.get(index) <= Short.MAX_VALUE,
                        "Slot " + index + " of " + value + " must fit in what a packet sends");
            }
            helper.assertTrue(ValueSync.read(received(data), 0) == value,
                    "The screen reads back " + value);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void aRawValueWouldHaveComeBackNegative(GameTestHelper helper) {
        // The bug this guards against: five diamonds are worth fifty thousand, which is not a short.
        SimpleContainerData naive = new SimpleContainerData(1);
        naive.set(0, 50_000);
        helper.assertTrue(received(naive).get(0) < 0, "A raw value above a short comes back negative");
        SimpleContainerData safe = new SimpleContainerData(ValueSync.SLOTS);
        ValueSync.write(safe, 0, 50_000);
        helper.assertTrue(ValueSync.read(received(safe), 0) == 50_000, "Split in two, it arrives intact");
        helper.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void whatCannotBeSentIsCappedNeverWrapped(GameTestHelper helper) {
        SimpleContainerData data = new SimpleContainerData(ValueSync.SLOTS);
        ValueSync.write(data, 0, ValueSync.MAX + 1_000);
        helper.assertTrue(ValueSync.read(received(data), 0) == ValueSync.MAX, "A huge value stops at the cap");
        ValueSync.write(data, 0, -5);
        helper.assertTrue(ValueSync.read(received(data), 0) == 0, "A negative value is never sent");
        helper.succeed();
    }
}
