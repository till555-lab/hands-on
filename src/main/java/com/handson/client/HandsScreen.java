package com.handson.client;

import com.handson.HandSlots;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

/** Inventory screen of the Hands On mode: only the two hand slots. */
public class HandsScreen extends Screen {
    public HandsScreen() {
        super(new StringTextComponent(""));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int slotX(int h) {
        return this.width / 2 + (h == 0 ? -50 : 34);
    }

    private int slotY() {
        return this.height / 2 - 8;
    }

    private boolean over(int h, double mx, double my) {
        int x = slotX(h);
        int y = slotY();
        return mx >= x && mx < x + 16 && my >= y && my < y + 16;
    }

    @Override
    public void render(MatrixStack ms, int mx, int my, float pt) {
        this.renderBackground(ms);
        Minecraft mc = Minecraft.getInstance();
        ItemStack hovered = ItemStack.EMPTY;
        for (int h = 0; h < 2; h++) {
            int x = slotX(h);
            int y = slotY();
            int rim = h == 1 ? 0xFFFF4040 : 0xFF4080FF;
            AbstractGui.fill(ms, x - 3, y - 3, x + 19, y + 19, rim);
            AbstractGui.fill(ms, x - 1, y - 1, x + 17, y + 17, 0xFF8B8B8B);

            String label = new TranslationTextComponent(
                    h == 1 ? "label.handson.right_hand" : "label.handson.left_hand").getString();
            ms.pushPose();
            ms.translate(x + 8.0F, y - 12.0F, 0.0F);
            ms.scale(0.75F, 0.75F, 1.0F);
            this.font.drawShadow(ms, label, -this.font.width(label) / 2.0F, 0.0F, 0xFFFFFF);
            ms.popPose();

            ItemStack stack = HandSlots.get(mc.player, h);
            if (!stack.isEmpty()) {
                mc.getItemRenderer().renderAndDecorateItem(stack, x, y);
                mc.getItemRenderer().renderGuiItemDecorations(this.font, stack, x, y);
            }
            if (over(h, mx, my)) {
                AbstractGui.fill(ms, x, y, x + 16, y + 16, 0x80FFFFFF);
                hovered = stack;
            }
        }
        ItemStack carried = mc.player.inventory.getCarried();
        if (!carried.isEmpty()) {
            mc.getItemRenderer().blitOffset = 200.0F;
            mc.getItemRenderer().renderAndDecorateItem(carried, mx - 8, my - 8);
            mc.getItemRenderer().renderGuiItemDecorations(this.font, carried, mx - 8, my - 8);
            mc.getItemRenderer().blitOffset = 0.0F;
        } else if (!hovered.isEmpty()) {
            this.renderTooltip(ms, hovered, mx, my);
        }
        super.render(ms, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 && button != 1) {
            return super.mouseClicked(mx, my, button);
        }
        Minecraft mc = Minecraft.getInstance();
        for (int h = 0; h < 2; h++) {
            if (over(h, mx, my)) {
                mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId,
                        HandSlots.LEFT + h, button, ClickType.PICKUP, mc.player);
                return true;
            }
        }
        if (!mc.player.inventory.getCarried().isEmpty()) {
            mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId,
                    -999, button, ClickType.PICKUP, mc.player);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.closeContainer();
        } else {
            super.onClose();
        }
    }
}
