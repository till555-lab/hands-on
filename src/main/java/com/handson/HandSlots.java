package com.handson;

import java.lang.reflect.Method;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

/** The two hand slots: extra slots of the player's inventory menu (index 46 = left hand, 47 = right hand). */
public class HandSlots {
    public static final int VANILLA_SLOTS = 46;
    public static final int LEFT = 46;
    public static final int RIGHT = 47;

    /** Position of the slots in the vanilla inventory screen (relative to the screen, right of the panel). */
    public static final int SCREEN_X = 180;
    public static final int SCREEN_Y_LEFT = 24;
    public static final int SCREEN_Y_RIGHT = 58;

    private static Method addSlot;
    private static boolean failed = false;

    /** Adds the two slots to the inventory menu of the player (the same code runs on client and server). */
    public static void install(PlayerEntity player) {
        if (failed) {
            return;
        }
        Container menu = player.inventoryMenu;
        if (menu.slots.size() != VANILLA_SLOTS) {
            return;
        }
        try {
            if (addSlot == null) {
                addSlot = ObfuscationReflectionHelper.findMethod(Container.class, "func_75146_a", Slot.class);
            }
            HandsInventory inv = new HandsInventory(player);
            addSlot.invoke(menu, new Slot(inv, 0, SCREEN_X, SCREEN_Y_LEFT));
            addSlot.invoke(menu, new Slot(inv, 1, SCREEN_X, SCREEN_Y_RIGHT));
        } catch (Exception e) {
            failed = true;
            e.printStackTrace();
        }
    }

    public static boolean ready(PlayerEntity player) {
        return player.inventoryMenu.slots.size() > RIGHT;
    }

    /** hand: 0 = left, 1 = right. */
    public static ItemStack get(PlayerEntity player, int hand) {
        if (!ready(player)) {
            return ItemStack.EMPTY;
        }
        return player.inventoryMenu.getSlot(LEFT + hand).getItem();
    }

    public static void set(PlayerEntity player, int hand, ItemStack stack) {
        if (!ready(player)) {
            return;
        }
        player.inventoryMenu.getSlot(LEFT + hand).set(stack);
    }
}
