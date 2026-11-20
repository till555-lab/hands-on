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
    /** How far a hand reaches, measured from the shoulders (blocks). Also the rope length when hanging. */
    public static final double REACH = 1.5D;
    private static final double SHOULDER = 1.35D;   // height of the shoulders above the feet
    private static final double PULL_TWO = 0.13D;   // climbing speed per tick with two hands
    private static final double PULL_ONE = 0.045D;  // climbing speed per tick with one hand
    // how far above the anchor the player can pull up to (shoulders relative to anchor point)
    private static final double PULL_ABOVE = 0.5D;
    // spring strength pulling player toward the rope length when further away
    private static final double SPRING = 0.5D;
    // damping applied to velocity component along the rope when swinging
    private static final double DAMP = 0.7D;

    public static void reset() {
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
            return;
        }
        // average anchor point (or single point if only one hand)
        Vector3d anchor = new Vector3d(ax / n, ay / n, az / n);
        Vector3d sh = shoulder(p);
        Vector3d d = anchor.subtract(sh);
        double dist = d.length();
        Vector3d mv = p.getDeltaMovement();

        // --- pull up with jump key (works both on ground and in air) ---
        boolean jumpDown = mc.options.keyJump.isDown();
        if (jumpDown && sh.y < anchor.y + PULL_ABOVE) {
            double up = n >= 2 ? PULL_TWO : PULL_ONE;
            // prevent vanilla jump from firing: cancel upward bump on ground
            mv = new Vector3d(mv.x, Math.max(mv.y, up), mv.z);
        }

        // --- rope constraint: keep player within reach distance ---
        if (dist > REACH - 0.02D && dist > 0.001D) {
            Vector3d dir = d.scale(1.0D / dist);
            // remove velocity component moving away from anchor
            double along = mv.dot(dir);
            if (along < 0.0D) {
                // damp swing instead of hard-cancelling
                mv = mv.subtract(dir.scale(along * DAMP));
            }
            // spring back toward rope length if past it
            if (dist > REACH) {
                double over = dist - REACH;
                mv = mv.add(dir.scale(Math.min(over * SPRING, 0.3D)));
            }
        }

        p.setDeltaMovement(mv);
        p.fallDistance = 0.0F;

        // cancel vanilla jump impulse when gripping so player doesn't bounce
        if (jumpDown && p.isOnGround()) {
            // absorb the upward impulse vanilla would add this tick
            Vector3d cur = p.getDeltaMovement();
            if (cur.y > 0.42D) {
                p.setDeltaMovement(cur.x, Math.min(cur.y, n >= 2 ? PULL_TWO : PULL_ONE), cur.z);
            }
        }
    }
}
