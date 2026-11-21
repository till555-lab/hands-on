package com.handson.client;

import net.minecraft.entity.item.ItemEntity;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;

/** Client-side state of the two hands. Index 0 = left hand, 1 = right hand. */
public class HandsState {
    public static boolean modeOn = false;
    public static boolean locked = false;

    public static final boolean[] down = new boolean[2];
    public static final float[] ext = new float[2];
    public static final float[] prevExt = new float[2];
    public static final ItemEntity[] target = new ItemEntity[2];

    public static final BlockRayTraceResult[] aim = new BlockRayTraceResult[2];
    public static final BlockRayTraceResult[] grip = new BlockRayTraceResult[2];
    /** World-space grip point for rendering the arm line. */
    public static final Vector3d[] gripPoint = new Vector3d[2];
    public static final boolean[] consumed = new boolean[2];

    public static boolean invOpen = false;
    /** true = second ring mode (blocked slots, future: armor/craft) */
    public static boolean invMode2 = false;
    public static float anchorYaw = 0.0F;
    public static final int[] ringHover = {-1, -1};

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
            aim[i] = null;
            grip[i] = null;
            gripPoint[i] = null;
            consumed[i] = false;
            ringHover[i] = -1;
        }
        invOpen = false;
        invMode2 = false;
        smoothInit = false;
        Grip.reset();
    }
}
