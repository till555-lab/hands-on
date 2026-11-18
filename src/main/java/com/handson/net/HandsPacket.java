package com.handson.net;

import com.handson.server.ServerEvents;
import java.util.function.Supplier;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.Hand;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.fml.network.NetworkEvent;

public class HandsPacket {
    public static final int MODE = 0;
    public static final int GRAB = 1;
    public static final int DROP = 2;

    public final int action;
    public final int hand; // 0 = left (offhand), 1 = right (main hand)
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
                return;
            }
            if (p.action == MODE) {
                if (p.flag) {
                    ServerEvents.MODE.add(player.getUUID());
                } else {
                    ServerEvents.MODE.remove(player.getUUID());
                }
                return;
            }
            if (!ServerEvents.MODE.contains(player.getUUID())) {
                return;
            }
            Hand hand = p.hand == 0 ? Hand.OFF_HAND : Hand.MAIN_HAND;
            if (p.action == GRAB) {
                grab(player, hand, p.id);
            } else if (p.action == DROP) {
                drop(player, hand);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void grab(ServerPlayerEntity player, Hand hand, int entityId) {
        Entity e = player.level.getEntity(entityId);
        if (!(e instanceof ItemEntity) || !e.isAlive()) {
            return;
        }
        if (player.distanceToSqr(e) > 36.0D) {
            return;
        }
        ItemEntity itemEntity = (ItemEntity) e;
        ItemStack src = itemEntity.getItem();
        ItemStack cur = player.getItemInHand(hand);
        if (cur.isEmpty()) {
            player.setItemInHand(hand, src.copy());
            itemEntity.remove();
        } else if (ItemStack.isSame(cur, src) && ItemStack.tagMatches(cur, src)) {
            int space = cur.getMaxStackSize() - cur.getCount();
            int move = Math.min(space, src.getCount());
            if (move > 0) {
                cur.grow(move);
                src.shrink(move);
                if (src.isEmpty()) {
                    itemEntity.remove();
                } else {
                    itemEntity.setItem(src);
                }
            }
        }
    }

    private static void drop(ServerPlayerEntity player, Hand hand) {
        ItemStack cur = player.getItemInHand(hand);
        if (cur.isEmpty()) {
            return;
        }
        ItemStack dropped = cur.copy();
        player.setItemInHand(hand, ItemStack.EMPTY);
        Vector3d look = player.getLookAngle();
        Vector3d pos = player.getEyePosition(1.0F).add(look.x * 0.5D, look.y * 0.5D - 0.3D, look.z * 0.5D);
        ItemEntity itemEntity = new ItemEntity(player.level, pos.x, pos.y, pos.z, dropped);
        itemEntity.setDeltaMovement(look.x * 0.25D, look.y * 0.25D + 0.1D, look.z * 0.25D);
        itemEntity.setPickUpDelay(20);
        player.level.addFreshEntity(itemEntity);
    }
}
