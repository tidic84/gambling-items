//#if MC >= 1.21.6
//$ package dev.gamblingitems.fabric.client.mixin;
//$
//$ import net.minecraft.client.gui.GuiGraphics;
//$ import net.minecraft.client.gui.render.state.GuiRenderState;
//$ import org.spongepowered.asm.mixin.Mixin;
//$ import org.spongepowered.asm.mixin.gen.Accessor;
//$
//$ /** Since 1.21.6 the interface is a list of render states; the rounded shapes add their own. */
//$ @Mixin(GuiGraphics.class)
//$ public interface GuiGraphicsAccessor {
//$     @Accessor("guiRenderState") GuiRenderState gamblingitems$state();
//$ }
//#endif
