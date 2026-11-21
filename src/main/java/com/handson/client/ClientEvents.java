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
import org.lwjgl.opengl.GL11;

public class ClientEvents {
    private static final float SPEED = 0.34F;
    private static final double REACH = 4.0D;

    private static final double REACH_FWD = 0.25D;
    private static final double STRETCH = 0.25D;
    private static final double LAG_SCALE = 0.55D;
    private static final double ITEM_UP = 0.02D;
    private static final double ITEM_FWD = -0.08D;

    private static final double RING_R = 1.25D;
    private static final double PANEL = 0.25D;

    // beige colour for the grip arm line
    private static final float ARM_R = 0.90F;
    private static final float ARM_G = 0.80F;
    private static final float ARM_B = 0.65F;
    // half-thickness of the arm quad (blocks)
    private static final float ARM_HALF = 0.045F;

    private boolean toggleWas = false;
    private boolean lockWas = false;
    private boolean invWas = false;
    private boolean cWas = false;
    private boolean relWas = false;

    // ---------------------------------------------------------------- tick

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            HandsState.reset();
            toggleWas = false; lockWas = false; invWas = false; cWas = false; relWas = false;
            return;
        }

        boolean toggleNow = mc.screen == null && HandKeys.TOGGLE.isDown();
        if (toggleNow && !toggleWas) {
            HandsNet.CHANNEL.sendToServer(new HandsPacket(HandsPacket.REQUEST_TOGGLE, 0, 0, false));
        }
        toggleWas = toggleNow;

        if (!HandsState.modeOn) return;

        if (mc.screen == null) {
            GameSettings o = mc.options;
            drain(o.keyAttack); drain(o.keyUse); drain(o.keyInventory);
            drain(o.keyDrop); drain(o.keySwapOffhand); drain(o.keyAdvancements);
            for (KeyBinding slotKey : o.keyHotbarSlots) drain(slotKey);
            skipFrontView(o);

            boolean cNow = InputMappings.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_C);
            if (cNow) o.keyShift.setDown(true);
            else if (cWas) o.keyShift.setDown(false);
            cWas = cNow;
        }

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
        if (lockNow && !lockWas) HandsState.locked = !HandsState.locked;
        lockWas = lockNow;

        boolean invNow = mc.screen == null && (HandKeys.INVENTORY.isDown() || HandKeys.INVENTORY_ALT.isDown());
        if (invNow && !invWas) HandsState.invOpen = !HandsState.invOpen;
        invWas = invNow;

        boolean relNow = mc.screen == null && HandKeys.RELEASE.isDown();
        if (relNow && !relWas) Grip.releaseAll();
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
            if (!now) HandsState.consumed[h] = false;
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
            if (was && !active) onRelease(mc, h, prevTarget, prevRing, prevAim);
            HandsState.down[h] = active;
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
        if (event.phase != TickEvent.Phase.START || !HandsState.modeOn) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        skipFrontView(mc.options);
    }

    private static void skipFrontView(GameSettings o) {
        if (o.getCameraType() == PointOfView.THIRD_PERSON_FRONT) o.setCameraType(PointOfView.FIRST_PERSON);
    }

    private static void drain(KeyBinding key) {
        key.setDown(false);
        while (key.consumeClick()) { }
    }

    @SubscribeEvent
    public void onScroll(InputEvent.MouseScrollEvent event) {
        if (HandsState.modeOn && Minecraft.getInstance().screen == null) event.setCanceled(true);
    }

    private void onRelease(Minecraft mc, int h, ItemEntity target, int ringSlot, BlockRayTraceResult aim) {
        if (ringSlot >= 0) {
            HandsNet.CHANNEL.sendToServer(new HandsPacket(HandsPacket.RING, h, ringSlot, false));
            return;
        }
        ItemStack held = HandSlots.get(mc.player, h);
        if (!held.isEmpty()) {
            if (!HandsState.locked) HandsNet.CHANNEL.sendToServer(new HandsPacket(HandsPacket.DROP, h, 0, false));
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
            if (bb.contains(start)) { d = 0.0D; }
            else {
                Optional<Vector3d> hit = bb.clip(start, end);
                if (!hit.isPresent()) continue;
                d = start.distanceToSqr(hit.get());
            }
            if (d < bestDist) { bestDist = d; best = ie; }
        }
        return best;
    }

    // ------------------------------------------------------------ first-person hands (vanilla arm render)

    @SubscribeEvent
    public void onRenderHand(RenderHandEvent event) {
        if (!HandsState.modeOn) return;
        event.setCanceled(true);
        if (event.getHand() != Hand.MAIN_HAND) return;
        Minecraft mc = Minecraft.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null) return;
        float pt = event.getPartialTicks();
        // only draw vanilla arms when NOT gripping (gripping arms are drawn as world lines)
        for (int h = 0; h < 2; h++) {
            if (HandsState.grip[h] == null) {
                drawHand(mc, player, event.getMatrixStack(), event.getBuffers(), event.getLight(), h, pt);
            }
        }
    }

    private static void tr(MatrixStack ms, Pose p, double x, double y, double z) { ms.translate(x, y, z); p.translate(x, y, z); }
    private static void rx(MatrixStack ms, Pose p, double deg) { ms.mulPose(Vector3f.XP.rotationDegrees((float)deg)); p.rotX(deg); }
    private static void ry(MatrixStack ms, Pose p, double deg) { ms.mulPose(Vector3f.YP.rotationDegrees((float)deg)); p.rotY(deg); }
    private static void rz(MatrixStack ms, Pose p, double deg) { ms.mulPose(Vector3f.ZP.rotationDegrees((float)deg)); p.rotZ(deg); }
    private static void sc(MatrixStack ms, Pose p, double x, double y, double z) { ms.scale((float)x,(float)y,(float)z); p.scale(x,y,z); }

    private static void drawHand(Minecraft mc, ClientPlayerEntity player, MatrixStack ms,
                                 IRenderTypeBuffer buf, int light, int h, float pt) {
        boolean right = h == 1;
        double f = right ? 1.0D : -1.0D;
        float ext = MathHelper.lerp(pt, HandsState.prevExt[h], HandsState.ext[h]);

        double yawRot = 0, pitchRot = 0;
        if (HandsState.smoothInit) {
            float sy = HandsState.prevSmoothYaw + MathHelper.wrapDegrees(HandsState.smoothYaw - HandsState.prevSmoothYaw) * pt;
            float sp = MathHelper.lerp(pt, HandsState.prevSmoothPitch, HandsState.smoothPitch);
            yawRot = MathHelper.clamp(MathHelper.wrapDegrees(player.getViewYRot(pt) - sy), -18F, 18F) * LAG_SCALE;
            pitchRot = MathHelper.clamp(player.getViewXRot(pt) - sp, -18F, 18F) * LAG_SCALE;
        }

        Pose pose = new Pose();
        ms.pushPose();
        ry(ms, pose, yawRot); rx(ms, pose, pitchRot);
        tr(ms, pose, 0, 0, -REACH_FWD * ext);
        tr(ms, pose, f * 0.64D, -0.6D, -0.72D);
        ry(ms, pose, f * 45.0D);
        tr(ms, pose, f * -1.0D, 3.6D, 3.5D);
        rz(ms, pose, f * 120.0D);
        rx(ms, pose, 200.0D);
        ry(ms, pose, f * -135.0D);
        tr(ms, pose, f * 5.6D, 0, 0);
        sc(ms, pose, 1.0D, 1.0D + STRETCH * ext, 1.0D);
        double[] tip = pose.apply(-f * 0.375D, 0.75D, 0.0D);

        PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
        if (right) renderer.renderRightHand(ms, buf, light, player);
        else renderer.renderLeftHand(ms, buf, light, player);
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

    // ------------------------------------------------------------ ring inventory

    private static double[] ringSlot(ClientPlayerEntity p, float pt, int i) {
        double px = p.xo + (p.getX() - p.xo) * pt;
        double py = p.yo + (p.getY() - p.yo) * pt;
        double pz = p.zo + (p.getZ() - p.zo) * pt;
        double by = py + 1.22D;
        double body = HandsState.anchorYaw;
        double yaw, dy = 0, r = RING_R, tilt = 0;
        if (i < 8) { yaw = body + i * 45.0D; }
        else if (i < 16) {
            int k = (i - 8) % 4; yaw = body + k * 90.0D; r = RING_R - 0.15D;
            if (i < 12) { dy = 0.62D; tilt = -35.0D; } else { dy = -0.62D; tilt = 35.0D; }
        } else if (i < 20) {
            int k = i - 16; yaw = body + (1 + 2*k) * 45.0D; r = RING_R - 0.15D; dy = -0.62D; tilt = 35.0D;
        } else {
            int k = i - 20; yaw = body + (1 + 2*k) * 45.0D; r = RING_R - 0.15D; dy = 0.62D; tilt = -35.0D;
        }
        double ang = Math.toRadians(yaw);
        return new double[]{px - Math.sin(ang)*r, by+dy, pz + Math.cos(ang)*r, yaw, tilt};
    }

    private static int findRingSlot(Minecraft mc) {
        ClientPlayerEntity p = mc.player;
        if (!HandSlots.ringReady(p)) return -1;
        Vector3d eye = p.getEyePosition(1.0F);
        Vector3d look = p.getViewVector(1.0F);
        int best = -1; double bestD = 0.3D;
        for (int i = 0; i < HandSlots.RING_COUNT; i++) {
            double[] s = ringSlot(p, 1.0F, i);
            Vector3d v = new Vector3d(s[0]-eye.x, s[1]-eye.y, s[2]-eye.z);
            double t = v.dot(look); if (t <= 0) continue;
            double d = v.subtract(look.scale(t)).length();
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }

    private static void ringTransform(MatrixStack ms, double[] s, Vector3d cam) {
        ms.translate(s[0]-cam.x, s[1]-cam.y, s[2]-cam.z);
        ms.mulPose(Vector3f.YP.rotationDegrees((float)-s[3]));
        ms.mulPose(Vector3f.XP.rotationDegrees((float)s[4]));
    }

    private static void drawRing(Minecraft mc, MatrixStack ms, IRenderTypeBuffer.Impl buf, Vector3d cam, float pt) {
        ClientPlayerEntity p = mc.player;
        if (p == null || !HandSlots.ringReady(p)) return;
        IVertexBuilder lines = buf.getBuffer(RenderType.lines());
        for (int i = 0; i < HandSlots.RING_COUNT; i++) {
            float r=0.85F,g=0.85F,b=0.85F;
            boolean hL = HandsState.ringHover[0]==i, hR = HandsState.ringHover[1]==i;
            if (hL&&hR){r=0.7F;g=0.3F;b=1F;}else if(hR){r=1F;g=0.2F;b=0.2F;}else if(hL){r=0.25F;g=0.5F;b=1F;}
            double[] s = ringSlot(p, pt, i);
            ms.pushPose(); ringTransform(ms, s, cam);
            WorldRenderer.renderLineBox(ms,lines,-PANEL,-PANEL,-0.01D,PANEL,PANEL,0.01D,r,g,b,0.9F);
            WorldRenderer.renderLineBox(ms,lines,-PANEL-0.01D,-PANEL-0.01D,-0.01D,PANEL+0.01D,PANEL+0.01D,0.01D,r,g,b,0.9F);
            if (HandSlots.getRing(p,i).isEmpty()) {
                WorldRenderer.renderLineBox(ms,lines,-0.07D,-0.005D,-0.005D,0.07D,0.005D,0.005D,1F,1F,1F,0.9F);
                WorldRenderer.renderLineBox(ms,lines,-0.005D,-0.07D,-0.005D,0.005D,0.07D,0.005D,1F,1F,1F,0.9F);
            }
            ms.popPose();
        }
        buf.endBatch(RenderType.lines());
        for (int i = 0; i < HandSlots.RING_COUNT; i++) {
            ItemStack st = HandSlots.getRing(p,i); if(st.isEmpty()) continue;
            double[] s = ringSlot(p,pt,i);
            ms.pushPose(); ringTransform(ms,s,cam);
            ms.mulPose(Vector3f.YP.rotationDegrees(180F)); ms.scale(0.4F,0.4F,0.4F);
            mc.getItemRenderer().renderStatic(st,ItemCameraTransforms.TransformType.FIXED,15728880,OverlayTexture.NO_OVERLAY,ms,buf);
            ms.popPose();
            if (st.getCount()>1) {
                String text=String.valueOf(st.getCount());
                ms.pushPose(); ms.translate(s[0]-cam.x,s[1]-cam.y-0.22D,s[2]-cam.z);
                ms.mulPose(mc.getEntityRenderDispatcher().cameraOrientation()); ms.scale(-0.02F,-0.02F,0.02F);
                float w=mc.font.width(text);
                mc.font.drawInBatch(text,-w/2F,0F,0xFFFFFFFF,false,ms.last().pose(),buf,true,0,15728880);
                ms.popPose();
            }
        }
        buf.endBatch();
    }

    // -------------------------------------------------------- world render

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!HandsState.modeOn) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        MatrixStack ms = event.getMatrixStack();
        Vector3d cam = mc.gameRenderer.getMainCamera().getPosition();
        IRenderTypeBuffer.Impl buf = mc.renderBuffers().bufferSource();

        if (HandsState.invOpen) drawRing(mc, ms, buf, cam, event.getPartialTicks());

        // grip arm lines (beige, from shoulder to grip point) - only for local player
        drawArmLines(mc, ms, buf, cam, event.getPartialTicks());

        // grip markers - drawn with depth test disabled so visible through blocks (local only)
        drawGripMarkers(ms, buf, cam);

        // item entity highlights
        for (int h = 0; h < 2; h++) {
            ItemEntity t = HandsState.target[h];
            if (!HandsState.down[h] || t == null || !t.isAlive()) continue;
            float r = h==0?0.25F:1F, g = h==0?0.5F:0.2F, b = h==0?1F:0.2F;
            AxisAlignedBB box = t.getBoundingBox().inflate(0.08D + 0.03D*h);
            IVertexBuilder lines = buf.getBuffer(RenderType.lines());
            ms.pushPose(); ms.translate(-cam.x,-cam.y,-cam.z);
            WorldRenderer.renderLineBox(ms,lines,box,r,g,b,1F);
            ms.popPose(); buf.endBatch(RenderType.lines());
            int count = t.getItem().getCount();
            if (count>1) {
                String text=String.valueOf(count);
                ms.pushPose();
                ms.translate(t.getX()-cam.x,t.getY()+t.getBbHeight()+0.4D-cam.y,t.getZ()-cam.z);
                ms.mulPose(mc.getEntityRenderDispatcher().cameraOrientation()); ms.scale(-0.03F,-0.03F,0.03F);
                float w=mc.font.width(text);
                int color=(255<<24)|((int)(r*255)<<16)|((int)(g*255)<<8)|(int)(b*255);
                mc.font.drawInBatch(text,-w/2F+(h==0?-8F:8F),0F,color,false,ms.last().pose(),buf,true,0,15728880);
                ms.popPose(); buf.endBatch();
            }
        }
    }

    /**
     * Draw solid beige arm rectangles from shoulder to grip point using GL11 quads.
     * Billboard: face the camera so the arm always has visible width.
     */
    private static void drawArmLines(Minecraft mc, MatrixStack ms, IRenderTypeBuffer.Impl buf, Vector3d cam, float pt) {
        ClientPlayerEntity p = mc.player;
        boolean anyGrip = HandsState.gripPoint[0] != null || HandsState.gripPoint[1] != null;
        if (!anyGrip) return;

        // flush existing render batches before raw GL
        buf.endBatch();

        double px = p.xo + (p.getX()-p.xo)*pt;
        double py = p.yo + (p.getY()-p.yo)*pt;
        double pz = p.zo + (p.getZ()-p.zo)*pt;

        net.minecraft.util.math.vector.Vector3f lv = mc.gameRenderer.getMainCamera().getLookVector();
        Vector3d camFwd = new Vector3d(lv.x(), lv.y(), lv.z());
        Vector3d camRight = camFwd.cross(new Vector3d(0,1,0)).normalize();

        RenderSystem.disableTexture();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableAlphaTest();

        Matrix4f mat = ms.last().pose();

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(ARM_R, ARM_G, ARM_B, 1.0F);

        for (int h = 0; h < 2; h++) {
            Vector3d gp = HandsState.gripPoint[h];
            if (gp == null) continue;

            double side = h == 1 ? 0.3D : -0.3D;
            // shoulder in camera-relative space
            float shX = (float)(px + camRight.x*side - cam.x);
            float shY = (float)(py + 1.35D - cam.y);
            float shZ = (float)(pz + camRight.z*side - cam.z);
            float gpX = (float)(gp.x - cam.x);
            float gpY = (float)(gp.y - cam.y);
            float gpZ = (float)(gp.z - cam.z);

            // perpendicular to arm direction and camera forward = gives thickness
            Vector3d armDir = new Vector3d(gpX-shX, gpY-shY, gpZ-shZ).normalize();
            Vector3d perp = armDir.cross(camFwd).normalize().scale(ARM_HALF);

            float px0 = (float)(shX + perp.x), py0 = (float)(shY + perp.y), pz0 = (float)(shZ + perp.z);
            float px1 = (float)(shX - perp.x), py1 = (float)(shY - perp.y), pz1 = (float)(shZ - perp.z);
            float px2 = (float)(gpX - perp.x), py2 = (float)(gpY - perp.y), pz2 = (float)(gpZ - perp.z);
            float px3 = (float)(gpX + perp.x), py3 = (float)(gpY + perp.y), pz3 = (float)(gpZ + perp.z);

            // transform through matrix stack
            org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush();
            java.nio.FloatBuffer fb = stack.mallocFloat(16);
            mat.store(fb);
            // apply matrix manually to each vertex
            glVertex(fb, px0, py0, pz0);
            glVertex(fb, px1, py1, pz1);
            glVertex(fb, px2, py2, pz2);
            glVertex(fb, px3, py3, pz3);
            stack.pop();
        }
        GL11.glEnd();

        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
        RenderSystem.enableAlphaTest();
    }

    private static void glVertex(java.nio.FloatBuffer m, float x, float y, float z) {
        // column-major Matrix4f: m[0..3]=col0, m[4..7]=col1, m[8..11]=col2, m[12..15]=col3
        float tx = m.get(0)*x + m.get(4)*y + m.get(8)*z + m.get(12);
        float ty = m.get(1)*x + m.get(5)*y + m.get(9)*z + m.get(13);
        float tz = m.get(2)*x + m.get(6)*y + m.get(10)*z + m.get(14);
        GL11.glVertex3f(tx, ty, tz);
    }

    /**
     * Grip markers drawn without depth test so they show through blocks (local only).
     */
    private static void drawGripMarkers(MatrixStack ms, IRenderTypeBuffer.Impl buf, Vector3d cam) {
        boolean any = false;
        for (int h = 0; h < 2; h++) {
            BlockRayTraceResult hit = HandsState.grip[h] != null ? HandsState.grip[h]
                    : (HandsState.down[h] ? HandsState.aim[h] : null);
            if (hit == null) continue;
            any = true;
        }
        if (!any) return;

        // flush existing batches then switch to no-depth lines
        buf.endBatch();

        // disable depth test so markers show through blocks
        RenderSystem.disableDepthTest();

        IVertexBuilder lines = buf.getBuffer(RenderType.lines());
        for (int h = 0; h < 2; h++) {
            BlockRayTraceResult hit = HandsState.grip[h] != null ? HandsState.grip[h]
                    : (HandsState.down[h] ? HandsState.aim[h] : null);
            if (hit == null) continue;
            float r = h==0?0.25F:1F, g = h==0?0.5F:0.2F, b = h==0?1F:0.2F;
            double half = (HandsState.grip[h] != null ? 0.09D : 0.07D) + 0.01D*h;
            Vector3d c = hit.getLocation();
            Direction d = hit.getDirection();
            double cx = c.x + d.getStepX()*0.05D;
            double cy = c.y + d.getStepY()*0.05D;
            double cz = c.z + d.getStepZ()*0.05D;
            ms.pushPose(); ms.translate(-cam.x,-cam.y,-cam.z);
            WorldRenderer.renderLineBox(ms, lines, cx-half, cy-half, cz-half, cx+half, cy+half, cz+half, r, g, b, 1F);
            ms.popPose();
        }
        buf.endBatch(RenderType.lines());
        RenderSystem.enableDepthTest();
    }

    // third person: raise arms when gripping
    @SubscribeEvent
    public void onRenderLivingPre(RenderLivingEvent.Pre event) {
        if (!HandsState.modeOn || event.getEntity() != Minecraft.getInstance().player) return;
        Object model = event.getRenderer().getModel();
        if (!(model instanceof PlayerModel)) return;
        PlayerModel<?> pm = (PlayerModel<?>) model;
        if (HandsState.grip[0] != null) pm.leftArmPose = BipedModel.ArmPose.THROW_SPEAR;
        if (HandsState.grip[1] != null) pm.rightArmPose = BipedModel.ArmPose.THROW_SPEAR;
    }

    // ------------------------------------------- inventory slots

    @SubscribeEvent
    public void onInventoryBackground(GuiContainerEvent.DrawBackground event) {
        if (!(event.getGuiContainer() instanceof InventoryScreen)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !HandSlots.ready(mc.player)) return;
        InventoryScreen screen = (InventoryScreen) event.getGuiContainer();
        MatrixStack ms = event.getMatrixStack();
        for (int h = 0; h < 2; h++) {
            int x = screen.getGuiLeft() + HandSlots.SCREEN_X;
            int y = screen.getGuiTop() + (h==0 ? HandSlots.SCREEN_Y_LEFT : HandSlots.SCREEN_Y_RIGHT);
            int rim = h==1?0xFFFF4040:0xFF4080FF;
            AbstractGui.fill(ms,x-2,y-2,x+18,y+18,rim);
            AbstractGui.fill(ms,x-1,y-1,x+17,y+17,0xFF373737);
            AbstractGui.fill(ms,x,y,x+17,y+17,0xFFFFFFFF);
            AbstractGui.fill(ms,x,y,x+16,y+16,0xFF8B8B8B);
            String label = new TranslationTextComponent(h==1?"label.handson.right_hand":"label.handson.left_hand").getString();
            ms.pushPose(); ms.translate(x+8F,y-12F,0F); ms.scale(0.75F,0.75F,1F);
            mc.font.drawShadow(ms,label,-mc.font.width(label)/2F,0F,0xFFFFFF);
            ms.popPose();
        }
        if (HandSlots.ringReady(mc.player)) {
            for (int i = 0; i < HandSlots.RING_COUNT; i++) {
                int x = screen.getGuiLeft() + HandSlots.SCREEN_RING_X + (i%4)*18;
                int y = screen.getGuiTop() + HandSlots.SCREEN_RING_Y + (i/4)*18;
                AbstractGui.fill(ms,x-1,y-1,x+17,y+17,0xFF373737);
                AbstractGui.fill(ms,x,y,x+17,y+17,0xFFFFFFFF);
                AbstractGui.fill(ms,x,y,x+16,y+16,0xFF8B8B8B);
            }
        }
    }

    // ------------------------------------------ vanilla HUD shifts

    private static boolean isLowered(RenderGameOverlayEvent.ElementType t) {
        return t==RenderGameOverlayEvent.ElementType.HEALTH||t==RenderGameOverlayEvent.ElementType.ARMOR
                ||t==RenderGameOverlayEvent.ElementType.FOOD||t==RenderGameOverlayEvent.ElementType.AIR
                ||t==RenderGameOverlayEvent.ElementType.HEALTHMOUNT||t==RenderGameOverlayEvent.ElementType.EXPERIENCE
                ||t==RenderGameOverlayEvent.ElementType.JUMPBAR;
    }

    @SubscribeEvent
    public void onOverlayPre(RenderGameOverlayEvent.Pre event) {
        if (!HandsState.modeOn) return;
        RenderGameOverlayEvent.ElementType t = event.getType();
        if (t==RenderGameOverlayEvent.ElementType.HOTBAR) event.setCanceled(true);
        else if (isLowered(t) && !event.isCanceled()) { event.getMatrixStack().pushPose(); event.getMatrixStack().translate(0,22,0); }
    }

    @SubscribeEvent
    public void onOverlayPostLowered(RenderGameOverlayEvent.Post event) {
        if (HandsState.modeOn && isLowered(event.getType())) event.getMatrixStack().popPose();
    }

    // -------------------------------------------------------------- HUD

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType()!=RenderGameOverlayEvent.ElementType.ALL || !HandsState.modeOn) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        MatrixStack ms = event.getMatrixStack();
        int w = mc.getWindow().getGuiScaledWidth();
        int hgt = mc.getWindow().getGuiScaledHeight();

        // top left: grip icons + lock icon
        int iconX = 6;
        // grip hand icons
        for (int h = 0; h < 2; h++) {
            if (HandsState.grip[h] == null) continue;
            int color = h==0?0xFF4080FF:0xFFFF4040;
            int x = iconX + h*20;
            int y = 6;
            AbstractGui.fill(ms,x-2,y-2,x+16,y+18,0x99000000);
            AbstractGui.fill(ms,x+2,y+7,x+14,y+16,color);
            AbstractGui.fill(ms,x+2,y+1,x+4,y+7,color);
            AbstractGui.fill(ms,x+5,y,x+7,y+7,color);
            AbstractGui.fill(ms,x+8,y,x+10,y+7,color);
            AbstractGui.fill(ms,x+11,y+1,x+13,y+7,color);
        }
        // lock icon: drawn at x=4, top left corner
        drawLockIcon(ms, 4, 6, HandsState.locked);

        drawHeld(mc, ms, HandSlots.get(mc.player,0), w/2-118, hgt-19, false);
        drawHeld(mc, ms, HandSlots.get(mc.player,1), w/2+102, hgt-19, true);
        drawHints(mc, ms, hgt);
    }

    /** Lock icon: a padlock shape, gold=locked grey=unlocked. No text. */
    private static void drawLockIcon(MatrixStack ms, int x, int y, boolean locked) {
        int body  = locked ? 0xFFE0B030 : 0xFF909090;
        int shackle = 0xFFCCCCCC;
        // shackle (arch above the body)
        if (locked) {
            AbstractGui.fill(ms,x+2,y,  x+10,y+2, shackle);
            AbstractGui.fill(ms,x+2,y,  x+4, y+6, shackle);
            AbstractGui.fill(ms,x+8,y,  x+10,y+6, shackle);
        } else {
            // open: shackle swung to the right
            AbstractGui.fill(ms,x+4,y-2,x+12,y,  shackle);
            AbstractGui.fill(ms,x+10,y-2,x+12,y+5,shackle);
        }
        // body of the lock
        AbstractGui.fill(ms,x,  y+5, x+12, y+13, body);
    }

    private static String tr(String key, Object... args) {
        return new TranslationTextComponent(key, args).getString();
    }

    private static void drawHints(Minecraft mc, MatrixStack ms, int hgt) {
        boolean more = Screen.hasShiftDown() || mc.options.keyShift.isDown();
        List<String> lines = new ArrayList<>();
        boolean ground=false, slot=false;
        for (int h=0;h<2;h++){if(HandsState.target[h]!=null)ground=true;if(HandsState.ringHover[h]>=0)slot=true;}
        if (slot) lines.add(tr("hint.handson.slot"));
        else if (ground) lines.add(tr("hint.handson.take"));
        boolean aiming=false,gripping=false;
        for(int h=0;h<2;h++){if(HandsState.aim[h]!=null)aiming=true;if(HandsState.grip[h]!=null)gripping=true;}
        if (aiming) lines.add(tr("hint.handson.grab")+(more?" "+tr("hint.handson.grab.more"):""));
        if (gripping) lines.add(tr("hint.handson.release",HandKeys.RELEASE.getTranslatedKeyMessage())+(more?" "+tr("hint.handson.release.more"):""));
        lines.add(tr("hint.handson.hands",HandKeys.LEFT.getTranslatedKeyMessage(),HandKeys.RIGHT.getTranslatedKeyMessage())+(more?" "+tr("hint.handson.hands.more"):""));
        String lockLine = tr("hint.handson.lock",HandKeys.LOCK.getTranslatedKeyMessage());
        if (HandsState.locked) lockLine += " "+tr("hint.handson.lock.active");
        if (more) lockLine += " "+tr("hint.handson.lock.more");
        lines.add(lockLine);
        lines.add(tr("hint.handson.inventory",HandKeys.INVENTORY.getTranslatedKeyMessage(),HandKeys.INVENTORY_ALT.getTranslatedKeyMessage())+(more?" "+tr("hint.handson.inventory.more"):""));

        float scale=0.7F;
        ms.pushPose(); ms.scale(scale,scale,1F);
        float y=(hgt-5)/scale - 9F*lines.size();
        for (String line:lines){mc.font.drawShadow(ms,line,6F/scale,y,0xBBBBBB);y+=9F;}
        ms.popPose();
    }

    private static void drawHotbarCell(Minecraft mc, MatrixStack ms, int x, int y) {
        mc.getTextureManager().bind(Widget.WIDGETS_LOCATION);
        RenderSystem.color4f(1,1,1,1);
        AbstractGui.blit(ms,x-3,y-3,0,0F,0F,21,22,256,256);
        AbstractGui.blit(ms,x+18,y-3,0,181F,0F,1,22,256,256);
    }

    private static void drawHeld(Minecraft mc, MatrixStack ms, ItemStack stack, int x, int y, boolean right) {
        drawHotbarCell(mc,ms,x,y);
        if (stack.isEmpty()) return;
        mc.getItemRenderer().renderAndDecorateItem(stack,x,y);
        mc.getItemRenderer().renderGuiItemDecorations(mc.font,stack,x,y);
        List<IReorderingProcessor> lines=mc.font.split(stack.getHoverName(),90);
        int n=Math.min(lines.size(),5);
        for(int i=0;i<n;i++){
            IReorderingProcessor line=lines.get(i);
            float lw=mc.font.width(line);
            mc.font.drawShadow(ms,line,x+8F-lw/2F,y-5F-(n-i)*10F,0xFFFFFF);
        }
    }
}
