package com.layue13.clashweave.client;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.EntityLivingBase;

/** Keep the rendered body and rigid weapon on the same committed yaw as gameplay. */
public final class KatanaPlayerRenderer extends RenderPlayer {

    private final CombatBiped biped = new CombatBiped(0.0F);
    private float observedBody;
    private float renderedBody;

    private ClientProxy.Visual committed(EntityLivingBase player) {
        ClientProxy.Visual visual = ClientProxy.instance.visualOf(player.getEntityId());
        return visual != null && visual.state.instance != 0 ? visual : null;
    }

    public KatanaPlayerRenderer() {
        mainModel = biped;
        modelBipedMain = biped;
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
            biped.pose = driver.pose;
            biped.weight = driver.weight;
        } else {
            biped.weight = 0;
        }
        super.renderModel(player, limb, amount, age, headYaw, pitch, scale);
    }
}
