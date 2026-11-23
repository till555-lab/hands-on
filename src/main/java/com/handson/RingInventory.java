package com.handson;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.entity.player.PlayerInventory;

import java.util.Arrays;

/**
 * Storage of the ring inventory slots.
 * Does NOT extend Inventory so items are never visible in vanilla creative screen.
 */
public class RingInventory implements IInventory {
    public static final String TAG = "HandsOnRing";

    private final PlayerEntity owner;
    private final ItemStack[] items;
    private boolean loading = false;

    public RingInventory(PlayerEntity owner) {
        this.owner = owner;
        this.items = new ItemStack[HandSlots.RING_COUNT];
        Arrays.fill(this.items, ItemStack.EMPTY);
        if (!owner.level.isClientSide) {
            load();
        }
    }

    private void load() {
        CompoundNBT root = owner.getPersistentData();
        if (!root.contains(TAG, 10)) return;
        CompoundNBT tag = root.getCompound(TAG);
        loading = true;
        for (int i = 0; i < items.length; i++) {
            items[i] = ItemStack.of(tag.getCompound("S" + i));
        }
        loading = false;
    }

    @Override
    public void setChanged() {
        if (loading || owner.level.isClientSide) return;
        CompoundNBT tag = new CompoundNBT();
        for (int i = 0; i < items.length; i++) {
            tag.put("S" + i, items[i].save(new CompoundNBT()));
        }
        owner.getPersistentData().put(TAG, tag);
    }

    @Override public int getContainerSize() { return items.length; }
    @Override public boolean isEmpty() { for (ItemStack s : items) if (!s.isEmpty()) return false; return true; }
    @Override public ItemStack getItem(int slot) { return slot >= 0 && slot < items.length ? items[slot] : ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int slot, int count) {
        if (slot < 0 || slot >= items.length || items[slot].isEmpty()) return ItemStack.EMPTY;
        ItemStack result = items[slot].split(count);
        if (items[slot].isEmpty()) items[slot] = ItemStack.EMPTY;
        setChanged();
        return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= items.length) return ItemStack.EMPTY;
        ItemStack result = items[slot];
        items[slot] = ItemStack.EMPTY;
        return result;
    }
    @Override public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= items.length) return;
        items[slot] = stack;
        setChanged();
    }
    @Override public boolean stillValid(PlayerEntity player) { return true; }
    @Override public void clearContent() { Arrays.fill(items, ItemStack.EMPTY); setChanged(); }
}
