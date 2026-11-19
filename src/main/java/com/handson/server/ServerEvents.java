package com.handson.server;

import com.handson.HandSlots;
import com.handson.HandsInventory;
import com.handson.net.HandsNet;
import com.handson.net.HandsPacket;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.world.GameRules;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.item.ItemExpireEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
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

    /** Dropped items never despawn: the despawn is cancelled and the timer is reset. */
    @SubscribeEvent
    public void onItemExpire(ItemExpireEvent event) {
        event.setExtraLife(6000);
        event.setCanceled(true);
    }

    /** Adds the hand slots to every player (client and server). */
    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent event) {
        if (event.getEntity() instanceof PlayerEntity) {
            HandSlots.install((PlayerEntity) event.getEntity());
        }
    }

    /** Safety net in case a player did not get the slots when joining. */
    @SubscribeEvent
    public void onPlayerTickStart(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            HandSlots.install(event.player);
        }
    }

    /** The hand slots survive death (keepInventory) and dimension changes. */
    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        CompoundNBT old = event.getOriginal().getPersistentData();
        if (old.contains(HandsInventory.TAG, 10)) {
            event.getPlayer().getPersistentData().put(HandsInventory.TAG, old.getCompound(HandsInventory.TAG).copy());
        }
    }

    /** Items in the hand slots drop on death unless keepInventory is on. */
    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayerEntity)) {
            return;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) event.getEntity();
        if (player.level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }
        for (int h = 0; h < 2; h++) {
            ItemStack stack = HandSlots.get(player, h);
            if (!stack.isEmpty()) {
                player.drop(stack.copy(), true, false);
                HandSlots.set(player, h, ItemStack.EMPTY);
            }
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
        applyDurability(Arrays.asList(HandSlots.get(event.player, 0), HandSlots.get(event.player, 1)), on);
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
