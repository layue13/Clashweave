package com.layue13.clashweave.client;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;

import com.layue13.clashweave.client.pose.LimbSolver;
import com.layue13.clashweave.client.pose.Pose;

/**
 * The player model with the baked combat pose laid over vanilla's own angles. Vanilla still runs first, so head look
 * and
 * everything the pose leaves alone stay as they were; {@link #weight} fades the pose in and out. The parts of
 * ModelBiped are independent (arms do not follow the torso), so every pivot is set from the pose here.
 */
final class CombatBiped extends ModelBiped {

    /** Pose and weight for the next render; written by the renderer immediately before it draws this model. */
    Pose pose;
    float weight;

    private final float[] angles = new float[3];
    private final float[] point = new float[3];

    CombatBiped(float expand) {
        super(expand);
    }

    @Override
    public void setRotationAngles(float limb, float amount, float age, float headYaw, float pitch, float scale,
        Entity entity) {
        restore();
        super.setRotationAngles(limb, amount, age, headYaw, pitch, scale, entity);
        if (weight <= 0 || pose == null) return;
        float w = weight;
        // The parts are independent: every pivot follows the pelvis, the shoulders also follow the torso twist.
        LimbSolver.shoulderPoint(true, pose.torsoYaw, pose.pelvis, point);
        pivot(bipedRightArm, point, w);
        limb(bipedRightArm, pose.armR, w);
        LimbSolver.shoulderPoint(false, pose.torsoYaw, pose.pelvis, point);
        pivot(bipedLeftArm, point, w);
        limb(bipedLeftArm, pose.armL, w);
        LimbSolver.hipPoint(true, pose.pelvis, point);
        pivot(bipedRightLeg, point, w);
        limb(bipedRightLeg, pose.legR, w);
        LimbSolver.hipPoint(false, pose.pelvis, point);
        pivot(bipedLeftLeg, point, w);
        limb(bipedLeftLeg, pose.legL, w);
        point[0] = -pose.pelvis[0] * 16 / .9375f;
        point[1] = -pose.pelvis[2] * 16 / .9375f;
        point[2] = -pose.pelvis[1] * 16 / .9375f;
        pivot(bipedBody, point, w);
        bipedBody.rotateAngleX = mix(bipedBody.rotateAngleX, 0, w);
        bipedBody.rotateAngleZ = mix(bipedBody.rotateAngleZ, 0, w);
        bipedBody.rotateAngleY = mix(bipedBody.rotateAngleY, LimbSolver.torsoAngleY(pose.torsoYaw), w);
        pivot(bipedHead, point, w);
        pivot(bipedHeadwear, point, w);
    }

    /**
     * One model draws every player. Vanilla's setRotationAngles never resets the head, headwear and body pivots, the
     * arm
     * pivot Y, the leg pivot X or the leg Z angle (only the constructor sets them), and the pose moves exactly those,
     * so
     * without this the next player drawn inherits the previous one's pelvis and twist: torso and limbs come apart.
     */
    private void restore() {
        bipedHead.setRotationPoint(0, 0, 0);
        bipedHeadwear.setRotationPoint(0, 0, 0);
        bipedBody.setRotationPoint(0, 0, 0);
        bipedBody.rotateAngleZ = 0;
        bipedRightArm.setRotationPoint(-5, 2, 0);
        bipedLeftArm.setRotationPoint(5, 2, 0);
        bipedRightLeg.setRotationPoint(-1.9f, 12, 0);
        bipedLeftLeg.setRotationPoint(1.9f, 12, 0);
        bipedRightLeg.rotateAngleZ = 0;
        bipedLeftLeg.rotateAngleZ = 0;
    }

    private void limb(ModelRenderer part, float[] direction, float w) {
        LimbSolver.solveLimb(direction[0], direction[1], direction[2], angles);
        part.rotateAngleX = mix(part.rotateAngleX, angles[0], w);
        part.rotateAngleY = mix(part.rotateAngleY, angles[1], w);
        part.rotateAngleZ = mix(part.rotateAngleZ, angles[2], w);
    }

    private static void pivot(ModelRenderer part, float[] target, float w) {
        part.rotationPointX = part.rotationPointX + (target[0] - part.rotationPointX) * w;
        part.rotationPointY = part.rotationPointY + (target[1] - part.rotationPointY) * w;
        part.rotationPointZ = part.rotationPointZ + (target[2] - part.rotationPointZ) * w;
    }

    /** Along the shorter way around, so a fade never spins a limb through a full turn. */
    private static float mix(float from, float to, float w) {
        float delta = (float) (to - from - Math.PI * 2 * Math.floor((to - from + Math.PI) / (Math.PI * 2)));
        return from + delta * w;
    }
}
