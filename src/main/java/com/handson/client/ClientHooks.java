package com.handson.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TranslationTextComponent;

/** Client-only entry points called from network packets. */
public class ClientHooks {
    public static void applyAnchor(int yaw) {
        HandsState.anchorYaw = yaw;
    }

    public static void applyMode(boolean on) {
        Minecraft mc = Minecraft.getInstance();
        HandsState.modeOn = on;
        HandsState.clearHands();
        if (mc.player != null) {
            mc.player.displayClientMessage(new TranslationTextComponent(
                    on ? "message.handson.mode_on" : "message.handson.mode_off"), true);
        }
    }
}
