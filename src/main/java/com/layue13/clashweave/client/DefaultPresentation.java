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

    /**
     * The blades meet: a burst of sparks where the blow reached the guard, toward the attacker, and a ring of them on a
     * precise guard. Event actor is the attacker, target the guard, however the blow was delivered.
     */
    private void clash(SemanticEvents.Event event) {
        net.minecraft.entity.Entity guard = minecraft.theWorld.getEntityByID(event.target);
        net.minecraft.entity.Entity attacker = minecraft.theWorld.getEntityByID(event.actor);
        if (guard == null) return;
        double dx = attacker == null ? 0 : attacker.posX - guard.posX,
            dz = attacker == null ? 0 : attacker.posZ - guard.posZ;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length > 1e-4) {
            dx /= length;
            dz /= length;
        }
        double x = guard.posX + dx * .6, y = guard.posY + guard.height * .65, z = guard.posZ + dz * .6;
        boolean precise = event.kind == SemanticEvents.Kind.PARRY;
        com.layue13.clashweave.client.vfx.Vfx vfx = com.layue13.clashweave.client.vfx.Vfx.INSTANCE;
        int effect = vfx.bound(event.kind.name());
        if (effect >= 0) {
            vfx.play(effect, x, y, z, (float) dx, .12f, (float) dz);
            minecraft.theWorld.playSound(
                x,
                y,
                z,
                precise ? "random.anvil_land" : "random.anvil_use",
                precise ? .5f : .3f,
                precise ? 1.9f : 1.5f,
                false);
            return;
        }
        int sparks = precise ? 16 : 7;
        java.util.Random random = minecraft.theWorld.rand;
        for (int i = 0; i < sparks; i++) {
            double spread = precise ? .22 : .12;
            minecraft.theWorld.spawnParticle(
                "fireworksSpark",
                x,
                y,
                z,
                dx * .12 + (random.nextDouble() - .5) * spread,
                (random.nextDouble() - .3) * spread,
                dz * .12 + (random.nextDouble() - .5) * spread);
        }
        minecraft.theWorld.playSound(
            x,
            y,
            z,
            precise ? "random.anvil_land" : "random.anvil_use",
            precise ? .5f : .3f,
            precise ? 1.9f : 1.5f,
            false);
    }

    @Override
    public void accept(SemanticEvents.Event event) {
        if (minecraft.thePlayer == null) return;
        int own = minecraft.thePlayer.getEntityId();
        // What the data binds to these events is played first; the vanilla particles below remain as the fallback.
        boolean fx = (event.kind == SemanticEvents.Kind.LAUNCH || event.kind == SemanticEvents.Kind.SLAM
            || event.kind == SemanticEvents.Kind.BREAK
            || event.kind == SemanticEvents.Kind.DELAYED
            || event.kind == SemanticEvents.Kind.FINISH)
            && com.layue13.clashweave.client.vfx.Vfx.INSTANCE
                .playEvent(event.kind.name(), minecraft.theWorld.getEntityByID(event.actor));
        if (event.kind == SemanticEvents.Kind.LAUNCH && !fx) {
            net.minecraft.entity.Entity entity = minecraft.theWorld.getEntityByID(event.actor);
            if (entity != null) minecraft.theWorld
                .spawnParticle("crit", entity.posX, entity.posY + entity.height / 2, entity.posZ, 0, 0, 0);
        }
        if (event.kind == SemanticEvents.Kind.BLOCK || event.kind == SemanticEvents.Kind.PARRY) clash(event);
        if (event.kind == SemanticEvents.Kind.DELAYED) {
            net.minecraft.entity.Entity entity = minecraft.theWorld.getEntityByID(event.actor);
            if (entity != null) {
                for (int i = 0; i < 14 && !fx; i++) {
                    double t = i / 13.0 * 2 - 1;
                    minecraft.theWorld.spawnParticle(
                        "fireworksSpark",
                        entity.posX + t * .7,
                        entity.posY + entity.height * (.85 - t * .35),
                        entity.posZ,
                        t * .05,
                        -.02,
                        0);
                }
                minecraft.theWorld.playSound(entity.posX, entity.posY, entity.posZ, "random.bow", .4f, 1.9f, false);
            }
        }
        if (event.kind == SemanticEvents.Kind.FINISH) {
            net.minecraft.entity.Entity entity = minecraft.theWorld.getEntityByID(event.actor);
            if (entity != null) {
                if (!fx) for (int i = 0; i < 16; i++) minecraft.theWorld.spawnParticle(
                    "crit",
                    entity.posX,
                    entity.posY + entity.height * .6,
                    entity.posZ,
                    (minecraft.theWorld.rand.nextDouble() - .5) * .6,
                    (minecraft.theWorld.rand.nextDouble() - .2) * .5,
                    (minecraft.theWorld.rand.nextDouble() - .5) * .6);
                minecraft.theWorld
                    .playSound(entity.posX, entity.posY, entity.posZ, "random.anvil_land", .6f, 1.2f, false);
                minecraft.theWorld.playSound(entity.posX, entity.posY, entity.posZ, "random.explode", .3f, 1.7f, false);
            }
        }
        if (event.kind == SemanticEvents.Kind.JUDGE_START || event.kind == SemanticEvents.Kind.JUDGE_BIND
            || event.kind == SemanticEvents.Kind.JUDGE_CUT) {
            net.minecraft.entity.Entity entity = minecraft.theWorld.getEntityByID(event.actor);
            if (entity != null) {
                com.layue13.clashweave.client.vfx.Vfx.INSTANCE.playEvent(event.kind.name(), entity);
                if (event.kind == SemanticEvents.Kind.JUDGE_START) minecraft.theWorld
                    .playSound(entity.posX, entity.posY, entity.posZ, "mob.endermen.portal", .6f, 1.7f, false);
                else if (event.kind == SemanticEvents.Kind.JUDGE_BIND) minecraft.theWorld
                    .playSound(entity.posX, entity.posY, entity.posZ, "portal.trigger", .25f, 1.9f, false);
                else {
                    minecraft.theWorld.playSound(entity.posX, entity.posY, entity.posZ, "dig.glass", 1f, .85f, false);
                    minecraft.theWorld
                        .playSound(entity.posX, entity.posY, entity.posZ, "random.anvil_land", .5f, 1.8f, false);
                }
            }
        }
        if (event.kind == SemanticEvents.Kind.BREAK) {
            net.minecraft.entity.Entity entity = minecraft.theWorld.getEntityByID(event.actor);
            if (entity != null) {
                for (int i = 0; i < 12 && !fx; i++) minecraft.theWorld.spawnParticle(
                    "crit",
                    entity.posX + (minecraft.theWorld.rand.nextDouble() - .5) * entity.width,
                    entity.posY + entity.height * .8,
                    entity.posZ + (minecraft.theWorld.rand.nextDouble() - .5) * entity.width,
                    (minecraft.theWorld.rand.nextDouble() - .5) * .3,
                    .2,
                    (minecraft.theWorld.rand.nextDouble() - .5) * .3);
                minecraft.theWorld.playSound(entity.posX, entity.posY, entity.posZ, "random.break", .5f, 1.2f, false);
            }
        }
        if (event.kind == SemanticEvents.Kind.SLAM) {
            net.minecraft.entity.Entity entity = minecraft.theWorld.getEntityByID(event.actor);
            if (entity != null) {
                for (int i = 0; i < 8 && !fx; i++) {
                    double angle = i * Math.PI / 4;
                    minecraft.theWorld.spawnParticle(
                        "explode",
                        entity.posX,
                        entity.posY + .1,
                        entity.posZ,
                        Math.cos(angle) * .25,
                        .05,
                        Math.sin(angle) * .25);
                }
                minecraft.theWorld.playSound(entity.posX, entity.posY, entity.posZ, "random.explode", .3f, 1.6f, false);
            }
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
