package com.femboycompanion.client;

import com.femboycompanion.entity.FemboyEntity;

import net.minecraft.client.renderer.entity.model.BipedModel;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.util.math.MathHelper;

public class FemboyModel extends BipedModel<FemboyEntity> {
    private final ModelRenderer earLeft;
    private final ModelRenderer earRight;
    private final ModelRenderer cheekLeft;
    private final ModelRenderer cheekRight;
    private final ModelRenderer tail;

    public FemboyModel() {
        super(0.0F);
        this.hat.visible = false;

        this.earLeft = this.makeEar(2.5F);
        this.earRight = this.makeEar(-2.5F);
        this.head.addChild(this.earLeft);
        this.head.addChild(this.earRight);

        ModelRenderer muzzle = new ModelRenderer(this, 40, 0);
        muzzle.addBox(-2.0F, 0.0F, -3.0F, 4.0F, 2.0F, 3.0F);
        muzzle.setPos(0.0F, -3.0F, -4.0F);
        this.head.addChild(muzzle);

        this.cheekLeft = new ModelRenderer(this, 40, 5);
        this.cheekLeft.addBox(0.0F, 0.0F, -1.0F, 1.0F, 2.0F, 2.0F);
        this.cheekLeft.setPos(4.0F, -3.0F, -1.0F);
        this.head.addChild(this.cheekLeft);

        this.cheekRight = new ModelRenderer(this, 40, 5);
        this.cheekRight.addBox(-1.0F, 0.0F, -1.0F, 1.0F, 2.0F, 2.0F);
        this.cheekRight.setPos(-4.0F, -3.0F, -1.0F);
        this.head.addChild(this.cheekRight);

        this.tail = new ModelRenderer(this, 48, 5);
        this.tail.addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 4.0F);
        this.tail.setPos(0.0F, 10.0F, 2.0F);
        ModelRenderer tip = new ModelRenderer(this, 32, 9);
        tip.addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 3.0F);
        tip.setPos(0.0F, 0.0F, 4.0F);
        this.tail.addChild(tip);
        this.body.addChild(this.tail);
    }

    public void setDetailed(boolean detailed) {
        this.earLeft.visible = detailed;
        this.earRight.visible = detailed;
        this.cheekLeft.visible = detailed;
        this.cheekRight.visible = detailed;
        this.tail.visible = detailed;
    }

    private ModelRenderer makeEar(float x) {
        ModelRenderer ear = new ModelRenderer(this, 32, 0);
        ear.addBox(-1.5F, -3.0F, -0.5F, 3.0F, 3.0F, 1.0F);
        ear.setPos(x, -8.0F, 0.0F);
        ModelRenderer upper = new ModelRenderer(this, 32, 4);
        upper.addBox(-1.0F, -3.0F, -0.5F, 2.0F, 3.0F, 1.0F);
        upper.setPos(0.0F, -3.0F, 0.0F);
        ear.addChild(upper);
        return ear;
    }

    @Override
    public void setupAnim(FemboyEntity entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        float wiggle = MathHelper.sin(ageInTicks * 0.09F) * 0.06F;
        this.earLeft.zRot = 0.15F + wiggle;
        this.earRight.zRot = -0.15F - wiggle;
        this.tail.xRot = 0.55F;
        this.tail.yRot = MathHelper.sin(ageInTicks * 0.2F) * (0.25F + limbSwingAmount * 0.3F);
    }
}
