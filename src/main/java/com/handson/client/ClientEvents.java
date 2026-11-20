package com.handson.client;

import com.handson.HandSlots;
import com.handson.net.HandsNet;
import com.handson.net.HandsPacket;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.GameSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.inventory.InventoryScreen;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.PlayerRenderer;
import net.minecraft.client.renderer.entity.model.BipedModel;
import net.minecraft.client.renderer.entity.model.PlayerModel;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.settings.PointOfView;
import net.minecraft.client.util.InputMappings;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.client.event.GuiContainerEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

public class ClientEvents {
    private static final float SPEED = 0.34F;
    private static final double REACH = 4.0D;

    // hand pose tuning
    private static final double REACH_FWD = 0.25D;    // how far the hand moves forward when extended
    private static final double STRETCH = 0.25D;      // extra arm length at full extension
    private static final double LAG_SCALE = 0.55D;    // how much the hands lag behind head turns
    private static final double ITEM_UP = 0.02D;      // item offset from the arm tip
    private static final double ITEM_FWD = -0.08D;

    // ring inventory
    private static final double RING_R = 1.25D;
    private static final double PANEL = 0.25D;

    // arm direction toward grip: how far from shoulder to grip point we lerp the arm angle
    // the arm swings to face the grip point; this is the max angle (degrees) it can swing
    private static final double ARM_SWING_MAX = 70.0D;

    private boolean toggleWas = false;
    private boolean lockWas = false;
    private boolean invWas = false;
    private boolean cWas = false;
    private boolean relWas = false;

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
            invWas = false;
            cWas = false;
            relWas = false;
            return;
        }

        // M asks the server to switch controls
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

            // C works like Shift (sneak)
            boolean cNow = InputMappings.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_C);
            if (cNow) {
                o.keyShift.setDown(true);
            } else if (cWas) {
                o.keyShift.setDown(false);
            }
            cWas = cNow;
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

        boolean invNow = mc.screen == null && (HandKeys.INVENTORY.isDown() || HandKeys.INVENTORY_ALT.isDown());
        if (invNow && !invWas) {
            HandsState.invOpen = !HandsState.invOpen;
        }
        invWas = invNow;

        // X lets go with both hands
        boolean relNow = mc.screen == null && HandKeys.RELEASE.isDown();
        if (relNow && !relWas) {
            Grip.releaseAll();
        }
        relWas = relNow;

        boolean ladder = mc.player.onClimbable();
        for (int h = 0; h < 2; h++) {
            KeyBinding key = h == 0 ? HandKeys.LEFT : HandKeys.RIGHT;
            boolean now = mc.screen == null && key.isDown();
            boolean was = HandsState.down[h];
            if (now && !was && HandsState.grip[h] != null) {
                HandsState.grip[h] = null;
                HandsState.gripPoint[h] = null;
                HandsState.consumed[h] = true;
            }
            if (!now) {
                HandsState.consumed[h] = false;
            }
            boolean active = now && !HandsState.consumed[h];
            ItemEntity prevTarget = HandsState.target[h];
            int prevRing = HandsState.ringHover[h];
            BlockRayTraceResult prevAim = HandsState.aim[h];

            HandsState.prevExt[h] = HandsState.ext[h];
            if (active || HandsState.grip[h] != null) {
                HandsState.ext[h] = Math.min(1.0F, HandsState.ext[h] + SPEED);
            } else {
                HandsState.ext[h] = Math.max(0.0F, HandsState.ext[h] - SPEED);
            }
            if (active) {
                int ringSlot = HandsState.invOpen ? findRingSlot(mc) : -1;
                HandsState.ringHover[h] = ringSlot;
                HandsState.target[h] = ringSlot >= 0 ? null : findTarget(mc);
                boolean canGrab = ringSlot < 0 && HandsState.target[h] == null && !ladder
                        && HandsState.grip[h] == null && HandSlots.get(mc.player, h).isEmpty();
                HandsState.aim[h] = canGrab ? Grip.findHit(mc) : null;
            } else {
                HandsState.ringHover[h] = -1;
                HandsState.target[h] = null;
                HandsState.aim[h] = null;
            }
            if (was && !active) {
                onRelease(mc, h, prevTarget, prevRing, prevAim);
            }
            HandsState.down[h] = active;
            // keep gripPoint in sync
            if (HandsState.grip[h] != null) {
                HandsState.gripPoint[h] = HandsState.grip[h].getLocation();
            } else {
                HandsState.gripPoint[h] = null;
            }
        }
        Grip.tick(mc);
    }

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

    private static void skipFrontView(GameSettings o) {
        if (o.getCameraType() == PointOfView.THIRD_PERSON_FRONT) {
            o.setCameraType(PointOfView.FIRST_PERSON);
        }
    }

    private static void drain(KeyBinding key) {
        key.setDown(false);
        while (key.consumeClick()) { }
    }

    @SubscribeEvent
    public void onScroll(InputEvent.MouseScrollEvent event) {
        if (HandsState.modeOn && Minecraft.getInstance().screen == null) {
            event.setCanceled(true);
        }
    }

    private void onRelease(Minecraft mc, int h, ItemEntity target, int ringSlot, BlockRayTraceResult aim) {
        if (ringSlot >= 0) {
            HandsNet.CHANNEL.sendToServer(new HandsPacket(HandsPacket.RING, h, ringSlot, false));
            return;
        }
        ItemStack held = HandSlots.get(mc.player, h);
        if (!held.isEmpty()) {
            if (!HandsState.locked) {
                HandsNet.CHANNEL.sendToServer(new HandsPacket(HandsPacket.DROP, h, 0, false));
            }
        } else if (target != null && target.isAlive()) {
            HandsNet.CHANNEL.sendToServer(new HandsPacket(HandsPacket.GRAB, h, target.getId(), false));
        } else if (aim != null && !mc.player.onClimbable()) {
            HandsState.grip[h] = aim;
            HandsState.gripPoint[h] = aim.getLocation();
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

    /**
     * Draw one hand in first-person.
     * When the hand has an active grip, the arm rotates to point toward the grip point
     * instead of following the camera. The player can look anywhere freely.
     */
    private static void drawHand(Minecraft mc, ClientPlayerEntity player, MatrixStack ms,
                                 IRenderTypeBuffer buf, int light, int h, float pt) {
        boolean right = h == 1;
        double f = right ? 1.0D : -1.0D;
        float ext = MathHelper.lerp(pt, HandsState.prevExt[h], HandsState.ext[h]);

        // base lag from head rotation (same as before)
        double yawRot = 0.0D;
        double pitchRot = 0.0D;
        if (HandsState.smoothInit) {
            float sy = HandsState.prevSmoothYaw
                    + MathHelper.wrapDegrees(HandsState.smoothYaw - HandsState.prevSmoothYaw) * pt;
            float sp = MathHelper.lerp(pt, HandsState.prevSmoothPitch, HandsState.smoothPitch);
            double lagYaw = MathHelper.clamp(MathHelper.wrapDegrees(player.getViewYRot(pt) - sy), -18.0F, 18.0F);
            double lagPitch = MathHelper.clamp(player.getViewXRot(pt) - sp, -18.0F, 18.0F);
            yawRot += lagYaw * LAG_SCALE;
            pitchRot += lagPitch * LAG_SCALE;
        }

        // If this hand has a grip, swing the arm to face the grip point.
        // We compute the direction from the shoulder to the grip in camera space.
        Vector3d gripWorld = HandsState.gripPoint[h];
        double gripYawDelta = 0.0D;
        double gripPitchDelta = 0.0D;
        if (gripWorld != null) {
            // shoulder position in world space
            double shoulderX = player.xo + (player.getX() - player.xo) * pt;
            double shoulderY = player.yo + (player.getY() - player.yo) * pt + 1.35D;
            double shoulderZ = player.zo + (player.getZ() - player.zo) * pt;

            double dx = gripWorld.x - shoulderX;
            double dy = gripWorld.y - shoulderY;
            double dz = gripWorld.z - shoulderZ;
            double horizDist = Math.sqrt(dx * dx + dz * dz);

            // direction angle of the grip in world yaw/pitch
            double gripWorldYaw = Math.toDegrees(Math.atan2(-dx, dz));
            double gripWorldPitch = Math.toDegrees(-Math.atan2(dy, horizDist));

            // delta from current camera direction
            double camYaw = player.getViewYRot(pt);
            double camPitch = player.getViewXRot(pt);

            gripYawDelta = MathHelper.wrapDegrees((float)(gripWorldYaw - camYaw));
            gripPitchDelta = gripWorldPitch - camPitch;

            // clamp so the arm stays physically plausible (doesn't swing behind the player)
            gripYawDelta = MathHelper.clamp(gripYawDelta, -ARM_SWING_MAX, ARM_SWING_MAX);
            gripPitchDelta = MathHelper.clamp(gripPitchDelta, -ARM_SWING_MAX, ARM_SWING_MAX);

            // blend in: use full swing only when fully extended
            gripYawDelta *= ext;
            gripPitchDelta *= ext;
        }

        Pose pose = new Pose();
        ms.pushPose();
        // apply camera lag first, then grip swing on top
        ry(ms, pose, yawRot + gripYawDelta);
        rx(ms, pose, pitchRot + gripPitchDelta);
        tr(ms, pose, 0.0D, 0.0D, -REACH_FWD * ext);

        // resting pose of the arm
        tr(ms, pose, f * 0.64D, -0.6D, -0.72D);
        ry(ms, pose, f * 45.0D);
        tr(ms, pose, f * -1.0D, 3.6D, 3.5D);
        rz(ms, pose, f * 120.0D);
        rx(ms, pose, 200.0D);
        ry(ms, pose, f * -135.0D);
        tr(ms, pose, f * 5.6D, 0.0D, 0.0D);
        sc(ms, pose, 1.0D, 1.0D + STRETCH * ext, 1.0D);

        double[] tip = pose.apply(-f * 0.375D, 0.75D, 0.0D);

        PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
        if (right) {
            renderer.renderRightHand(ms, buf, light, player);
        } else {
            renderer.renderLeftHand(ms, buf, light, player);
        }
        ms.popPose();

        ItemStack stack = HandSlots.get(player, h);
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

    // ------------------------------------------------------ ring inventory

    private static double[] ringSlot(ClientPlayerEntity p, float pt, int i) {
        double px = p.xo + (p.getX() - p.xo) * pt;
        double py = p.yo + (p.getY() - p.yo) * pt;
        double pz = p.zo + (p.getZ() - p.zo) * pt;
        double by = py + 1.22D;
        double body = HandsState.anchorYaw;
        double yaw;
        double dy = 0.0D;
        double r = RING_R;
        double tilt = 0.0D;
        if (i < 8) {
            yaw = body + i * 45.0D;
        } else if (i < 16) {
            int k = (i - 8) % 4;
            yaw = body + k * 90.0D;
            r = RING_R - 0.15D;
            if (i < 12) {
                dy = 0.62D;
                tilt = -35.0D;
            } else {
                dy = -0.62D;
                tilt = 35.0D;
            }
        } else if (i < 20) {
            int k = i - 16;
            yaw = body + (1 + 2 * k) * 45.0D;
            r = RING_R - 0.15D;
            dy = -0.62D;
            tilt = 35.0D;
        } else {
            int k = i - 20;
            yaw = body + (1 + 2 * k) * 45.0D;
            r = RING_R - 0.15D;
            dy = 0.62D;
            tilt = -35.0D;
        }
        double ang = Math.toRadians(yaw);
        return new double[] {px - Math.sin(ang) * r, by + dy, pz + Math.cos(ang) * r, yaw, tilt};
    }

    private static int findRingSlot(Minecraft mc) {
        ClientPlayerEntity p = mc.player;
        if (!HandSlots.ringReady(p)) {
            return -1;
        }
        Vector3d eye = p.getEyePosition(1.0F);
        Vector3d look = p.getViewVector(1.0F);
        int best = -1;
        double bestD = 0.3D;
        for (int i = 0; i < HandSlots.RING_COUNT; i++) {
            double[] s = ringSlot(p, 1.0F, i);
            Vector3d v = new Vector3d(s[0] - eye.x, s[1] - eye.y, s[2] - eye.z);
            double t = v.dot(look);
            if (t <= 0.0D) {
                continue;
            }
            double d = v.subtract(look.scale(t)).length();
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    private static void ringTransform(MatrixStack ms, double[] s, Vector3d cam) {
        ms.translate(s[0] - cam.x, s[1] - cam.y, s[2] - cam.z);
        ms.mulPose(Vector3f.YP.rotationDegrees((float) -s[3]));
        ms.mulPose(Vector3f.XP.rotationDegrees((float) s[4]));
    }

    private static void drawRing(Minecraft mc, MatrixStack ms, IRenderTypeBuffer.Impl buf, Vector3d cam, float pt) {
        ClientPlayerEntity p = mc.player;
        if (p == null || !HandSlots.ringReady(p)) {
            return;
        }
        IVertexBuilder lines = buf.getBuffer(RenderType.lines());
        for (int i = 0; i < HandSlots.RING_COUNT; i++) {
            float r = 0.85F;
            float g = 0.85F;
            float b = 0.85F;
            boolean hoverLeft = HandsState.ringHover[0] == i;
            boolean hoverRight = HandsState.ringHover[1] == i;
            if (hoverLeft && hoverRight) {
                r = 0.7F;
                g = 0.3F;
                b = 1.0F;
            } else if (hoverRight) {
                r = 1.0F;
                g = 0.2F;
                b = 0.2F;
            } else if (hoverLeft) {
                r = 0.25F;
                g = 0.5F;
                b = 1.0F;
            }
            double[] s = ringSlot(p, pt, i);
            ms.pushPose();
            ringTransform(ms, s, cam);
            WorldRenderer.renderLineBox(ms, lines, -PANEL, -PANEL, -0.01D, PANEL, PANEL, 0.01D, r, g, b, 0.9F);
            WorldRenderer.renderLineBox(ms, lines, -PANEL - 0.01D, -PANEL - 0.01D, -0.01D,
                    PANEL + 0.01D, PANEL + 0.01D, 0.01D, r, g, b, 0.9F);
            if (HandSlots.getRing(p, i).isEmpty()) {
                WorldRenderer.renderLineBox(ms, lines, -0.07D, -0.005D, -0.005D, 0.07D, 0.005D, 0.005D, 1.0F, 1.0F, 1.0F, 0.9F);
                WorldRenderer.renderLineBox(ms, lines, -0.005D, -0.07D, -0.005D, 0.005D, 0.07D, 0.005D, 1.0F, 1.0F, 1.0F, 0.9F);
            }
            ms.popPose();
        }
        buf.endBatch(RenderType.lines());

        for (int i = 0; i < HandSlots.RING_COUNT; i++) {
            ItemStack st = HandSlots.getRing(p, i);
            if (st.isEmpty()) {
                continue;
            }
            double[] s = ringSlot(p, pt, i);
            ms.pushPose();
            ringTransform(ms, s, cam);
            ms.mulPose(Vector3f.YP.rotationDegrees(180.0F));
            ms.scale(0.4F, 0.4F, 0.4F);
            mc.getItemRenderer().renderStatic(st, ItemCameraTransforms.TransformType.FIXED,
                    15728880, OverlayTexture.NO_OVERLAY, ms, buf);
            ms.popPose();

            if (st.getCount() > 1) {
                String text = String.valueOf(st.getCount());
                ms.pushPose();
                ms.translate(s[0] - cam.x, s[1] - cam.y - 0.22D, s[2] - cam.z);
                ms.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
                ms.scale(-0.02F, -0.02F, 0.02F);
                float w = mc.font.width(text);
                mc.font.drawInBatch(text, -w / 2.0F, 0.0F, 0xFFFFFFFF, false,
                        ms.last().pose(), buf, true, 0, 15728880);
                ms.popPose();
            }
        }
        buf.endBatch();
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

        if (HandsState.invOpen) {
            drawRing(mc, ms, buf, cam, event.getPartialTicks());
        }
        drawGripMarkers(ms, buf, cam);

        for (int h = 0; h < 2; h++) {
            ItemEntity t = HandsState.target[h];
            if (!HandsState.down[h] || t == null || !t.isAlive()) {
                continue;
            }
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

    // ------------------------------------------------------ grip markers

    private static void drawGripMarkers(MatrixStack ms, IRenderTypeBuffer.Impl buf, Vector3d cam) {
        IVertexBuilder lines = buf.getBuffer(RenderType.lines());
        boolean any = false;
        for (int h = 0; h < 2; h++) {
            BlockRayTraceResult hit = HandsState.grip[h] != null ? HandsState.grip[h]
                    : (HandsState.down[h] ? HandsState.aim[h] : null);
            if (hit == null) {
                continue;
            }
            any = true;
            float r = h == 0 ? 0.25F : 1.0F;
            float g = h == 0 ? 0.5F : 0.2F;
            float b = h == 0 ? 1.0F : 0.2F;
            double half = (HandsState.grip[h] != null ? 0.09D : 0.07D) + 0.01D * h;
            Vector3d c = hit.getLocation();
            Direction d = hit.getDirection();
            double cx = c.x + d.getStepX() * 0.05D;
            double cy = c.y + d.getStepY() * 0.05D;
            double cz = c.z + d.getStepZ() * 0.05D;
            ms.pushPose();
            ms.translate(-cam.x, -cam.y, -cam.z);
            // single outline box (no nested squares)
            WorldRenderer.renderLineBox(ms, lines, cx - half, cy - half, cz - half, cx + half, cy + half, cz + half, r, g, b, 1.0F);
            ms.popPose();
        }
        if (any) {
            buf.endBatch(RenderType.lines());
        }
    }

    /** Third person: hand that holds on is directed toward the grip point using THROW_SPEAR pose.
     *  This is the best we can do without a custom layer renderer in this Forge version. */
    @SubscribeEvent
    public void onRenderLivingPre(RenderLivingEvent.Pre event) {
        if (!HandsState.modeOn || event.getEntity() != Minecraft.getInstance().player) {
            return;
        }
        Object model = event.getRenderer().getModel();
        if (!(model instanceof PlayerModel)) {
            return;
        }
        PlayerModel<?> pm = (PlayerModel<?>) model;
        if (HandsState.grip[0] != null) {
            pm.leftArmPose = BipedModel.ArmPose.THROW_SPEAR;
        }
        if (HandsState.grip[1] != null) {
            pm.rightArmPose = BipedModel.ArmPose.THROW_SPEAR;
        }
    }

    // ------------------------------------------- hand slots in the inventory

    @SubscribeEvent
    public void onInventoryBackground(GuiContainerEvent.DrawBackground event) {
        if (!(event.getGuiContainer() instanceof InventoryScreen)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !HandSlots.ready(mc.player)) {
            return;
        }
        InventoryScreen screen = (InventoryScreen) event.getGuiContainer();
        MatrixStack ms = event.getMatrixStack();
        for (int h = 0; h < 2; h++) {
            int x = screen.getGuiLeft() + HandSlots.SCREEN_X;
            int y = screen.getGuiTop() + (h == 0 ? HandSlots.SCREEN_Y_LEFT : HandSlots.SCREEN_Y_RIGHT);
            int rim = h == 1 ? 0xFFFF4040 : 0xFF4080FF;
            AbstractGui.fill(ms, x - 2, y - 2, x + 18, y + 18, rim);
            AbstractGui.fill(ms, x - 1, y - 1, x + 17, y + 17, 0xFF373737);
            AbstractGui.fill(ms, x, y, x + 17, y + 17, 0xFFFFFFFF);
            AbstractGui.fill(ms, x, y, x + 16, y + 16, 0xFF8B8B8B);

            String label = new TranslationTextComponent(
                    h == 1 ? "label.handson.right_hand" : "label.handson.left_hand").getString();
            ms.pushPose();
            ms.translate(x + 8.0F, y - 12.0F, 0.0F);
            ms.scale(0.75F, 0.75F, 1.0F);
            mc.font.drawShadow(ms, label, -mc.font.width(label) / 2.0F, 0.0F, 0xFFFFFF);
            ms.popPose();
        }
        if (HandSlots.ringReady(mc.player)) {
            for (int i = 0; i < HandSlots.RING_COUNT; i++) {
                int x = screen.getGuiLeft() + HandSlots.SCREEN_RING_X + (i % 4) * 18;
                int y = screen.getGuiTop() + HandSlots.SCREEN_RING_Y + (i / 4) * 18;
                AbstractGui.fill(ms, x - 1, y - 1, x + 17, y + 17, 0xFF373737);
                AbstractGui.fill(ms, x, y, x + 17, y + 17, 0xFFFFFFFF);
                AbstractGui.fill(ms, x, y, x + 16, y + 16, 0xFF8B8B8B);
            }
        }
    }

    // ------------------------------------------ vanilla HUD in Hands On mode

    private static boolean isLowered(RenderGameOverlayEvent.ElementType t) {
        return t == RenderGameOverlayEvent.ElementType.HEALTH
                || t == RenderGameOverlayEvent.ElementType.ARMOR
                || t == RenderGameOverlayEvent.ElementType.FOOD
                || t == RenderGameOverlayEvent.ElementType.AIR
                || t == RenderGameOverlayEvent.ElementType.HEALTHMOUNT
                || t == RenderGameOverlayEvent.ElementType.EXPERIENCE
                || t == RenderGameOverlayEvent.ElementType.JUMPBAR;
    }

    @SubscribeEvent
    public void onOverlayPre(RenderGameOverlayEvent.Pre event) {
        if (!HandsState.modeOn) {
            return;
        }
        RenderGameOverlayEvent.ElementType t = event.getType();
        if (t == RenderGameOverlayEvent.ElementType.HOTBAR) {
            event.setCanceled(true);
        } else if (isLowered(t) && !event.isCanceled()) {
            event.getMatrixStack().pushPose();
            event.getMatrixStack().translate(0.0D, 22.0D, 0.0D);
        }
    }

    @SubscribeEvent
    public void onOverlayPostLowered(RenderGameOverlayEvent.Post event) {
        if (HandsState.modeOn && isLowered(event.getType())) {
            event.getMatrixStack().popPose();
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
        int w = mc.getWindow().getGuiScaledWidth();
        int hgt = mc.getWindow().getGuiScaledHeight();

        // --- top left: grip icons for active grips ---
        for (int h = 0; h < 2; h++) {
            if (HandsState.grip[h] == null) {
                continue;
            }
            int color = h == 0 ? 0xFF4080FF : 0xFFFF4040;
            int x = 6 + h * 20;
            int y = 6;
            AbstractGui.fill(ms, x - 2, y - 2, x + 16, y + 18, 0x99000000);
            AbstractGui.fill(ms, x + 2, y + 7, x + 14, y + 16, color);
            AbstractGui.fill(ms, x + 2, y + 1, x + 4, y + 7, color);
            AbstractGui.fill(ms, x + 5, y, x + 7, y + 7, color);
            AbstractGui.fill(ms, x + 8, y, x + 10, y + 7, color);
            AbstractGui.fill(ms, x + 11, y + 1, x + 13, y + 7, color);
        }

        // items held in hands (bottom, same as before)
        drawHeld(mc, ms, HandSlots.get(mc.player, 0), w / 2 - 118, hgt - 19, false);
        drawHeld(mc, ms, HandSlots.get(mc.player, 1), w / 2 + 102, hgt - 19, true);

        drawHints(mc, ms, hgt);
    }

    private static void drawHotbarCell(Minecraft mc, MatrixStack ms, int x, int y) {
        mc.getTextureManager().bind(Widget.WIDGETS_LOCATION);
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        AbstractGui.blit(ms, x - 3, y - 3, 0, 0.0F, 0.0F, 21, 22, 256, 256);
        AbstractGui.blit(ms, x + 18, y - 3, 0, 181.0F, 0.0F, 1, 22, 256, 256);
    }

    private static String tr(String key, Object... args) {
        return new TranslationTextComponent(key, args).getString();
    }

    /** Hints in the bottom left corner. L state shown here instead of a separate lock icon. */
    private static void drawHints(Minecraft mc, MatrixStack ms, int hgt) {
        boolean more = Screen.hasShiftDown() || mc.options.keyShift.isDown();
        List<String> lines = new ArrayList<>();

        boolean ground = false;
        boolean slot = false;
        for (int h = 0; h < 2; h++) {
            if (HandsState.target[h] != null) ground = true;
            if (HandsState.ringHover[h] >= 0) slot = true;
        }
        if (slot) {
            lines.add(tr("hint.handson.slot"));
        } else if (ground) {
            lines.add(tr("hint.handson.take"));
        }
        boolean aiming = false;
        boolean gripping = false;
        for (int h = 0; h < 2; h++) {
            if (HandsState.aim[h] != null) aiming = true;
            if (HandsState.grip[h] != null) gripping = true;
        }
        if (aiming) {
            lines.add(tr("hint.handson.grab") + (more ? " " + tr("hint.handson.grab.more") : ""));
        }
        if (gripping) {
            lines.add(tr("hint.handson.release", HandKeys.RELEASE.getTranslatedKeyMessage())
                    + (more ? " " + tr("hint.handson.release.more") : ""));
        }

        lines.add(tr("hint.handson.hands", HandKeys.LEFT.getTranslatedKeyMessage(),
                HandKeys.RIGHT.getTranslatedKeyMessage()) + (more ? " " + tr("hint.handson.hands.more") : ""));

        // L state shown inline in hints, no separate lock icon
        String lockLine = tr("hint.handson.lock", HandKeys.LOCK.getTranslatedKeyMessage());
        if (HandsState.locked) {
            lockLine += " " + tr("hint.handson.lock.active");
        }
        if (more) {
            lockLine += " " + tr("hint.handson.lock.more");
        }
        lines.add(lockLine);

        lines.add(tr("hint.handson.inventory", HandKeys.INVENTORY.getTranslatedKeyMessage(),
                HandKeys.INVENTORY_ALT.getTranslatedKeyMessage())
                + (more ? " " + tr("hint.handson.inventory.more") : ""));

        float scale = 0.7F;
        ms.pushPose();
        ms.scale(scale, scale, 1.0F);
        float y = (hgt - 5) / scale - 9.0F * lines.size();
        for (String line : lines) {
            mc.font.drawShadow(ms, line, 6.0F / scale, y, 0xBBBBBB);
            y += 9.0F;
        }
        ms.popPose();
    }

    private static void drawHeld(Minecraft mc, MatrixStack ms, ItemStack stack, int x, int y, boolean right) {
        drawHotbarCell(mc, ms, x, y);
        if (stack.isEmpty()) {
            return;
        }
        mc.getItemRenderer().renderAndDecorateItem(stack, x, y);
        mc.getItemRenderer().renderGuiItemDecorations(mc.font, stack, x, y);

        List<IReorderingProcessor> lines = mc.font.split(stack.getHoverName(), 90);
        int n = Math.min(lines.size(), 5);
        for (int i = 0; i < n; i++) {
            IReorderingProcessor line = lines.get(i);
            float lw = mc.font.width(line);
            mc.font.drawShadow(ms, line, x + 8.0F - lw / 2.0F, y - 5.0F - (n - i) * 10.0F, 0xFFFFFF);
        }
    }
}
