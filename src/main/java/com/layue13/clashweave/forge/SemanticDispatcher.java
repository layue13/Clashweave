package com.layue13.clashweave.forge;

import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import com.layue13.clashweave.Clashweave;
import com.layue13.clashweave.core.SemanticEvents;
import com.layue13.clashweave.network.SemanticMessage;

import cpw.mods.fml.common.network.NetworkRegistry;

/** Optional presentation delivery extracted from settlement. No damage/input mutation. */
final class SemanticDispatcher {

    private long sequence;

    void publish(SemanticEvents.Event event) {
        Entity actor = null;
        for (WorldServer world : MinecraftServer.getServer().worldServers) if (world != null) {
            actor = world.getEntityByID(event.actor);
            if (actor != null) break;
        }
        if (actor == null) return;
        Clashweave.network.sendToAllAround(
            new SemanticMessage(++sequence, event),
            new NetworkRegistry.TargetPoint(
                actor.dimension,
                actor.posX,
                actor.posY,
                actor.posZ,
                Clashweave.config.presentationRange));
        if (Boolean.getBoolean("clashweave.trace")) System.out.println(
            "CW_EVENT id=" + sequence
                + " kind="
                + event.kind
                + " actor="
                + event.actor
                + " target="
                + event.target
                + " instance="
                + event.instance
                + " tick="
                + event.tick);
    }
}
