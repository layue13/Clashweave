package com.layue13.clashweave.client;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.EntityLivingBase;

/** Keep the rendered body and rigid weapon on the same committed yaw as gameplay. */
public final class KatanaPlayerRenderer extends RenderPlayer {

    private final CombatBiped biped = new CombatBiped(0.0F);
    /** The two armor models RenderPlayer draws vanilla armor with: the plain ModelBiped's, but posed like the skin. */
    private final CombatBiped armorChest = new CombatBiped(1.0F);
    private final CombatBiped armorLegs = new CombatBiped(0.5F);
    /** The observer's own arms, posed like the third-person body but drawn from the eye. */
    final CombatBiped hands = new CombatBiped(0.0F);
    private float observedBody;
    private float renderedBody;

    private ClientProxy.Visual committed(EntityLivingBase player) {
        ClientProxy.Visual visual = ClientProxy.instance.visualOf(player.getEntityId());
        return visual != null && visual.state.instance != 0 ? visual : null;
    }

    public KatanaPlayerRenderer() {
        mainModel = biped;
        modelBipedMain = biped;
        modelArmorChestplate = armorChest;
        modelArmor = armorLegs;
    }

    /**
     * Once per entity and frame: the body rig, and what the locomotion layer reads. The three models (skin and the two
     * armor layers) share the result, so the chase is stepped by whichever of them is posed first.
     */
    private void body(CombatBiped skin, net.minecraft.entity.player.EntityPlayer player, ClientProxy.Visual visual,
        float limb, float amount, float age, float headYaw, float pitch) {
        long now = System.nanoTime();
        long period = ClientProxy.instance.tickPeriod();
        com.layue13.clashweave.client.body.BodyRig rig = visual != null ? visual.rig
            : BodyRigs.of(player.getEntityId(), now);
        if (rig.beginFrame(now)) rig.observeGround(player.onGround, now, period);
        com.layue13.clashweave.client.body.LocomotionInputs in = skin.inputs;
        in.limbSwing = limb;
        in.limbSwingAmount = amount;
        in.age = age;
        in.headYaw = headYaw;
        in.headPitch = pitch;
        in.onGround = player.onGround;
        in.sprinting = player.isSprinting();
        in.sneaking = player.isSneaking();
        in.ticksSinceTouchdown = rig.ticksSinceTouchdown();
        in.ticksAirborne = rig.ticksAirborne();
        in.armsBusy = player.swingProgress > 0 || player.isUsingItem();
        in.usingItem = player.isUsingItem();
        in.leftBusy = player.isUsingItem() && player.getItemInUse() != null
            && player.getItemInUse()
                .getItemUseAction() == net.minecraft.item.EnumAction.bow;
        boolean climbing = player.isOnLadder() && !player.onGround;
        in.carryRight = PoseDriver.carrying(player) && (visual == null || !visual.state.sheathed)
            && com.layue13.clashweave.forge.CombatWeapons.armed(player.getHeldItem());
        in.climbPhase = rig.observeClimb(player.posY, climbing);
        in.mode = player.isPlayerSleeping() ? com.layue13.clashweave.client.body.LocomotionInputs.SLEEP
            : player.isRiding() ? com.layue13.clashweave.client.body.LocomotionInputs.RIDE
                : climbing ? com.layue13.clashweave.client.body.LocomotionInputs.CLIMB
                    : player.capabilities.isFlying && !player.onGround
                        ? com.layue13.clashweave.client.body.LocomotionInputs.FLY
                        : player.isInWater() ? com.layue13.clashweave.client.body.LocomotionInputs.SWIM
                            : com.layue13.clashweave.client.body.LocomotionInputs.GROUND;
        skin.locomotion = true;
        skin.rig = rig;
        if (skin != biped) {
            skin.frameNanos = now;
            skin.tickPeriod = period;
            return;
        }
        biped.rig = armorChest.rig = armorLegs.rig = rig;
        biped.frameNanos = armorChest.frameNanos = armorLegs.frameNanos = now;
        biped.tickPeriod = armorChest.tickPeriod = armorLegs.tickPeriod = period;
        armorChest.locomotion = armorLegs.locomotion = skin.locomotion;
        armorChest.inputs.limbSwing = armorLegs.inputs.limbSwing = limb;
    }

    /** Largest angle between where the camera looks and where the body faces that the arms follow, degrees. */
    private static final float MAX_BODY_TURN = 45;
    /** How far the arms are raised into the frame of the first-person camera, blocks. */
    private static final float FIRST_PERSON_LIFT = .22f;

    /**
     * Poses {@link #hands} for the observer and leaves the modelview matrix where the model's origin is, ready for
     * {@code hands.bipedRightArm.render}. The caller pushes and pops the matrix. The body stays world-fixed: the camera
     * pitch and its turn away from the body's facing are applied, so the arms sit where the body has them.
     */
    void placeHands(net.minecraft.client.entity.EntityClientPlayerMP player, ClientProxy.Visual visual, float partial) {
        PoseDriver driver = PoseDriver.INSTANCE;
        driver.prepare(player, visual);
        float amount = player.prevLimbSwingAmount + (player.limbSwingAmount - player.prevLimbSwingAmount) * partial;
        float limb = player.limbSwing - player.limbSwingAmount * (1 - partial);
        float age = player.ticksExisted + partial;
        float pitch = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partial;
        float yaw = player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * partial;
        float body = visual != null && visual.state.instance != 0 ? visual.state.yaw
            : player.prevRenderYawOffset + (player.renderYawOffset - player.prevRenderYawOffset) * partial;
        float turn = net.minecraft.util.MathHelper.wrapAngleTo180_float(yaw - body);
        turn = Math.max(-MAX_BODY_TURN, Math.min(MAX_BODY_TURN, turn));
        hands.pose = driver.pose;
        hands.weight = driver.weight;
        hands.stanceOnly = driver.stance;
        hands.rigidArms = false;
        hands.rigidLegs = false;
        hands.onGround = player.getSwingProgress(partial);
        hands.isSneak = player.isSneaking();
        hands.isRiding = player.isRiding();
        hands.isChild = false;
        hands.heldItemRight = 1;
        hands.aimedBow = false;
        body(hands, player, visual, limb, amount, age, 0, pitch);
        hands.setRotationAngles(limb, amount, age, 0, pitch, 1 / 16f, player);
        org.lwjgl.opengl.GL11.glRotatef(pitch, 1, 0, 0);
        org.lwjgl.opengl.GL11.glRotatef(turn, 0, 1, 0);
        // The eye is a little below where the body puts it so the arms and the blade rise into the frame.
        org.lwjgl.opengl.GL11.glTranslatef(0, -(1.62f - FIRST_PERSON_LIFT), 0);
        org.lwjgl.opengl.GL11.glScalef(.9375f, .9375f, .9375f);
        org.lwjgl.opengl.GL11.glScalef(-1, -1, 1);
        org.lwjgl.opengl.GL11.glTranslatef(0, -1.5078125f, 0);
    }

    private static ClientProxy.Visual visualOf(EntityLivingBase player) {
        return ClientProxy.instance.visualOf(player.getEntityId());
    }

    @Override
    protected void rotateCorpse(AbstractClientPlayer player, float age, float yaw, float partial) {
        observedBody = yaw;
        ClientProxy.Visual visual = committed(player);
        renderedBody = visual == null ? yaw : visual.state.yaw;
        ClientProxy proxy = ClientProxy.instance;
        if (visual == null && player == net.minecraft.client.Minecraft.getMinecraft().thePlayer
            && proxy.lockCamera.thirdStrong()
            && proxy.lockCamera.target() != null) renderedBody = player.rotationYaw;
        super.rotateCorpse(player, age, renderedBody, partial);
    }

    @Override
    protected void renderModel(EntityLivingBase player, float limb, float amount, float age, float headYaw, float pitch,
        float scale) {
        ClientProxy.Visual visual = committed(player);
        if (visual != null || renderedBody != observedBody) {
            // Head observation remains independent of the committed body; entity fields stay untouched.
            headYaw += observedBody - renderedBody;
        }
        PoseDriver driver = PoseDriver.INSTANCE;
        if (player instanceof net.minecraft.entity.player.EntityPlayer) {
            driver.prepare((net.minecraft.entity.player.EntityPlayer) player, visualOf(player));
            net.minecraft.entity.player.EntityPlayer worn = (net.minecraft.entity.player.EntityPlayer) player;
            // A worn piece keeps its limbs one straight block, skin included, so the joint cannot disagree with it.
            biped.rigidArms = worn.getCurrentArmor(2) != null;
            biped.rigidLegs = worn.getCurrentArmor(1) != null || worn.getCurrentArmor(0) != null;
            biped.pose = armorChest.pose = armorLegs.pose = driver.pose;
            biped.weight = armorChest.weight = armorLegs.weight = driver.weight;
            biped.stanceOnly = armorChest.stanceOnly = armorLegs.stanceOnly = driver.stance;
            body(biped, worn, visualOf(player), limb, amount, age, headYaw, pitch);
        } else {
            biped.weight = armorChest.weight = armorLegs.weight = 0;
            biped.rig = armorChest.rig = armorLegs.rig = null;
        }
        super.renderModel(player, limb, amount, age, headYaw, pitch, scale);
    }
}
