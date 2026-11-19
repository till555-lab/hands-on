package com.handson.client;

import com.handson.net.HandsNet;
import com.handson.net.HandsPacket;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import java.util.Optional;
import net.minecraft.client.GameSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.PlayerRenderer;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.settings.PointOfView;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ClientEvents {
    private static final float SPEED = 0.34F;
    private static final double REACH = 4.0D;

    // hand pose tuning
    private static final double REACH_FWD = 0.25D;    // how far the hand moves forward when extended
    private static final double STRETCH = 0.25D;      // extra arm length at full extension (0.25 = +25%)
    private static final double LAG_SCALE = 0.8D;     // how much the hands lag behind head turns
    private static final double ITEM_UP = 0.02D;      // item offset from the arm tip
    private static final double ITEM_FWD = -0.08D;

    private boolean toggleWas = false;
    private boolean lockWas = false;

    // ---------------------------------------------------------------- tick

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            HandsState.reset();
            toggleWas = false;
            lockWas = false;
            return;
        }

        // M asks the server to switch controls (the server checks for cheats)
        boolean toggleNow = mc.screen == null && HandKeys.TOGGLE.isDown();
        if (toggleNow && !toggleWas) {
            HandsNet.CHANNEL.sendToServer(new HandsPacket(HandsPacket.REQUEST_TOGGLE, 0, 0, false));
        }
        toggleWas = toggleNow;

        if (!HandsState.modeOn) {
            return;
        }

        // Vanilla controls are switched off while Hands On mode is active.
        if (mc.screen == null) {
            GameSettings o = mc.options;
            drain(o.keyAttack);
            drain(o.keyUse);
            drain(o.keyInventory);
            drain(o.keyDrop);
            drain(o.keySwapOffhand);
            drain(o.keyAdvancements);
            for (KeyBinding slotKey : o.keyHotbarSlots) {
                drain(slotKey);
            }
            skipFrontView(o);
        }

        // smoothed head rotation for the hand lag
        if (!HandsState.smoothInit) {
            HandsState.smoothYaw = mc.player.yRot;
            HandsState.smoothPitch = mc.player.xRot;
            HandsState.prevSmoothYaw = HandsState.smoothYaw;
            HandsState.prevSmoothPitch = HandsState.smoothPitch;
            HandsState.smoothInit = true;
        } else {
            HandsState.prevSmoothYaw = HandsState.smoothYaw;
            HandsState.prevSmoothPitch = HandsState.smoothPitch;
            HandsState.smoothYaw += MathHelper.wrapDegrees(mc.player.yRot - HandsState.smoothYaw) * 0.4F;
            HandsState.smoothPitch += (mc.player.xRot - HandsState.smoothPitch) * 0.4F;
        }

        boolean lockNow = mc.screen == null && HandKeys.LOCK.isDown();
        if (lockNow && !lockWas) {
            HandsState.locked = !HandsState.locked;
        }
        lockWas = lockNow;

        for (int h = 0; h < 2; h++) {
            KeyBinding key = h == 0 ? HandKeys.LEFT : HandKeys.RIGHT;
            boolean now = mc.screen == null && key.isDown();
            boolean was = HandsState.down[h];
            ItemEntity prevTarget = HandsState.target[h];

            HandsState.prevExt[h] = HandsState.ext[h];
            if (now) {
                HandsState.ext[h] = Math.min(1.0F, HandsState.ext[h] + SPEED);
                HandsState.target[h] = findTarget(mc);
            } else {
                HandsState.ext[h] = Math.max(0.0F, HandsState.ext[h] - SPEED);
                HandsState.target[h] = null;
            }
            if (was && !now) {
                onRelease(mc, h, prevTarget);
            }
            HandsState.down[h] = now;
        }
    }

    /** Checked every rendered frame, so the front view never shows up, not even for one frame. */
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !HandsState.modeOn) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        skipFrontView(mc.options);
    }

    /** F5 cycles only first person <-> third person from behind. */
    private static void skipFrontView(GameSettings o) {
        if (o.getCameraType() == PointOfView.THIRD_PERSON_FRONT) {
            o.setCameraType(PointOfView.FIRST_PERSON);
        }
    }

    private static void drain(KeyBinding key) {
        key.setDown(false);
        while (key.consumeClick()) {
            // discard clicks
        }
    }

    @SubscribeEvent
    public void onScroll(InputEvent.MouseScrollEvent event) {
        if (HandsState.modeOn && Minecraft.getInstance().screen == null) {
            event.setCanceled(true);
        }
    }

    private void onRelease(Minecraft mc, int h, ItemEntity target) {
        Hand hand = h == 0 ? Hand.OFF_HAND : Hand.MAIN_HAND;
        ItemStack held = mc.player.getItemInHand(hand);
        if (!held.isEmpty()) {
            if (!HandsState.locked) {
                HandsNet.CHANNEL.sendToServer(new HandsPacket(HandsPacket.DROP, h, 0, false));
            }
        } else if (target != null && target.isAlive()) {
            HandsNet.CHANNEL.sendToServer(new HandsPacket(HandsPacket.GRAB, h, target.getId(), false));
        }
    }

    private static ItemEntity findTarget(Minecraft mc) {
        ClientPlayerEntity p = mc.player;
        Vector3d start = p.getEyePosition(1.0F);
        Vector3d look = p.getViewVector(1.0F);
        Vector3d end = start.add(look.x * REACH, look.y * REACH, look.z * REACH);
        AxisAlignedBB area = p.getBoundingBox().expandTowards(look.x * REACH, look.y * REACH, look.z * REACH).inflate(1.0D);
        ItemEntity best = null;
        double bestDist = REACH * REACH;
        for (ItemEntity ie : mc.level.getEntitiesOfClass(ItemEntity.class, area)) {
            AxisAlignedBB bb = ie.getBoundingBox().inflate(0.25D);
            double d;
            if (bb.contains(start)) {
                d = 0.0D;
            } else {
                Optional<Vector3d> hit = bb.clip(start, end);
                if (!hit.isPresent()) {
                    continue;
                }
                d = start.distanceToSqr(hit.get());
            }
            if (d < bestDist) {
                bestDist = d;
                best = ie;
            }
        }
        return best;
    }

    // ------------------------------------------------------------ hands

    @SubscribeEvent
    public void onRenderHand(RenderHandEvent event) {
        if (!HandsState.modeOn) {
            return;
        }
        event.setCanceled(true);
        if (event.getHand() != Hand.MAIN_HAND) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null) {
            return;
        }
        float pt = event.getPartialTicks();
        for (int h = 0; h < 2; h++) {
            drawHand(mc, player, event.getMatrixStack(), event.getBuffers(), event.getLight(), h, pt);
        }
    }

    private static void tr(MatrixStack ms, Pose p, double x, double y, double z) {
        ms.translate(x, y, z);
        p.translate(x, y, z);
    }

    private static void rx(MatrixStack ms, Pose p, double deg) {
        ms.mulPose(net.minecraft.util.math.vector.Vector3f.XP.rotationDegrees((float) deg));
        p.rotX(deg);
    }

    private static void ry(MatrixStack ms, Pose p, double deg) {
        ms.mulPose(net.minecraft.util.math.vector.Vector3f.YP.rotationDegrees((float) deg));
        p.rotY(deg);
    }

    private static void rz(MatrixStack ms, Pose p, double deg) {
        ms.mulPose(net.minecraft.util.math.vector.Vector3f.ZP.rotationDegrees((float) deg));
        p.rotZ(deg);
    }

    private static void sc(MatrixStack ms, Pose p, double x, double y, double z) {
        ms.scale((float) x, (float) y, (float) z);
        p.scale(x, y, z);
    }

    private static void drawHand(Minecraft mc, ClientPlayerEntity player, MatrixStack ms,
                                 IRenderTypeBuffer buf, int light, int h, float pt) {
        boolean right = h == 1;
        double f = right ? 1.0D : -1.0D;
        float ext = MathHelper.lerp(pt, HandsState.prevExt[h], HandsState.ext[h]);

        // the hands only move forward when extended; the only rotation is the lag behind head turns
        double yawRot = 0.0D;
        double pitchRot = 0.0D;
        if (HandsState.smoothInit) {
            float sy = HandsState.prevSmoothYaw
                    + MathHelper.wrapDegrees(HandsState.smoothYaw - HandsState.prevSmoothYaw) * pt;
            float sp = MathHelper.lerp(pt, HandsState.prevSmoothPitch, HandsState.smoothPitch);
            double lagYaw = MathHelper.clamp(MathHelper.wrapDegrees(player.getViewYRot(pt) - sy), -25.0F, 25.0F);
            double lagPitch = MathHelper.clamp(player.getViewXRot(pt) - sp, -25.0F, 25.0F);
            yawRot += lagYaw * LAG_SCALE;
            pitchRot += lagPitch * LAG_SCALE;
        }

        Pose pose = new Pose();
        ms.pushPose();
        ry(ms, pose, yawRot);
        rx(ms, pose, pitchRot);
        tr(ms, pose, 0.0D, 0.0D, -REACH_FWD * ext);

        // resting pose of the arm (the same pose as in 0.1.0)
        tr(ms, pose, f * 0.64D, -0.6D, -0.72D);
        ry(ms, pose, f * 45.0D);
        tr(ms, pose, f * -1.0D, 3.6D, 3.5D);
        rz(ms, pose, f * 120.0D);
        rx(ms, pose, 200.0D);
        ry(ms, pose, f * -135.0D);
        tr(ms, pose, f * 5.6D, 0.0D, 0.0D);
        sc(ms, pose, 1.0D, 1.0D + STRETCH * ext, 1.0D);

        // tip of the arm in the space the hand started in (centre of the end of the arm)
        double[] tip = pose.apply(-f * 0.375D, 0.75D, 0.0D);

        PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
        if (right) {
            renderer.renderRightHand(ms, buf, light, player);
        } else {
            renderer.renderLeftHand(ms, buf, light, player);
        }
        ms.popPose();

        // item sits at the tip of the arm and moves with it
        ItemStack stack = player.getItemInHand(right ? Hand.MAIN_HAND : Hand.OFF_HAND);
        if (!stack.isEmpty()) {
            ms.pushPose();
            ms.translate(tip[0], tip[1] + ITEM_UP, tip[2] + ITEM_FWD);
            mc.getItemInHandRenderer().renderItem(player, stack,
                    right ? ItemCameraTransforms.TransformType.FIRST_PERSON_RIGHT_HAND
                          : ItemCameraTransforms.TransformType.FIRST_PERSON_LEFT_HAND,
                    !right, ms, buf, light);
            ms.popPose();
        }
    }

    // -------------------------------------------------------- highlight

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!HandsState.modeOn) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        MatrixStack ms = event.getMatrixStack();
        Vector3d cam = mc.gameRenderer.getMainCamera().getPosition();
        IRenderTypeBuffer.Impl buf = mc.renderBuffers().bufferSource();

        for (int h = 0; h < 2; h++) {
            ItemEntity t = HandsState.target[h];
            if (!HandsState.down[h] || t == null || !t.isAlive()) {
                continue;
            }
            // left hand = blue, right hand = red
            float r = h == 0 ? 0.25F : 1.0F;
            float g = h == 0 ? 0.5F : 0.2F;
            float b = h == 0 ? 1.0F : 0.2F;

            AxisAlignedBB box = t.getBoundingBox().inflate(0.08D + 0.03D * h);
            IVertexBuilder lines = buf.getBuffer(RenderType.lines());
            ms.pushPose();
            ms.translate(-cam.x, -cam.y, -cam.z);
            WorldRenderer.renderLineBox(ms, lines, box, r, g, b, 1.0F);
            ms.popPose();
            buf.endBatch(RenderType.lines());

            int count = t.getItem().getCount();
            if (count > 1) {
                String text = String.valueOf(count);
                ms.pushPose();
                ms.translate(t.getX() - cam.x, t.getY() + t.getBbHeight() + 0.4D - cam.y, t.getZ() - cam.z);
                ms.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
                ms.scale(-0.03F, -0.03F, 0.03F);
                float w = mc.font.width(text);
                int color = (255 << 24) | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
                mc.font.drawInBatch(text, -w / 2.0F + (h == 0 ? -8.0F : 8.0F), 0.0F, color, false,
                        ms.last().pose(), buf, true, 0, 15728880);
                ms.popPose();
                buf.endBatch();
            }
        }
    }

    // -------------------------------------------------------------- HUD

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL || !HandsState.modeOn) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        MatrixStack ms = event.getMatrixStack();

        // grip lock icon, top left
        int x = 6;
        int y = 6;
        boolean locked = HandsState.locked;
        int shackle = 0xFFCCCCCC;
        int body = locked ? 0xFFE0B030 : 0xFF70D070;
        if (locked) {
            AbstractGui.fill(ms, x + 2, y, x + 10, y + 2, shackle);
            AbstractGui.fill(ms, x + 2, y, x + 4, y + 7, shackle);
            AbstractGui.fill(ms, x + 8, y, x + 10, y + 7, shackle);
        } else {
            AbstractGui.fill(ms, x + 2, y - 2, x + 10, y, shackle);
            AbstractGui.fill(ms, x + 2, y - 2, x + 4, y + 7, shackle);
            AbstractGui.fill(ms, x + 8, y - 2, x + 10, y + 1, shackle);
        }
        AbstractGui.fill(ms, x, y + 6, x + 12, y + 14, body);
        String lockText = new TranslationTextComponent(
                locked ? "hud.handson.locked" : "hud.handson.unlocked").getString();
        mc.font.drawShadow(ms, lockText, x + 16, y + 3, 0xFFFFFF);

        // what the hands hold: item icon with count, the name below it (white)
        int w = mc.getWindow().getGuiScaledWidth();
        int hgt = mc.getWindow().getGuiScaledHeight();
        drawHeld(mc, ms, mc.player.getItemInHand(Hand.OFF_HAND), w / 2 - 118, hgt - 66);
        drawHeld(mc, ms, mc.player.getItemInHand(Hand.MAIN_HAND), w / 2 + 102, hgt - 66);
    }

    private static void drawHeld(Minecraft mc, MatrixStack ms, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) {
            return;
        }
        mc.getItemRenderer().renderAndDecorateItem(stack, x, y);
        mc.getItemRenderer().renderGuiItemDecorations(mc.font, stack, x, y);
        String name = stack.getHoverName().getString();
        float tw = mc.font.width(name);
        mc.font.drawShadow(ms, name, x + 8.0F - tw / 2.0F, y + 20.0F, 0xFFFFFF);
    }
}
