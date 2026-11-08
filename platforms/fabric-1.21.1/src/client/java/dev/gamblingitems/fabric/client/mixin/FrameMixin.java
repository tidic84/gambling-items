package dev.gamblingitems.fabric.client.mixin;

import dev.gamblingitems.fabric.client.WorldCanvas;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts the frames, for what the furniture draws once in each, and times them during the frame-rate measure. */
@Mixin(Minecraft.class)
public abstract class FrameMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void gamblingitems$frame(boolean tick, CallbackInfo info) {
        WorldCanvas.frame();
    }
}
