package com.femboycompanion.client;

import com.femboycompanion.FemboyCompanion;
import com.femboycompanion.registry.ModEntities;
import com.femboycompanion.registry.ModItems;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = FemboyCompanion.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        RenderingRegistry.registerEntityRenderingHandler(ModEntities.FEMBOY.get(), FemboyRenderer::new);
    }

    @SubscribeEvent
    public static void onItemColors(ColorHandlerEvent.Item event) {
        event.getItemColors().register((stack, tint) -> {
            if (stack.getItem() == ModItems.WHITE_EGG.get()) {
                return tint == 0 ? 0xF2F2F5 : 0x9AA3B2;
            }
            return tint == 0 ? 0x2B2B32 : 0x6B6B78;
        }, ModItems.WHITE_EGG.get(), ModItems.BLACK_EGG.get());
    }
}
