package com.layue13.clashweave.client;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;

import com.layue13.clashweave.client.pose.LimbAngles;
import com.layue13.clashweave.client.pose.LimbSolver;
import com.layue13.clashweave.client.pose.Pose;

/**
 * The player model with the baked combat pose laid over vanilla's own angles. Vanilla still runs first, so head look
 * and
 * everything the pose leaves alone stay as they were; {@link #weight} fades the pose in and out. The parts of
 * ModelBiped are independent (arms do not follow the torso), so every pivot is set from the pose here.
 *
 * <p>
 * The skin model (expand 0) has arms and legs in two segments: an upper box and a forearm/shin child hinged at the
 * elbow/knee, the 64x32 skin cut across its rows so the texture still maps. A limb whose armor slot is occupied stays
 * one
 * rigid block ({@link #rigidArms}, {@link #rigidLegs}) so skin and armor never disagree about where the joint is. Armor
 * models (expand above 0) are always rigid; they follow the pose because this class is their model.
 */
final class CombatBiped extends ModelBiped {

    /** Pixels from the shoulder pivot to the elbow, and from the hip pivot to the knee. */
    private static final int ELBOW = 5, KNEE = 6;

    /** Pose and weight for the next render; written by the renderer immediately before it draws this model. */
    Pose pose;
    float weight;
    /** Set by the renderer from the worn armor: that limb pair is drawn as one straight block. */
    boolean rigidArms, rigidLegs;

    private final boolean segmented;
    private ModelRenderer rightForearm, leftForearm, rightShin, leftShin;
    private final LimbAngles solved = new LimbAngles();
    private final float[] angles = new float[3];
    private final float[] point = new float[3];

    CombatBiped(float expand) {
        super(expand);
        segmented = expand == 0;
        if (segmented) {
            // ModelBiped(float) uses the 64x32 skin layout: right arm (40,16), left arm mirrored, legs (0,16).
            bipedRightArm = arm(false);
            rightForearm = (ModelRenderer) bipedRightArm.childModels.get(0);
            bipedLeftArm = arm(true);
            leftForearm = (ModelRenderer) bipedLeftArm.childModels.get(0);
            bipedRightLeg = leg(false);
            rightShin = (ModelRenderer) bipedRightLeg.childModels.get(0);
            bipedLeftLeg = leg(true);
            leftShin = (ModelRenderer) bipedLeftLeg.childModels.get(0);
        }
    }

    /** Upper arm from -2 to ELBOW px below the pivot (including the shoulder cap), forearm below it. */
    private ModelRenderer arm(boolean left) {
        ModelRenderer upper = new ModelRenderer(this, 40, 16);
        upper.mirror = left;
        upper.addBox(left ? -1 : -3, -2, -2, 4, ELBOW + 2, 4, 0);
        upper.setRotationPoint(left ? 5 : -5, 2, 0);
        // The forearm's side faces start where the upper's end: row 16 + 4 + (ELBOW + 2) of the skin.
        ModelRenderer lower = new ModelRenderer(this, 40, 16 + ELBOW + 2);
        lower.mirror = left;
        lower.addBox(left ? -1 : -3, 0, -2, 4, 10 - ELBOW, 4, 0);
        lower.setRotationPoint(0, ELBOW, 0);
        upper.addChild(lower);
        return upper;
    }

    private ModelRenderer leg(boolean left) {
        ModelRenderer upper = new ModelRenderer(this, 0, 16);
        upper.mirror = left;
        upper.addBox(-2, 0, -2, 4, KNEE, 4, 0);
        upper.setRotationPoint(left ? 1.9f : -1.9f, 12, 0);
        ModelRenderer lower = new ModelRenderer(this, 0, 16 + KNEE);
        lower.mirror = left;
        lower.addBox(-2, 0, -2, 4, 12 - KNEE, 4, 0);
        lower.setRotationPoint(0, KNEE, 0);
        upper.addChild(lower);
        return upper;
    }

    @Override
    public void setRotationAngles(float limb, float amount, float age, float headYaw, float pitch, float scale,
        Entity entity) {
        restore();
        super.setRotationAngles(limb, amount, age, headYaw, pitch, scale, entity);
        if (segmented) {
            rightForearm.rotateAngleX = 0;
            leftForearm.rotateAngleX = 0;
            rightShin.rotateAngleX = 0;
            leftShin.rotateAngleX = 0;
        }
        if (weight <= 0 || pose == null) return;
        float w = weight;
        // The parts are independent: every pivot follows the pelvis, the shoulders also follow the torso twist.
        LimbSolver.shoulderPoint(true, pose.torsoYaw, pose.pelvis, point);
        pivot(bipedRightArm, point, w);
        arm(bipedRightArm, rightForearm, true, pose.armR, w);
        LimbSolver.shoulderPoint(false, pose.torsoYaw, pose.pelvis, point);
        pivot(bipedLeftArm, point, w);
        arm(bipedLeftArm, leftForearm, false, pose.armL, w);
        LimbSolver.hipPoint(true, pose.pelvis, point);
        pivot(bipedRightLeg, point, w);
        leg(bipedRightLeg, rightShin, true, pose.legR, w);
        LimbSolver.hipPoint(false, pose.pelvis, point);
        pivot(bipedLeftLeg, point, w);
        leg(bipedLeftLeg, leftShin, false, pose.legL, w);
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

    private void arm(ModelRenderer upper, ModelRenderer forearm, boolean right, float[] rigidDirection, float w) {
        if (segmented && !rigidArms) {
            LimbSolver.solveArm(right, pose, solved);
            apply(upper, solved.upper, w);
            forearm.rotateAngleX = solved.childAngleX * w;
        } else {
            LimbSolver.solveLimb(rigidDirection[0], rigidDirection[1], rigidDirection[2], angles);
            apply(upper, angles, w);
        }
    }

    private void leg(ModelRenderer upper, ModelRenderer shin, boolean right, float[] rigidDirection, float w) {
        if (segmented && !rigidLegs) {
            LimbSolver.solveLeg(right, pose, solved);
            apply(upper, solved.upper, w);
            shin.rotateAngleX = solved.childAngleX * w;
        } else {
            LimbSolver.solveLimb(rigidDirection[0], rigidDirection[1], rigidDirection[2], angles);
            apply(upper, angles, w);
        }
    }

    private static void apply(ModelRenderer part, float[] euler, float w) {
        part.rotateAngleX = mix(part.rotateAngleX, euler[0], w);
        part.rotateAngleY = mix(part.rotateAngleY, euler[1], w);
        part.rotateAngleZ = mix(part.rotateAngleZ, euler[2], w);
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
