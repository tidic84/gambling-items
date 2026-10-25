package dev.gamblingitems.fabric.block;

import net.minecraft.world.item.ItemStack;

/** A cabinet's menu, as {@link CabinetPlay} drives it from the cabinet itself, without a window. */
public interface CabinetMenu {
    /** Whether the stack has a value the machine can take as a stake. */
    boolean accepts(ItemStack stack);
    /** The value of a stack for this machine, 0 when it takes none of it. */
    long valueOf(ItemStack stack);
    /** How many positions or targets the machine offers; 1 when there is nothing to choose. */
    int choices();
    long minimumStake();
    /** Stakes as much of the stack as the machine accepts and plays it, taking it from the stack; false if refused. */
    boolean playFromHand(ItemStack hand, int choice);
    /** Moves the credit and the winnings waiting in the machine to the inventory; false if nothing moved. */
    boolean collectAll();
}
