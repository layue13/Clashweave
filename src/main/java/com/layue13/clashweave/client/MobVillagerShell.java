package com.layue13.clashweave.client;

import net.minecraft.client.model.ModelZombieVillager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

/**
 * A {@link MobBiped} in the type RenderZombie keeps its villager model in (ModelZombieVillager): that renderer puts the
 * field's model back as the main model between the draw and the hurt flash, so only a model of that type stays ours
 * for both. It draws the same parts and hands every pose to the core.
 */
final class MobVillagerShell extends ModelZombieVillager {

    final MobBiped core;

    MobVillagerShell(MobBiped core) {
        super();
        this.core = core;
        textureWidth = core.textureWidth;
        textureHeight = core.textureHeight;
        bipedHead = core.bipedHead;
        bipedHeadwear = core.bipedHeadwear;
        bipedBody = core.bipedBody;
        bipedRightArm = core.bipedRightArm;
        bipedLeftArm = core.bipedLeftArm;
        bipedRightLeg = core.bipedRightLeg;
        bipedLeftLeg = core.bipedLeftLeg;
        bipedEars = core.bipedEars;
        bipedCloak = core.bipedCloak;
    }

    private void toCore() {
        core.onGround = onGround;
        core.isRiding = isRiding;
        core.isChild = isChild;
        core.heldItemLeft = heldItemLeft;
        core.heldItemRight = heldItemRight;
        core.isSneak = isSneak;
        core.aimedBow = aimedBow;
    }

    @Override
    public void setLivingAnimations(EntityLivingBase entity, float limb, float amount, float partial) {
        toCore();
        core.setLivingAnimations(entity, limb, amount, partial);
        heldItemLeft = core.heldItemLeft;
        heldItemRight = core.heldItemRight;
        aimedBow = core.aimedBow;
        isSneak = core.isSneak;
    }

    @Override
    public void setRotationAngles(float limb, float amount, float age, float headYaw, float pitch, float scale,
        Entity entity) {
        toCore();
        core.setRotationAngles(limb, amount, age, headYaw, pitch, scale, entity);
    }
}
