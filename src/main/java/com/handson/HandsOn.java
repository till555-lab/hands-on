package com.handson;

import com.handson.client.ClientEvents;
import com.handson.client.HandKeys;
import com.handson.net.HandsNet;
import com.handson.server.ServerEvents;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(HandsOn.MODID)
public class HandsOn {
    public static final String MODID = "handson";

    public HandsOn() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
        MinecraftForge.EVENT_BUS.register(new ServerEvents());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        HandsNet.register();
    }

    private void clientSetup(FMLClientSetupEvent event) {
        HandKeys.register();
        MinecraftForge.EVENT_BUS.register(new ClientEvents());
    }
}
