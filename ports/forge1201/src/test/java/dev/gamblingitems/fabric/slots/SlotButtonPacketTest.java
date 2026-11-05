package dev.gamblingitems.fabric.slots;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Exercise the real 1.20.1 wire format, which direct menu GameTests bypass. */
class SlotButtonPacketTest {
    private static int receivedButton(int button) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new ServerboundContainerButtonClickPacket(1, button).write(buffer);
            ServerboundContainerButtonClickPacket received =
                    new ServerboundContainerButtonClickPacket(buffer);
            assertEquals(1, received.getContainerId());
            return received.getButtonId();
        } finally {
            buffer.release();
        }
    }

    @Test void spinSurvivesTheClientServerPacket() {
        assertEquals(SlotMenu.SPIN_BUTTON, receivedButton(SlotMenu.SPIN_BUTTON));
    }

    @Test void collectSurvivesTheClientServerPacket() {
        assertEquals(SlotMenu.COLLECT_BUTTON, receivedButton(SlotMenu.COLLECT_BUTTON));
        assertNotEquals(SlotMenu.SPIN_BUTTON, SlotMenu.COLLECT_BUTTON);
    }

    @Test void originalSpinIdWasTruncatedByMinecraft() {
        assertEquals(-24, receivedButton(1000));
    }
}
