package com.handson.server;

import com.handson.net.HandsNet;
import com.handson.net.HandsPacket;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.network.PacketDistributor;

public class ServerEvents {
    /** Players who currently use the Hands On controls. */
    public static final Set<UUID> MODE = ConcurrentHashMap.newKeySet();

    private static final String TAG_UNBREAKABLE = "Unbreakable";
    private static final String TAG_TEMP = "HandsOnTemp";

    /** Sets the control mode for a player and tells the client about it. */
    public static void setMode(ServerPlayerEntity player, boolean on) {
        if (on) {
            MODE.add(player.getUUID());
        } else {
            MODE.remove(player.getUUID());
        }
        HandsNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new HandsPacket(HandsPacket.SET_MODE, 0, 0, on));
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onPickup(EntityItemPickupEvent event) {
        if (MODE.contains(event.getPlayer().getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        MODE.remove(event.getPlayer().getUUID());
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level.isClientSide) {
            return;
        }
        if (event.player.tickCount % 10 != 0) {
            return;
        }
        boolean on = MODE.contains(event.player.getUUID());
        PlayerInventory inv = event.player.inventory;
        applyDurability(inv.items, on);
        applyDurability(inv.armor, on);
        applyDurability(inv.offhand, on);
    }

    /** In Hands On mode damageable items become unbreakable; the marker is removed again afterwards. */
    private static void applyDurability(List<ItemStack> list, boolean on) {
        for (ItemStack stack : list) {
            if (stack.isEmpty()) {
                continue;
            }
            if (on) {
                if (stack.isDamageableItem()) {
                    CompoundNBT tag = stack.getOrCreateTag();
                    if (!tag.getBoolean(TAG_UNBREAKABLE)) {
                        tag.putBoolean(TAG_UNBREAKABLE, true);
                        tag.putBoolean(TAG_TEMP, true);
                    }
                }
            } else {
                CompoundNBT tag = stack.getTag();
                if (tag != null && tag.getBoolean(TAG_TEMP)) {
                    tag.remove(TAG_UNBREAKABLE);
                    tag.remove(TAG_TEMP);
                    if (tag.isEmpty()) {
                        stack.setTag(null);
                    }
                }
            }
        }
    }
}
