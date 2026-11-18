package com.femboycompanion.registry;

import com.femboycompanion.FemboyCompanion;
import com.femboycompanion.entity.FemboyEntity;
import com.femboycompanion.item.FemboySpawnEggItem;

import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, FemboyCompanion.MOD_ID);

    public static final ItemGroup TAB = new ItemGroup("femboycompanion") {
        @Override
        public ItemStack makeIcon() {
            return new ItemStack(ModItems.WHITE_EGG.get());
        }
    };

    private static Item.Properties props() {
        return new Item.Properties().tab(TAB);
    }

    public static final RegistryObject<Item> WHITE_EGG = ITEMS.register("white_femboy_spawn_egg",
            () -> new FemboySpawnEggItem(FemboyEntity.VARIANT_WHITE, props()));
    public static final RegistryObject<Item> BLACK_EGG = ITEMS.register("black_femboy_spawn_egg",
            () -> new FemboySpawnEggItem(FemboyEntity.VARIANT_BLACK, props()));

    public static final RegistryObject<Item> WHITE_EARS = ITEMS.register("white_ears", () -> new Item(props()));
    public static final RegistryObject<Item> WHITE_DOUBLE_EARS =
            ITEMS.register("white_double_ears", () -> new Item(props()));
    public static final RegistryObject<Item> WHITE_TAIL = ITEMS.register("white_tail", () -> new Item(props()));
    public static final RegistryObject<Item> WHITE_PAWS = ITEMS.register("white_paws", () -> new Item(props()));
    public static final RegistryObject<Item> BLACK_EARS = ITEMS.register("black_ears", () -> new Item(props()));
    public static final RegistryObject<Item> BLACK_DOUBLE_EARS =
            ITEMS.register("black_double_ears", () -> new Item(props()));
    public static final RegistryObject<Item> BLACK_TAIL = ITEMS.register("black_tail", () -> new Item(props()));
    public static final RegistryObject<Item> BLACK_PAWS = ITEMS.register("black_paws", () -> new Item(props()));

    public static Item ears(int variant) {
        return (variant == FemboyEntity.VARIANT_BLACK ? BLACK_EARS : WHITE_EARS).get();
    }

    public static Item tail(int variant) {
        return (variant == FemboyEntity.VARIANT_BLACK ? BLACK_TAIL : WHITE_TAIL).get();
    }

    public static Item paws(int variant) {
        return (variant == FemboyEntity.VARIANT_BLACK ? BLACK_PAWS : WHITE_PAWS).get();
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
