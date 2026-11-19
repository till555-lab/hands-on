package com.handson.net;

import com.handson.HandSlots;
import com.handson.client.ClientHooks;
import com.handson.server.ServerEvents;
import java.util.function.Supplier;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.Util;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

public class HandsPacket {
    /** Client to server: player asks to switch between vanilla and Hands On controls (needs cheats). */
    public static final int REQUEST_TOGGLE = 0;
    /** Client to server: grab the item entity with the given id. */
    public static final int GRAB = 1;
    /** Client to server: drop the item in the given hand. */
    public static final int DROP = 2;
    /** Server to client: set the control mode. */
    public static final int SET_MODE = 3;
    /** Client to server: put the item of the given hand into the ring slot (or take it from the slot). */
    public static final int RING = 4;

    public final int action;
    public final int hand; // 0 = left hand slot, 1 = right hand slot
    public final int id;
    public final boolean flag;

    public HandsPacket(int action, int hand, int id, boolean flag) {
        this.action = action;
        this.hand = hand;
        this.id = id;
        this.flag = flag;
    }

    public static void encode(HandsPacket p, PacketBuffer buf) {
        buf.writeVarInt(p.action);
        buf.writeVarInt(p.hand);
        buf.writeVarInt(p.id);
        buf.writeBoolean(p.flag);
    }

    public static HandsPacket decode(PacketBuffer buf) {
        int action = buf.readVarInt();
        int hand = buf.readVarInt();
        int id = buf.readVarInt();
        boolean flag = buf.readBoolean();
        return new HandsPacket(action, hand, id, flag);
    }

    public static void handle(HandsPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player == null) {
                // received on the client
                if (p.action == SET_MODE) {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.applyMode(p.flag));
                }
                return;
            }
            if (p.action == REQUEST_TOGGLE) {
                if (!player.createCommandSourceStack().hasPermission(2)) {
                    player.sendMessage(new TranslationTextComponent("message.handson.need_cheats"), Util.NIL_UUID);
                    return;
                }
                ServerEvents.setMode(player, !ServerEvents.MODE.contains(player.getUUID()));
                return;
            }
            if (!ServerEvents.MODE.contains(player.getUUID())) {
                return;
            }
            if (!HandSlots.ready(player)) {
                return;
            }
            int h = p.hand == 1 ? 1 : 0;
            if (p.action == GRAB) {
                grab(player, h, p.id);
            } else if (p.action == DROP) {
                drop(player, h);
            } else if (p.action == RING) {
                ringMove(player, h, p.id);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void ringMove(ServerPlayerEntity player, int h, int slot) {
        if (slot < 0 || slot >= HandSlots.RING_COUNT || !HandSlots.ringReady(player)) {
            return;
        }
        ItemStack hand = HandSlots.get(player, h);
        ItemStack in = HandSlots.getRing(player, slot);
        if (hand.isEmpty() && in.isEmpty()) {
            return;
        }
        if (hand.isEmpty()) {
            HandSlots.set(player, h, in.copy());
            HandSlots.setRing(player, slot, ItemStack.EMPTY);
        } else if (in.isEmpty()) {
            HandSlots.setRing(player, slot, hand.copy());
            HandSlots.set(player, h, ItemStack.EMPTY);
        } else if (ItemStack.isSame(hand, in) && ItemStack.tagMatches(hand, in)
                && in.getCount() < in.getMaxStackSize()) {
            int move = Math.min(in.getMaxStackSize() - in.getCount(), hand.getCount());
            in.grow(move);
            hand.shrink(move);
            HandSlots.setRing(player, slot, in);
            HandSlots.set(player, h, hand.isEmpty() ? ItemStack.EMPTY : hand);
        } else {
            ItemStack a = hand.copy();
            ItemStack b = in.copy();
            HandSlots.set(player, h, b);
            HandSlots.setRing(player, slot, a);
        }
    }

    private static void grab(ServerPlayerEntity player, int h, int entityId) {
        Entity e = player.level.getEntity(entityId);
        if (!(e instanceof ItemEntity) || !e.isAlive()) {
            return;
        }
        if (player.distanceToSqr(e) > 36.0D) {
            return;
        }
        ItemEntity itemEntity = (ItemEntity) e;
        ItemStack src = itemEntity.getItem();
        ItemStack cur = HandSlots.get(player, h);
        if (cur.isEmpty()) {
            HandSlots.set(player, h, src.copy());
            itemEntity.remove();
        } else if (ItemStack.isSame(cur, src) && ItemStack.tagMatches(cur, src)) {
            int space = cur.getMaxStackSize() - cur.getCount();
            int move = Math.min(space, src.getCount());
            if (move > 0) {
                cur.grow(move);
                HandSlots.set(player, h, cur);
                src.shrink(move);
                if (src.isEmpty()) {
                    itemEntity.remove();
                } else {
                    itemEntity.setItem(src);
                }
            }
        }
    }

    private static void drop(ServerPlayerEntity player, int h) {
        ItemStack cur = HandSlots.get(player, h);
        if (cur.isEmpty()) {
            return;
        }
        ItemStack dropped = cur.copy();
        HandSlots.set(player, h, ItemStack.EMPTY);
        Vector3d look = player.getLookAngle();
        Vector3d pos = player.getEyePosition(1.0F).add(look.x * 0.5D, look.y * 0.5D - 0.3D, look.z * 0.5D);
        ItemEntity itemEntity = new ItemEntity(player.level, pos.x, pos.y, pos.z, dropped);
        itemEntity.setDeltaMovement(look.x * 0.25D, look.y * 0.25D + 0.1D, look.z * 0.25D);
        itemEntity.setPickUpDelay(20);
        player.level.addFreshEntity(itemEntity);
    }
}
