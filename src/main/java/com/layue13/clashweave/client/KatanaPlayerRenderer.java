package com.layue13.clashweave.client;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.EntityLivingBase;

/** Keep the rendered body and rigid weapon on the same committed yaw as gameplay. */
public final class KatanaPlayerRenderer extends RenderPlayer {

    private float observedBody;

    private ClientProxy.Visual committed(EntityLivingBase player) {
        ClientProxy.Visual visual = ClientProxy.instance.visuals.get(player.getEntityId());
        return visual != null && visual.state.instance != 0 ? visual : null;
    }

    @Override
    protected void rotateCorpse(AbstractClientPlayer player, float age, float yaw, float partial) {
        observedBody = yaw;
        ClientProxy.Visual visual = committed(player);
        super.rotateCorpse(player, age, visual == null ? yaw : visual.state.yaw, partial);
    }

    @Override
    protected void renderModel(EntityLivingBase player, float limb, float amount, float age, float headYaw, float pitch,
        float scale) {
        ClientProxy.Visual visual = committed(player);
        if (visual != null) {
            // Head observation remains independent of the committed body; entity fields stay untouched.
            headYaw += observedBody - visual.state.yaw;
        }
        super.renderModel(player, limb, amount, age, headYaw, pitch, scale);
    }
}
