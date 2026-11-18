package com.femboycompanion.registry;

import com.femboycompanion.FemboyCompanion;
import com.femboycompanion.entity.FemboyEntity;

import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITIES, FemboyCompanion.MOD_ID);

    public static final RegistryObject<EntityType<FemboyEntity>> FEMBOY = ENTITIES.register("femboy",
            () -> EntityType.Builder.of(FemboyEntity::new, EntityClassification.CREATURE)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(4)
                    .build(new ResourceLocation(FemboyCompanion.MOD_ID, "femboy").toString()));

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }
}
