package dev.gamblingitems.fabric.client.mixin;

import dev.gamblingitems.fabric.client.ClientSmoke;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** During the automated interface check the game never captures the cursor of the person at the desk. */
@Mixin(MouseHandler.class)
public abstract class MouseGrabMixin {
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void gamblingitems$keepCursor(CallbackInfo info) {
        if (ClientSmoke.ENABLED) info.cancel();
    }
}
