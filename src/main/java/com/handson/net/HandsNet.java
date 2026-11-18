package com.handson.net;

import com.handson.HandsOn;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;

public class HandsNet {
    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(HandsOn.MODID, "main"),
            () -> VERSION,
            VERSION::equals,
            VERSION::equals);

    public static void register() {
        CHANNEL.registerMessage(0, HandsPacket.class,
                HandsPacket::encode, HandsPacket::decode, HandsPacket::handle);
    }
}
