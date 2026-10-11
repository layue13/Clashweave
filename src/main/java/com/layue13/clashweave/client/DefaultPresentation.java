package com.layue13.clashweave.client;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;

import com.layue13.clashweave.core.SemanticEvents;

/** Keeps P0's owner hit/guard sounds, now subscribed to confirmed semantic results. */
final class DefaultPresentation implements Consumer<SemanticEvents.Event> {

    private final com.layue13.clashweave.presentation.FeedbackPolicy policy = new com.layue13.clashweave.presentation.FeedbackPolicy();
    private final Minecraft minecraft;
    private final ClientProxy proxy;

    DefaultPresentation(Minecraft minecraft, ClientProxy proxy) {
        this.minecraft = minecraft;
        this.proxy = proxy;
    }

    @Override
    public void accept(SemanticEvents.Event event) {
        if (minecraft.thePlayer == null) return;
        int own = minecraft.thePlayer.getEntityId();
        if (event.kind == SemanticEvents.Kind.LAUNCH) {
            net.minecraft.entity.Entity entity = minecraft.theWorld.getEntityByID(event.actor);
            if (entity != null) minecraft.theWorld
                .spawnParticle("crit", entity.posX, entity.posY + entity.height / 2, entity.posZ, 0, 0, 0);
        }
        com.layue13.clashweave.presentation.FeedbackPolicy.Sound sound = policy.sound(event, own);
        if (sound != com.layue13.clashweave.presentation.FeedbackPolicy.Sound.NONE) {
            proxy.lastFeedbackFrozen = event.frozen;
            minecraft.thePlayer.playSound(
                sound == com.layue13.clashweave.presentation.FeedbackPolicy.Sound.HIT ? "random.successful_hit"
                    : "random.anvil_land",
                .35f,
                1.4f);
        }
    }
}
