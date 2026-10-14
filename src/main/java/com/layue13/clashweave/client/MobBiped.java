package com.layue13.clashweave.client;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

import com.layue13.clashweave.client.body.BodyRig;
import com.layue13.clashweave.client.body.Gait;
import com.layue13.clashweave.client.body.Locomotion;
import com.layue13.clashweave.client.body.LocomotionInputs;

/**
 * Stands in for a monster's own ModelBiped (zombie, skeleton, pigman): the donor's parts are drawn, cut at the elbows
 * and knees, and the donor still poses them first, so everything that monster does by itself (raised zombie arms, a
 * drawn bow, a swing) stays. On top of that the body rig gives legs and torso the same chased, coordinated gait as a
 * player's, and the arms of a model whose arms vanilla owns (the zombie's) are left as vanilla set them.
 */
final class MobBiped extends ModelBiped {

    final ModelBiped donor;
    private final float[] home;
    private final ModelRenderer rightForearm, leftForearm, rightShin, leftShin;
    private final ModelRenderer rightElbow, leftElbow, rightKnee, leftKnee;
    private final LocomotionInputs inputs = new LocomotionInputs();
    private final boolean armsBelongToVanilla;
    private final Gait gait;
    private net.minecraft.client.model.ModelEnderman ender;
    private boolean released;
    private static final float STRAIGHT = .06f;
    /** Set per frame from the worn armor: that pair of limbs stays one straight block so it and the armor agree. */
    boolean rigidArms, rigidLegs;

    /** Returns null when the donor's limbs cannot be cut (not single boxes tall enough). */
    static MobBiped of(ModelBiped donor, boolean armsBelongToVanilla, Gait gait) {
        return of(
            donor,
            armsBelongToVanilla,
            gait,
            BipedChannels.homeOf(donor),
            new float[] { .583f, .583f, .5f, .5f });
    }

    /**
     * An enderman: the model poses its own pivots when it runs (legs 3 px higher than built), so the rest pivots are
     * what one run leaves; the joints sit half way down 30 px limbs, and carrying or screaming raises the arms.
     */
    static MobBiped ofEnderman(net.minecraft.client.model.ModelEnderman donor) {
        donor.isCarrying = false;
        donor.isAttacking = false;
        donor.setRotationAngles(0, 0, 0, 0, 0, 1 / 16f, null);
        MobBiped made = of(
            donor,
            false,
            Gait.ENDERMAN,
            BipedChannels.homeOf(donor),
            new float[] { .5f, .5f, .5f, .5f });
        if (made != null) made.ender = donor;
        return made;
    }

    private static MobBiped of(ModelBiped donor, boolean armsBelongToVanilla, Gait gait, float[] home,
        float[] fractions) {
        ModelRenderer[] cut = new ModelRenderer[8];
        ModelRenderer[] parts = { donor.bipedRightArm, donor.bipedLeftArm, donor.bipedRightLeg, donor.bipedLeftLeg };
        for (int i = 0; i < 4; i++) {
            LimbSlicer.Joint joint = LimbSlicer.slice(donor, parts[i], fractions[i]);
            if (joint == null) return null;
            cut[i] = joint.lower;
            cut[i + 4] = joint.prism;
        }
        return new MobBiped(donor, home, cut, armsBelongToVanilla, gait);
    }

    private MobBiped(ModelBiped donor, float[] home, ModelRenderer[] cut, boolean armsBelongToVanilla, Gait gait) {
        super(0);
        this.donor = donor;
        this.home = home;
        this.armsBelongToVanilla = armsBelongToVanilla;
        this.gait = gait;
        textureWidth = donor.textureWidth;
        textureHeight = donor.textureHeight;
        bipedHead = donor.bipedHead;
        bipedHeadwear = donor.bipedHeadwear;
        bipedBody = donor.bipedBody;
        bipedRightArm = donor.bipedRightArm;
        bipedLeftArm = donor.bipedLeftArm;
        bipedRightLeg = donor.bipedRightLeg;
        bipedLeftLeg = donor.bipedLeftLeg;
        bipedEars = donor.bipedEars;
        bipedCloak = donor.bipedCloak;
        rightForearm = cut[0];
        leftForearm = cut[1];
        rightShin = cut[2];
        leftShin = cut[3];
        rightElbow = cut[4];
        leftElbow = cut[5];
        rightKnee = cut[6];
        leftKnee = cut[7];
    }

    /**
     * Hands the donor its parts back, as built, for a render that must not change (a worn armor model follows them).
     */
    void release() {
        if (released) return;
        released = true;
        BipedChannels.restoreHome(donor, home);
        rightForearm.rotateAngleX = leftForearm.rotateAngleX = rightShin.rotateAngleX = leftShin.rotateAngleX = 0;
        rightElbow.rotateAngleX = leftElbow.rotateAngleX = rightKnee.rotateAngleX = leftKnee.rotateAngleX = 0;
        donor.bipedHead.rotateAngleZ = 0;
        donor.bipedBody.rotateAngleZ = 0;
    }

    void engage() {
        released = false;
    }

    private void toDonor() {
        donor.onGround = onGround;
        donor.isRiding = isRiding;
        donor.isChild = isChild;
        donor.heldItemLeft = heldItemLeft;
        donor.heldItemRight = heldItemRight;
        donor.isSneak = isSneak;
        donor.aimedBow = aimedBow;
    }

    @Override
    public void setLivingAnimations(EntityLivingBase entity, float limb, float amount, float partial) {
        toDonor();
        donor.setLivingAnimations(entity, limb, amount, partial);
        heldItemLeft = donor.heldItemLeft;
        heldItemRight = donor.heldItemRight;
        aimedBow = donor.aimedBow;
        isSneak = donor.isSneak;
    }

    @Override
    public void setRotationAngles(float limb, float amount, float age, float headYaw, float pitch, float scale,
        Entity entity) {
        BipedChannels.restoreHome(donor, home);
        toDonor();
        donor.setRotationAngles(limb, amount, age, headYaw, pitch, scale, entity);
        rightForearm.rotateAngleX = leftForearm.rotateAngleX = rightShin.rotateAngleX = leftShin.rotateAngleX = 0;
        long now = System.nanoTime();
        long period = ClientProxy.instance == null ? 50_000_000L : ClientProxy.instance.tickPeriod();
        BodyRig rig = BodyRigs.of(entity.getEntityId(), now);
        if (rig.beginFrame(now)) rig.observeGround(entity.onGround, now, period);
        if (rig.needsStep()) {
            LocomotionInputs in = inputs;
            in.limbSwing = limb;
            in.limbSwingAmount = amount;
            in.age = age;
            in.headYaw = headYaw;
            in.headPitch = pitch;
            in.onGround = entity.onGround;
            in.sprinting = false;
            in.sneaking = false;
            in.ticksSinceTouchdown = rig.ticksSinceTouchdown();
            in.ticksAirborne = rig.ticksAirborne();
            in.armsBusy = in.leftBusy = armsBelongToVanilla || ender != null && (ender.isCarrying || ender.isAttacking);
            // A held item rides the upper arm alone, so an elbow bend would leave it behind: no elbow when holding one.
            in.usingItem = entity instanceof EntityLivingBase && ((EntityLivingBase) entity).getHeldItem() != null;
            in.gait = gait;
            in.mode = entity.isRiding() ? LocomotionInputs.RIDE
                : entity.isInWater() ? LocomotionInputs.SWIM : LocomotionInputs.GROUND;
            BipedChannels.readVanilla(this, rig.target());
            Locomotion.compute(in, rig.target());
            rig.chase(now, period);
        }
        BipedChannels.writeBase(
            this,
            rightForearm,
            leftForearm,
            rightShin,
            leftShin,
            rigidArms,
            rigidLegs,
            home,
            rig.smoothed());
        rightElbow.rotateAngleX = rightForearm.rotateAngleX * .5f;
        leftElbow.rotateAngleX = leftForearm.rotateAngleX * .5f;
        rightKnee.rotateAngleX = rightShin.rotateAngleX * .5f;
        leftKnee.rotateAngleX = leftShin.rotateAngleX * .5f;
        // The wedge a bend opens is a fraction of a pixel until the joint is well bent: straight, there is nothing to
        // fill.
        rightElbow.showModel = !rigidArms && Math.abs(rightForearm.rotateAngleX) > STRAIGHT;
        leftElbow.showModel = !rigidArms && Math.abs(leftForearm.rotateAngleX) > STRAIGHT;
        rightKnee.showModel = !rigidLegs && Math.abs(rightShin.rotateAngleX) > STRAIGHT;
        leftKnee.showModel = !rigidLegs && Math.abs(leftShin.rotateAngleX) > STRAIGHT;
    }
}
