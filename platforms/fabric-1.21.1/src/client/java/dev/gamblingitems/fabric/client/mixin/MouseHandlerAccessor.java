package dev.gamblingitems.fabric.client.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets a game window put the cursor back where it was when the terminal changes game. */
@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
    @Accessor("xpos") void gamblingitems$setX(double x);
    @Accessor("ypos") void gamblingitems$setY(double y);
}
