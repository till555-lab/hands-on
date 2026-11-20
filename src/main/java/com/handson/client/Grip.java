package com.handson.client;

import com.handson.HandSlots;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;

/** Holding on to blocks with the hands: finding the point, holding the player and pulling up. */
public class Grip {
    /** How far a hand reaches, measured from the shoulders (blocks). Also the length of the arm when hanging. */
    public static final double REACH = 1.5D;
    private static final double SHOULDER = 1.35D;   // height of the shoulders above the feet
    private static final double PULL_TWO = 0.13D;   // climbing speed per tick with two hands
    private static final double PULL_ONE = 0.045D;  // climbing speed per tick with one hand
    private static final float FLICK = 3.0F;        // degrees per tick of a quick camera move down
    private static final int FLICK_TICKS = 5;       // how long such a move pulls

    private static int pullTicks = 0;
    private static float prevPitch = 0.0F;
    private static boolean pitchInit = false;

    public static void reset() {
        pullTicks = 0;
        pitchInit = false;
    }

    public static void releaseAll() {
        HandsState.grip[0] = null;
        HandsState.grip[1] = null;
    }

    private static Vector3d shoulder(ClientPlayerEntity p) {
        return p.position().add(0.0D, SHOULDER, 0.0D);
    }

    /** The block point under the crosshair that a hand can reach, or null. */
    public static BlockRayTraceResult findHit(Minecraft mc) {
        ClientPlayerEntity p = mc.player;
        Vector3d eye = p.getEyePosition(1.0F);
        Vector3d look = p.getViewVector(1.0F);
        Vector3d end = eye.add(look.scale(REACH + 0.5D));
        BlockRayTraceResult hit = mc.level.clip(new RayTraceContext(eye, end,
                RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.NONE, p));
        if (hit.getType() != RayTraceResult.Type.BLOCK) {
            return null;
        }
        if (hit.getLocation().distanceTo(shoulder(p)) > REACH) {
            return null;
        }
        return hit;
    }

    /** Called every tick in Hands On mode. */
    public static void tick(Minecraft mc) {
        ClientPlayerEntity p = mc.player;
        float pitch = p.xRot;
        boolean flick = pitchInit && pitch - prevPitch > FLICK;
        prevPitch = pitch;
        pitchInit = true;

        boolean ladder = p.onClimbable();
        int n = 0;
        double ax = 0.0D;
        double ay = 0.0D;
        double az = 0.0D;
        for (int h = 0; h < 2; h++) {
            BlockRayTraceResult g = HandsState.grip[h];
            if (g == null) {
                continue;
            }
            BlockPos pos = g.getBlockPos();
            BlockState state = mc.level.getBlockState(pos);
            if (ladder || !p.isAlive() || !HandSlots.get(p, h).isEmpty()
                    || state.getCollisionShape(mc.level, pos).isEmpty()) {
                HandsState.grip[h] = null;
                continue;
            }
            Vector3d l = g.getLocation();
            ax += l.x;
            ay += l.y;
            az += l.z;
            n++;
        }
        if (n == 0) {
            pullTicks = 0;
            return;
        }
        Vector3d anchor = new Vector3d(ax / n, ay / n, az / n);
        Vector3d sh = shoulder(p);
        Vector3d d = anchor.subtract(sh);
        double dist = d.length();
        Vector3d mv = p.getDeltaMovement();

        // pulling up: jump in the air, or a quick camera move down
        if (flick) {
            pullTicks = FLICK_TICKS;
        }
        boolean pulling = (mc.options.keyJump.isDown() && !p.isOnGround()) || pullTicks > 0;
        if (pullTicks > 0) {
            pullTicks--;
        }
        if (pulling && sh.y < anchor.y - 0.15D) {
            double up = n >= 2 ? PULL_TWO : PULL_ONE;
            mv = new Vector3d(mv.x, Math.max(mv.y, up), mv.z);
        }

        // the arms are a rope of a fixed length: no moving further away than the reach
        if (dist > REACH - 0.02D && dist > 0.001D) {
            Vector3d dir = d.scale(1.0D / dist);
            double along = mv.dot(dir);
            if (along < 0.0D) {
                mv = mv.subtract(dir.scale(along));
            }
            if (dist > REACH) {
                mv = mv.add(dir.scale(Math.min((dist - REACH) * 0.6D, 0.4D)));
            }
        }
        p.setDeltaMovement(mv);
        p.fallDistance = 0.0F;
    }
}
