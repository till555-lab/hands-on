package com.handson;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;

/** Storage of the two hand slots. On the server it is saved in the persistent data of the player. */
public class HandsInventory extends Inventory {
    public static final String TAG = "HandsOnHands";

    private final PlayerEntity owner;
    private boolean loading = false;

    public HandsInventory(PlayerEntity owner) {
        super(2);
        this.owner = owner;
        if (!owner.level.isClientSide) {
            load();
        }
    }

    private void load() {
        CompoundNBT root = owner.getPersistentData();
        if (!root.contains(TAG, 10)) {
            return;
        }
        CompoundNBT tag = root.getCompound(TAG);
        loading = true;
        setItem(0, ItemStack.of(tag.getCompound("L")));
        setItem(1, ItemStack.of(tag.getCompound("R")));
        loading = false;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (loading || owner.level.isClientSide) {
            return;
        }
        CompoundNBT tag = new CompoundNBT();
        tag.put("L", getItem(0).save(new CompoundNBT()));
        tag.put("R", getItem(1).save(new CompoundNBT()));
        owner.getPersistentData().put(TAG, tag);
    }
}
