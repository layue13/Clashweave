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
    private void body(net.minecraft.entity.player.EntityPlayer player, ClientProxy.Visual visual, float limb,
        float amount, float age, float headYaw, float pitch) {
        long now = System.nanoTime();
        long period = ClientProxy.instance.tickPeriod();
        com.layue13.clashweave.client.body.BodyRig rig = visual != null ? visual.rig
            : BodyRigs.of(player.getEntityId(), now);
        if (rig.beginFrame(now)) rig.observeGround(player.onGround, now, period);
        com.layue13.clashweave.client.body.LocomotionInputs in = biped.inputs;
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
        // Swimming, riding, climbing, sleeping and flying are not animated yet: vanilla's pose stands in for them.
        biped.locomotion = !(player.isInWater() || player.isRiding()
            || player.isPlayerSleeping()
            || player.isOnLadder()
            || player.capabilities.isFlying);
        biped.rig = armorChest.rig = armorLegs.rig = rig;
        biped.frameNanos = armorChest.frameNanos = armorLegs.frameNanos = now;
        biped.tickPeriod = armorChest.tickPeriod = armorLegs.tickPeriod = period;
        armorChest.locomotion = armorLegs.locomotion = biped.locomotion;
        armorChest.inputs.limbSwing = armorLegs.inputs.limbSwing = limb;
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
            body(worn, visualOf(player), limb, amount, age, headYaw, pitch);
        } else {
            biped.weight = armorChest.weight = armorLegs.weight = 0;
            biped.rig = armorChest.rig = armorLegs.rig = null;
        }
        super.renderModel(player, limb, amount, age, headYaw, pitch, scale);
    }
}
