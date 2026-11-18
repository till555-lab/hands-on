package com.femboycompanion;

import com.femboycompanion.advancement.FemboyTrigger;
import com.femboycompanion.entity.FemboyEntity;
import com.femboycompanion.registry.ModEntities;
import com.femboycompanion.registry.ModItems;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.goal.NearestAttackableTargetGoal;
import net.minecraft.entity.monster.AbstractSkeletonEntity;
import net.minecraft.entity.monster.ZombieEntity;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntitySpawnPlacementRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.MobSpawnInfo;
import net.minecraft.world.gen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.world.BiomeLoadingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(FemboyCompanion.MOD_ID)
public class FemboyCompanion {
    public static final String MOD_ID = "femboycompanion";
    public static final Logger LOGGER = LogManager.getLogger();
    public static final FemboyTrigger TRIGGER = new FemboyTrigger();

    public FemboyCompanion() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.register(bus);
        ModItems.register(bus);
        bus.addListener(this::onAttributes);
        bus.addListener(this::onCommonSetup);
        MinecraftForge.EVENT_BUS.addListener(this::onBiomeLoading);
        MinecraftForge.EVENT_BUS.addListener(this::onLivingDeath);
        MinecraftForge.EVENT_BUS.addListener(this::onEntityJoin);
        LOGGER.info("Femboy Companion loaded");
    }

    private void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.FEMBOY.get(), FemboyEntity.createAttributes().build());
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CriteriaTriggers.register(TRIGGER);
            EntitySpawnPlacementRegistry.register(ModEntities.FEMBOY.get(),
                    EntitySpawnPlacementRegistry.PlacementType.ON_GROUND,
                    Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                    FemboyEntity::checkSpawnRules);
        });
    }

    private void onBiomeLoading(BiomeLoadingEvent event) {
        Biome.Category c = event.getCategory();
        if (c == Biome.Category.MUSHROOM || c == Biome.Category.OCEAN || c == Biome.Category.RIVER
                || c == Biome.Category.NETHER || c == Biome.Category.THEEND || c == Biome.Category.NONE) {
            return;
        }
        event.getSpawns().getSpawner(EntityClassification.CREATURE)
                .add(new MobSpawnInfo.Spawners(ModEntities.FEMBOY.get(), 8, 2, 4));
        event.getSpawns().getSpawner(EntityClassification.CREATURE)
                .add(new MobSpawnInfo.Spawners(ModEntities.FEMBOY.get(), 3, 5, 6));
        event.getSpawns().getSpawner(EntityClassification.CREATURE)
                .add(new MobSpawnInfo.Spawners(ModEntities.FEMBOY.get(), 1, 7, 9));
    }

    private void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof FemboyEntity)) {
            return;
        }
        Entity killer = event.getSource().getEntity();
        if (!(killer instanceof PlayerEntity)) {
            return;
        }
        FemboyEntity dead = (FemboyEntity) event.getEntity();
        if (dead.level.isClientSide) {
            return;
        }
        if (killer instanceof ServerPlayerEntity) {
            TRIGGER.fire((ServerPlayerEntity) killer, "kill");
        }
        AxisAlignedBB area = dead.getBoundingBox().inflate(16.0D);
        for (FemboyEntity witness : dead.level.getEntitiesOfClass(FemboyEntity.class, area)) {
            if (witness != dead) {
                witness.distrust(killer.getUUID(), dead.level.getGameTime() + 12000L);
            }
        }
    }

    private void onEntityJoin(EntityJoinWorldEvent event) {
        if (event.getWorld().isClientSide) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof ZombieEntity || entity instanceof AbstractSkeletonEntity) {
            MobEntity mob = (MobEntity) entity;
            mob.targetSelector.addGoal(4, new NearestAttackableTargetGoal<FemboyEntity>(mob, FemboyEntity.class, true));
        }
    }
}
