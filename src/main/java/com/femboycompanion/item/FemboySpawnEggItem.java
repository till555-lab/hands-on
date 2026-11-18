package com.femboycompanion.item;

import com.femboycompanion.entity.FemboyEntity;
import com.femboycompanion.registry.ModEntities;

import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUseContext;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

public class FemboySpawnEggItem extends Item {
    private final int variant;

    public FemboySpawnEggItem(int variant, Item.Properties properties) {
        super(properties);
        this.variant = variant;
    }

    @Override
    public ActionResultType useOn(ItemUseContext context) {
        World world = context.getLevel();
        if (world.isClientSide) {
            return ActionResultType.SUCCESS;
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        FemboyEntity entity = ModEntities.FEMBOY.get().create(world);
        if (entity == null) {
            return ActionResultType.FAIL;
        }
        entity.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, world.random.nextFloat() * 360.0F, 0.0F);
        entity.finalizeSpawn((ServerWorld) world, world.getCurrentDifficultyAt(pos), SpawnReason.SPAWN_EGG, null, null);
        entity.setVariant(this.variant);
        world.addFreshEntity(entity);
        PlayerEntity player = context.getPlayer();
        if (player == null || !player.abilities.instabuild) {
            context.getItemInHand().shrink(1);
        }
        return ActionResultType.SUCCESS;
    }
}
