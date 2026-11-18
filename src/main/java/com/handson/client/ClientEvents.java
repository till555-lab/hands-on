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
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.HandSide;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ClientEvents {
    private static final float SPEED = 0.34F;
    private static final double REACH = 4.0D;

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

        boolean toggleNow = mc.screen == null && HandKeys.TOGGLE.isDown();
        if (toggleNow && !toggleWas) {
            toggleMode(mc);
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

    private static void drain(KeyBinding key) {
        key.setDown(false);
        while (key.consumeClick()) {
            // discard clicks
        }
    }

    private void toggleMode(Minecraft mc) {
        HandsState.modeOn = !HandsState.modeOn;
        HandsState.clearHands();
        HandsNet.CHANNEL.sendToServer(new HandsPacket(HandsPacket.MODE, 0, 0, HandsState.modeOn));
        mc.player.displayClientMessage(new TranslationTextComponent(
                HandsState.modeOn ? "message.handson.mode_on" : "message.handson.mode_off"), true);
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
            float ext = MathHelper.lerp(pt, HandsState.prevExt[h], HandsState.ext[h]);
            drawHand(mc, player, event.getMatrixStack(), event.getBuffers(), event.getLight(),
                    h == 1 ? HandSide.RIGHT : HandSide.LEFT, ext);
        }
    }

    private static void drawHand(Minecraft mc, ClientPlayerEntity player, MatrixStack ms,
                                 IRenderTypeBuffer buf, int light, HandSide side, float ext) {
        boolean right = side == HandSide.RIGHT;
        float f = right ? 1.0F : -1.0F;

        // arm (placeholder: the player's own skin arm)
        ms.pushPose();
        ms.translate(f * -0.12D * ext, 0.22D * ext, -1.15D * ext);
        ms.translate(f * 0.64D, -0.6D, -0.72D);
        ms.mulPose(Vector3f.YP.rotationDegrees(f * 45.0F));
        ms.translate(f * -1.0D, 3.6D, 3.5D);
        ms.mulPose(Vector3f.ZP.rotationDegrees(f * 120.0F));
        ms.mulPose(Vector3f.XP.rotationDegrees(200.0F));
        ms.mulPose(Vector3f.YP.rotationDegrees(f * -135.0F));
        ms.translate(f * 5.6D, 0.0D, 0.0D);
        PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
        if (right) {
            renderer.renderRightHand(ms, buf, light, player);
        } else {
            renderer.renderLeftHand(ms, buf, light, player);
        }
        ms.popPose();

        // item held in this hand (normal 2D item look)
        ItemStack stack = player.getItemInHand(right ? Hand.MAIN_HAND : Hand.OFF_HAND);
        if (!stack.isEmpty()) {
            ms.pushPose();
            ms.translate(f * -0.12D * ext, 0.22D * ext, -1.15D * ext);
            ms.translate(f * 0.56D, -0.52D, -0.72D);
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
        MatrixStack ms = event.getMatrixStack();
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

        String text = new TranslationTextComponent(
                locked ? "hud.handson.locked" : "hud.handson.unlocked").getString();
        mc.font.drawShadow(ms, text, x + 16, y + 3, 0xFFFFFF);
    }
}
