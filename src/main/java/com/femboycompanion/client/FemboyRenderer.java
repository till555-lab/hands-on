package com.femboycompanion.client;

import com.femboycompanion.FemboyCompanion;
import com.femboycompanion.entity.FemboyEntity;
import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.BipedRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.util.ResourceLocation;

public class FemboyRenderer extends BipedRenderer<FemboyEntity, FemboyModel> {
    private static final ResourceLocation[][] TEXTURES =
            new ResourceLocation[2][FemboyEntity.COAT_COUNT];

    static {
        String[] families = {"white", "black"};
        for (int f = 0; f < 2; f++) {
            for (int c = 0; c < FemboyEntity.COAT_COUNT; c++) {
                TEXTURES[f][c] = new ResourceLocation(FemboyCompanion.MOD_ID,
                        "textures/entity/femboy_" + families[f] + "_" + c + ".png");
            }
        }
    }

    public FemboyRenderer(EntityRendererManager manager) {
        super(manager, new FemboyModel(), 0.5F);
    }

    @Override
    public void render(FemboyEntity entity, float entityYaw, float partialTicks, MatrixStack matrixStack,
            IRenderTypeBuffer buffer, int packedLight) {
        this.getModel().setDetailed(this.entityRenderDispatcher.distanceToSqr(entity) < 576.0D);
        super.render(entity, entityYaw, partialTicks, matrixStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(FemboyEntity entity) {
        int family = entity.getVariant() == FemboyEntity.VARIANT_BLACK ? 1 : 0;
        int coat = Math.floorMod(entity.getCoat(), FemboyEntity.COAT_COUNT);
        return TEXTURES[family][coat];
    }
}
