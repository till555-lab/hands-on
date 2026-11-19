package com.handson.client;

import net.minecraft.entity.item.ItemEntity;

/** Client-side state of the two hands. Index 0 = left hand, 1 = right hand. */
public class HandsState {
    public static boolean modeOn = false;
    public static boolean locked = false;

    public static final boolean[] down = new boolean[2];
    public static final float[] ext = new float[2];
    public static final float[] prevExt = new float[2];
    public static final ItemEntity[] target = new ItemEntity[2];

    // ring inventory around the player
    public static boolean invOpen = false;
    public static float anchorYaw = 0.0F;
    public static final int[] ringHover = {-1, -1};

    // smoothed head rotation, used for the lag of the hands
    public static boolean smoothInit = false;
    public static float smoothYaw = 0.0F;
    public static float smoothPitch = 0.0F;
    public static float prevSmoothYaw = 0.0F;
    public static float prevSmoothPitch = 0.0F;

    public static void reset() {
        modeOn = false;
        locked = false;
        clearHands();
    }

    public static void clearHands() {
        for (int i = 0; i < 2; i++) {
            down[i] = false;
            ext[i] = 0.0F;
            prevExt[i] = 0.0F;
            target[i] = null;
            ringHover[i] = -1;
        }
        invOpen = false;
        smoothInit = false;
    }
}
