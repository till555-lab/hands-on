package com.handson.server;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;

public class ModCommands {
    /** /controls minecraft = vanilla controls, /controls handson = Hands On controls (cheats required). */
    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal("controls")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("minecraft").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrException();
                    ServerEvents.setMode(player, false);
                    return 1;
                }))
                .then(Commands.literal("handson").executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrException();
                    ServerEvents.setMode(player, true);
                    return 1;
                })));
    }
}
