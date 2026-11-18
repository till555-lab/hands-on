package com.femboycompanion.command;

import com.femboycompanion.FemboyCompanion;
import com.femboycompanion.entity.FemboyEntity;
import com.femboycompanion.registry.ModEntities;

import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.SpawnReason;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FemboyCompanion.MOD_ID)
public class ModCommands {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("femboy")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("spawn")
                                .then(Commands.literal("white")
                                        .executes(c -> spawn(c.getSource(), FemboyEntity.VARIANT_WHITE)))
                                .then(Commands.literal("black")
                                        .executes(c -> spawn(c.getSource(), FemboyEntity.VARIANT_BLACK)))));
    }

    private static int spawn(CommandSource source, int variant) {
        ServerWorld world = source.getLevel();
        FemboyEntity entity = ModEntities.FEMBOY.get().create(world);
        if (entity == null) {
            return 0;
        }
        Vector3d pos = source.getPosition();
        entity.moveTo(pos.x, pos.y, pos.z, world.random.nextFloat() * 360.0F, 0.0F);
        entity.finalizeSpawn(world, world.getCurrentDifficultyAt(entity.blockPosition()),
                SpawnReason.COMMAND, null, null);
        entity.setVariant(variant);
        world.addFreshEntity(entity);
        source.sendSuccess(new StringTextComponent("Femboy spawned"), true);
        return 1;
    }
}
