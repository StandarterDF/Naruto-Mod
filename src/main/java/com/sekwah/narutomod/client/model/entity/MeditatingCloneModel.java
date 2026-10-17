package com.sekwah.narutomod.client.model.entity;

import com.sekwah.narutomod.entity.ShadowCloneEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * The vanilla player model with one extra pose: sitting down to meditate.
 *
 * <p>Vanilla never sits a player model down, so the legs are folded across and the hands brought
 * together in front of the chest here, once the normal walking animation has been applied. The
 * sleeves and trouser layers of the slim model are copied over afterwards, otherwise they would be
 * left standing while the limbs beneath them fold up.</p>
 */
public class MeditatingCloneModel extends PlayerModel<ShadowCloneEntity> {

    /** Legs folded out flat, which together with the renderer's shift reads as cross legged. */
    private static final float LEG_PITCH = -1.45f;
    private static final float LEG_ROLL = 0.6f;

    /** Arms down and inwards, hands meeting in a seal. */
    private static final float ARM_PITCH = -0.9f;
    private static final float ARM_ROLL = 0.3f;
    private static final float ARM_YAW = 0.4f;

    public MeditatingCloneModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    @Override
    public void setupAnim(ShadowCloneEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (!entity.isMeditating()) {
            return;
        }

        this.rightLeg.xRot = LEG_PITCH;
        this.rightLeg.zRot = -LEG_ROLL;
        this.leftLeg.xRot = LEG_PITCH;
        this.leftLeg.zRot = LEG_ROLL;

        this.rightArm.xRot = ARM_PITCH;
        this.rightArm.zRot = ARM_ROLL;
        this.rightArm.yRot = ARM_YAW;
        this.leftArm.xRot = ARM_PITCH;
        this.leftArm.zRot = -ARM_ROLL;
        this.leftArm.yRot = -ARM_YAW;

        // The slim model draws its sleeves and trousers as separate parts that copy their limb.
        this.leftPants.copyFrom(this.leftLeg);
        this.rightPants.copyFrom(this.rightLeg);
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightSleeve.copyFrom(this.rightArm);
    }
}
