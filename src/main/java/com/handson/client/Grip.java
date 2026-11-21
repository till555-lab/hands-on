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

/** Holding on to blocks with the hands. */
public class Grip {
    public static final double REACH = 1.5D;
    private static final double SHOULDER = 1.35D;
    private static final double PULL_TWO = 0.18D;   // faster pull with two hands
    private static final double PULL_ONE = 0.09D;   // pull with one hand
    // player can pull up until shoulders are this far ABOVE the grip point
    private static final double PULL_ABOVE = 1.5D;
    private static final double SPRING = 0.6D;
    private static final double DAMP = 0.75D;

    public static void reset() { }

    public static void releaseAll() {
        HandsState.grip[0] = null;
        HandsState.grip[1] = null;
        HandsState.gripPoint[0] = null;
        HandsState.gripPoint[1] = null;
    }

    public static Vector3d shoulder(ClientPlayerEntity p) {
        return p.position().add(0.0D, SHOULDER, 0.0D);
    }

    public static BlockRayTraceResult findHit(Minecraft mc) {
        ClientPlayerEntity p = mc.player;
        Vector3d eye = p.getEyePosition(1.0F);
        Vector3d look = p.getViewVector(1.0F);
        Vector3d end = eye.add(look.scale(REACH + 0.5D));
        BlockRayTraceResult hit = mc.level.clip(new RayTraceContext(eye, end,
                RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.NONE, p));
        if (hit.getType() != RayTraceResult.Type.BLOCK) return null;
        if (hit.getLocation().distanceTo(shoulder(p)) > REACH) return null;
        return hit;
    }

    public static void tick(Minecraft mc) {
        ClientPlayerEntity p = mc.player;
        boolean ladder = p.onClimbable();
        int n = 0;
        double ax = 0, ay = 0, az = 0;
        for (int h = 0; h < 2; h++) {
            BlockRayTraceResult g = HandsState.grip[h];
            if (g == null) continue;
            BlockPos pos = g.getBlockPos();
            BlockState state = mc.level.getBlockState(pos);
            if (ladder || !p.isAlive() || !HandSlots.get(p, h).isEmpty()
                    || state.getCollisionShape(mc.level, pos).isEmpty()) {
                HandsState.grip[h] = null;
                HandsState.gripPoint[h] = null;
                continue;
            }
            Vector3d l = g.getLocation();
            ax += l.x; ay += l.y; az += l.z;
            n++;
        }
        if (n == 0) return;

        Vector3d anchor = new Vector3d(ax / n, ay / n, az / n);
        Vector3d sh = shoulder(p);
        Vector3d d = anchor.subtract(sh);
        double dist = d.length();
        Vector3d mv = p.getDeltaMovement();

        boolean jumpDown = mc.options.keyJump.isDown();

        // rope constraint first
        if (dist > REACH - 0.02D && dist > 0.001D) {
            Vector3d dir = d.scale(1.0D / dist);
            double along = mv.dot(dir);
            if (along < 0.0D) {
                mv = mv.subtract(dir.scale(along * DAMP));
            }
            if (dist > REACH) {
                double over = dist - REACH;
                mv = mv.add(dir.scale(Math.min(over * SPRING, 0.4D)));
            }
        }

        // pull up: set a fixed upward velocity, no spring bounce
        // stop pulling when shoulders are above anchor + PULL_ABOVE
        if (jumpDown && sh.y < anchor.y + PULL_ABOVE) {
            double up = n >= 2 ? PULL_TWO : PULL_ONE;
            // hard-set vertical velocity to pull speed, don't add on top
            mv = new Vector3d(mv.x, up, mv.z);
        } else if (jumpDown) {
            // reached the top - hold position, cancel vertical movement
            mv = new Vector3d(mv.x, 0, mv.z);
        }

        // on ground: suppress vanilla jump entirely
        if (p.isOnGround() && jumpDown) {
            mv = new Vector3d(mv.x, 0, mv.z);
        }

        p.setDeltaMovement(mv);
        p.fallDistance = 0.0F;
    }
}
