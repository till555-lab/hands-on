package com.handson.server;

import com.handson.HandSlots;
import com.handson.HandsInventory;
import com.handson.RingInventory;
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
    /** Saved in the persistent data of the player: the control mode and the direction of the ring. */
    public static final String TAG_MODE = "HandsOnMode";
    public static final String TAG_ANCHOR = "HandsOnRingYaw";

    /** Sets the control mode for a player and tells the client about it. */
    public static void setMode(ServerPlayerEntity player, boolean on) {
        if (on) {
            MODE.add(player.getUUID());
        } else {
            MODE.remove(player.getUUID());
        }
        player.getPersistentData().putBoolean(TAG_MODE, on);
        HandsNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new HandsPacket(HandsPacket.SET_MODE, 0, 0, on));
    }

    /** Sends the saved ring direction and control mode to the client. The direction is chosen once, on the first join. */
    public static void syncToClient(ServerPlayerEntity player) {
        CompoundNBT data = player.getPersistentData();
        if (!data.contains(TAG_ANCHOR, 3)) {
            data.putInt(TAG_ANCHOR, Math.floorMod(Math.round(player.yRot / 90.0F) * 90, 360));
        }
        HandsNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new HandsPacket(HandsPacket.SET_ANCHOR, 0, data.getInt(TAG_ANCHOR), false));
        if (data.getBoolean(TAG_MODE)) {
            setMode(player, true);
        }
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
        for (String tag : new String[] {HandsInventory.TAG, RingInventory.TAG}) {
            if (old.contains(tag, 10)) {
                event.getPlayer().getPersistentData().put(tag, old.getCompound(tag).copy());
            }
        }
        if (old.contains(TAG_ANCHOR, 3)) {
            event.getPlayer().getPersistentData().putInt(TAG_ANCHOR, old.getInt(TAG_ANCHOR));
        }
        if (old.contains(TAG_MODE)) {
            event.getPlayer().getPersistentData().putBoolean(TAG_MODE, old.getBoolean(TAG_MODE));
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
        for (int i = 0; i < HandSlots.RING_COUNT; i++) {
            ItemStack stack = HandSlots.getRing(player, i);
            if (!stack.isEmpty()) {
                player.drop(stack.copy(), true, false);
                HandSlots.setRing(player, i, ItemStack.EMPTY);
            }
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            syncToClient((ServerPlayerEntity) event.getPlayer());
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
        // one more sync a second after joining, in case the first packet came before the client was ready
        if (event.player.tickCount == 20 && event.player instanceof ServerPlayerEntity) {
            syncToClient((ServerPlayerEntity) event.player);
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
        ItemStack[] ring = new ItemStack[HandSlots.RING_COUNT];
        for (int i = 0; i < ring.length; i++) {
            ring[i] = HandSlots.getRing(event.player, i);
        }
        applyDurability(Arrays.asList(ring), on);
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
