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

    public static final int RING_START = 48;
    public static final int RING_COUNT = 24;
    public static final int RING_END = RING_START + RING_COUNT; // 72

    public static final int SCREEN_RING_X = 180;
    public static final int SCREEN_RING_Y = 82;

    public static final int SCREEN_X = 180;
    public static final int SCREEN_Y_LEFT = 24;
    public static final int SCREEN_Y_RIGHT = 58;

    private static Method addSlot;
    private static boolean failed = false;

    public static void install(PlayerEntity player) {
        if (failed) return;
        Container menu = player.inventoryMenu;
        // Only install if exactly vanilla slots present (not yet installed)
        if (menu.slots.size() != VANILLA_SLOTS) return;
        try {
            if (addSlot == null) {
                addSlot = ObfuscationReflectionHelper.findMethod(Container.class, "func_75146_a", Slot.class);
            }
            HandsInventory inv = new HandsInventory(player);
            addSlot.invoke(menu, new Slot(inv, 0, SCREEN_X, SCREEN_Y_LEFT));
            addSlot.invoke(menu, new Slot(inv, 1, SCREEN_X, SCREEN_Y_RIGHT));
            RingInventory ring = new RingInventory(player);
            for (int i = 0; i < RING_COUNT; i++) {
                addSlot.invoke(menu, new Slot(ring, i, SCREEN_RING_X + (i % 4) * 18, SCREEN_RING_Y + (i / 4) * 18));
            }
        } catch (Exception e) {
            failed = true;
            e.printStackTrace();
        }
    }

    public static boolean ringReady(PlayerEntity player) {
        return player.inventoryMenu.slots.size() >= RING_END;
    }

    public static ItemStack getRing(PlayerEntity player, int index) {
        if (!ringReady(player) || index < 0 || index >= RING_COUNT) return ItemStack.EMPTY;
        return player.inventoryMenu.getSlot(RING_START + index).getItem();
    }

    public static void setRing(PlayerEntity player, int index, ItemStack stack) {
        if (!ringReady(player) || index < 0 || index >= RING_COUNT) return;
        player.inventoryMenu.getSlot(RING_START + index).set(stack);
    }

    public static boolean ready(PlayerEntity player) {
        return player.inventoryMenu.slots.size() > RIGHT;
    }

    public static ItemStack get(PlayerEntity player, int hand) {
        if (!ready(player)) return ItemStack.EMPTY;
        return player.inventoryMenu.getSlot(LEFT + hand).getItem();
    }

    public static void set(PlayerEntity player, int hand, ItemStack stack) {
        if (!ready(player)) return;
        player.inventoryMenu.getSlot(LEFT + hand).set(stack);
    }
}
