package com.handson;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;

/** Storage of the ring inventory slots. Extends Inventory so Slot accepts it. */
public class RingInventory extends Inventory {
    public static final String TAG = "HandsOnRing";

    private final PlayerEntity owner;
    private boolean loading = false;

    public RingInventory(PlayerEntity owner) {
        super(HandSlots.RING_COUNT);
        this.owner = owner;
        if (!owner.level.isClientSide) {
            load();
        }
    }

    private void load() {
        CompoundNBT root = owner.getPersistentData();
        if (!root.contains(TAG, 10)) return;
        CompoundNBT tag = root.getCompound(TAG);
        loading = true;
        for (int i = 0; i < HandSlots.RING_COUNT; i++) {
            setItem(i, ItemStack.of(tag.getCompound("S" + i)));
        }
        loading = false;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (loading || owner.level.isClientSide) return;
        CompoundNBT tag = new CompoundNBT();
        for (int i = 0; i < HandSlots.RING_COUNT; i++) {
            tag.put("S" + i, getItem(i).save(new CompoundNBT()));
        }
        owner.getPersistentData().put(TAG, tag);
    }
}
