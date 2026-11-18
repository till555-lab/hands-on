package com.femboycompanion.advancement;

import com.google.gson.JsonObject;

import net.minecraft.advancements.criterion.AbstractCriterionTrigger;
import net.minecraft.advancements.criterion.CriterionInstance;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.loot.ConditionArrayParser;
import net.minecraft.util.JSONUtils;
import net.minecraft.util.ResourceLocation;

public class FemboyTrigger extends AbstractCriterionTrigger<FemboyTrigger.Instance> {
    public static final ResourceLocation ID = new ResourceLocation("femboycompanion", "event");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    protected Instance createInstance(JsonObject json, EntityPredicate.AndPredicate player,
            ConditionArrayParser parser) {
        return new Instance(player, JSONUtils.getAsString(json, "event"));
    }

    public void fire(ServerPlayerEntity player, String event) {
        this.trigger(player, instance -> instance.event.equals(event));
    }

    public static class Instance extends CriterionInstance {
        private final String event;

        public Instance(EntityPredicate.AndPredicate player, String event) {
            super(ID, player);
            this.event = event;
        }
    }
}
