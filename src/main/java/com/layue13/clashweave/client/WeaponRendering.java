package com.layue13.clashweave.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;

import org.lwjgl.opengl.GL11;

import com.layue13.clashweave.Clashweave;
import com.layue13.clashweave.forge.CombatWeapons;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Rigid adapter for any weapon: grip, hand grips and mounts come from the weapon's Resource, the pose from its
 * animation preset. Block units, grip at the model origin, blade +Y; Biped coordinates have downward Y. Every value a
 * frame needs was resolved when the entity's state arrived, so rendering does no lookups by string.
 */
public final class WeaponRendering {

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final ModelBiped arm = new ModelBiped();
    private final float[] swordMatrix = new float[16];
    private final java.nio.FloatBuffer matrixBuffer = org.lwjgl.BufferUtils.createFloatBuffer(16);

    public static void load() {
        PresentationAssets.load(Clashweave.content.loaded.registry, Clashweave.log);
        System.out.println(
            "CW_RENDER models loaded units=block blade=+Y sheath=downwardBipedY resources=" + PresentationAssets.ids()
                .size());
    }

    private PresentationAssets.Render render(ClientProxy.Visual visual, net.minecraft.item.ItemStack held) {
        if (visual != null) return visual.render;
        return PresentationAssets.render(resourceFor(held));
    }

    /** Before state arrives, resolve the rendered entity's item, never the observer's item. */
    static String resourceFor(net.minecraft.item.ItemStack held) {
        CombatWeapons.Entry entry = CombatWeapons.entry(held);
        return entry == null ? "" : entry.definition.appearance.skin;
    }

    private double swing(ClientProxy.Visual visual, float partial) {
        if (visual == null) return 0;
        com.layue13.clashweave.core.ActionCatalog.Definition definition = ClientProxy.instance.definition(visual);
        return definition == null ? 0 : visual.preset.swing(definition, ClientProxy.instance.elapsed(visual, partial));
    }

    /** Whether the blade is in the hand rather than the sheath. */
    private boolean drawn(ClientProxy.Visual visual, float partial) {
        if (visual == null) return true;
        ClientProxy proxy = ClientProxy.instance;
        if (proxy.predicted(visual)) return true;
        if (visual.state.action.isEmpty()) return !visual.state.sheathed;
        return !visual.returnsToSheath || proxy.elapsed(visual, partial) < visual.preset.sheatheVisibleTicks();
    }

    private void sheath(PresentationAssets.Render render, boolean drawn) {
        // Drawn: the empty scabbard (the blade is in the hand). Sheathed: the scabbard with its hilt.
        (drawn ? render.stowedEmpty : render.stowed).renderAll();
    }

    /** The blade in the hand: baked pose when the body is taken over, otherwise attached to the right arm. */
    private void blade(PresentationAssets.Render render, ModelBiped model, ClientProxy.Visual visual, boolean posed,
        PoseDriver driver, CarryStyle carried, float partial) {
        if (posed) {
            // The blade follows the baked sword pose in body space, not the arm, so arm roll cannot turn it.
            GL11.glPushMatrix();
            com.layue13.clashweave.client.pose.LimbSolver.swordMatrix(driver.pose, swordMatrix);
            matrixBuffer.clear();
            matrixBuffer.put(swordMatrix)
                .flip();
            GL11.glMultMatrix(matrixBuffer);
            GL11.glTranslated(-render.gripX, -render.gripY, -render.gripZ);
            render.held.renderAll();
            GL11.glPopMatrix();
        } else {
            GL11.glPushMatrix();
            model.bipedRightArm.postRender(1 / 16f);
            GL11.glTranslated(render.rightX, render.rightY, render.rightZ);
            // Carried at the side the blade points down and a little forward along the leg; held out it points ahead.
            GL11.glRotated(carried != null ? carried.pitch : -90, 1, 0, 0);
            GL11.glRotated(swing(visual, partial), 0, 0, 1);
            GL11.glRotated(90, 0, 1, 0);
            GL11.glScaled(1 / .9375, 1 / .9375, 1 / .9375);
            GL11.glTranslated(-render.gripX, -render.gripY, -render.gripZ);
            render.held.renderAll();
            GL11.glPopMatrix();
        }
    }

    @SubscribeEvent
    public void specialPre(RenderPlayerEvent.Specials.Pre event) {
        if (CombatWeapons.armed(event.entityPlayer.getHeldItem())) event.renderItem = false;
    }

    @SubscribeEvent
    public void specialPost(RenderPlayerEvent.Specials.Post event) {
        if (!CombatWeapons.armed(event.entityPlayer.getHeldItem())) return;
        ClientProxy.Visual visual = ClientProxy.instance.visualOf(event.entityPlayer.getEntityId());
        PresentationAssets.Render render = render(visual, event.entityPlayer.getHeldItem());
        PoseDriver driver = PoseDriver.INSTANCE;
        // Half-way through a fade the blade moves from the arm-attached pose to the baked one; the body is blended by
        // then.
        boolean posed = driver.entity == event.entityPlayer.getEntityId() && driver.weight >= 0.5f;
        boolean drawn = posed ? driver.bladeDrawn : drawn(visual, event.partialRenderTick);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glEnable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        GL11.glColor4f(1, 1, 1, 1);
        minecraft.getTextureManager()
            .bindTexture(render.texture);
        if (render.mounted) {
            GL11.glPushMatrix();
            // The pelvis follows Biped's crouch translation, but not torso lean or walking leg rotation.
            ModelBiped biped = event.renderer.modelBipedMain;
            GL11.glTranslated(
                render.mountX,
                render.mountY + (biped.bipedLeftLeg.rotationPointY - 12) / 16.0,
                render.mountZ + biped.bipedLeftLeg.rotationPointZ / 16.0);
            GL11.glRotated(render.mountAngle, 1, 0, 0);
            GL11.glScaled(1 / .9375, 1 / .9375, 1 / .9375);
            sheath(render, drawn);
            GL11.glPopMatrix();
        }
        if (drawn) blade(
            render,
            event.renderer.modelBipedMain,
            visual,
            posed,
            driver,
            PoseDriver.carrying(event.entityPlayer) ? CarryStyle.of(resourceFor(event.entityPlayer.getHeldItem()))
                : null,
            event.partialRenderTick);
        GL11.glPopAttrib();
    }

    @SubscribeEvent
    public void hand(RenderHandEvent event) {
        if (minecraft.thePlayer == null || minecraft.gameSettings.thirdPersonView != 0
            || !CombatWeapons.armed(minecraft.thePlayer.getHeldItem())) return;
        event.setCanceled(true);
        ClientProxy.Visual visual = ClientProxy.instance.own();
        // Like the vanilla hand, nothing is drawn with the interface hidden.
        if (minecraft.gameSettings.hideGUI || minecraft.thePlayer.isPlayerSleeping()) return;
        boolean drawn = drawn(visual, event.partialTicks);
        PresentationAssets.Render render = render(visual, minecraft.thePlayer.getHeldItem());
        net.minecraft.client.renderer.entity.Render renderer = net.minecraft.client.renderer.entity.RenderManager.instance
            .getEntityRenderObject(minecraft.thePlayer);
        if (renderer instanceof KatanaPlayerRenderer) {
            bothArms((KatanaPlayerRenderer) renderer, visual, render, drawn, event.partialTicks);
            return;
        }
        if (!drawn) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glEnable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        org.lwjgl.util.glu.GLU.gluPerspective(70, (float) minecraft.displayWidth / minecraft.displayHeight, 0.05f, 128);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslated(0.48, -0.40, -0.45);
        GL11.glRotated(180, 0, 0, 1);
        arm.bipedRightArm.setRotationPoint(0, 0, 0);
        arm.bipedRightArm.rotateAngleX = -1.1f;
        arm.bipedRightArm.rotateAngleY = 0;
        arm.bipedRightArm.rotateAngleZ = (float) Math.toRadians(swing(visual, event.partialTicks) * 0.25);
        minecraft.getTextureManager()
            .bindTexture(minecraft.thePlayer.getLocationSkin());
        arm.bipedRightArm.render(1 / 16f);
        arm.bipedRightArm.postRender(1 / 16f);
        GL11.glTranslated(render.rightX, render.rightY, render.rightZ);
        GL11.glRotated(-55, 1, 0, 0);
        GL11.glRotated(swing(visual, event.partialTicks), 0, 0, 1);
        minecraft.getTextureManager()
            .bindTexture(render.texture);
        GL11.glTranslated(-render.gripX, -render.gripY, -render.gripZ);
        render.held.renderAll();
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopAttrib();
    }

    /** First person: the observer's body arms, posed like the third-person body, with the blade as it is there. */
    private void bothArms(KatanaPlayerRenderer renderer, ClientProxy.Visual visual, PresentationAssets.Render render,
        boolean blade, float partial) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glEnable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        org.lwjgl.util.glu.GLU.gluPerspective(70, (float) minecraft.displayWidth / minecraft.displayHeight, 0.05f, 128);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        renderer.placeHands(minecraft.thePlayer, visual, partial);
        PoseDriver driver = PoseDriver.INSTANCE;
        boolean posed = driver.weight >= 0.5f;
        minecraft.getTextureManager()
            .bindTexture(minecraft.thePlayer.getLocationSkin());
        renderer.hands.bipedRightArm.render(1 / 16f);
        renderer.hands.bipedLeftArm.render(1 / 16f);
        minecraft.getTextureManager()
            .bindTexture(render.texture);
        if (blade) blade(
            render,
            renderer.hands,
            visual,
            posed,
            driver,
            PoseDriver.carrying(minecraft.thePlayer) ? CarryStyle.of(resourceFor(minecraft.thePlayer.getHeldItem()))
                : null,
            partial);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopAttrib();
    }

    @SubscribeEvent
    public void ownWaist(net.minecraftforge.client.event.RenderWorldLastEvent event) {
        if (minecraft.thePlayer == null || minecraft.gameSettings.thirdPersonView != 0
            || !CombatWeapons.armed(minecraft.thePlayer.getHeldItem())) return;
        ClientProxy.Visual visual = ClientProxy.instance.own();
        PresentationAssets.Render render = render(visual, minecraft.thePlayer.getHeldItem());
        if (!render.mounted) return;
        net.minecraft.client.entity.EntityClientPlayerMP player = minecraft.thePlayer;
        double partial = event.partialTicks;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glPushMatrix();
        GL11.glEnable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glTranslated(
            player.lastTickPosX + (player.posX - player.lastTickPosX) * partial
                - net.minecraft.client.renderer.entity.RenderManager.renderPosX,
            player.lastTickPosY + (player.posY - player.lastTickPosY) * partial
                - player.yOffset
                - net.minecraft.client.renderer.entity.RenderManager.renderPosY
                - (player.isSneaking() ? .125 : 0),
            player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partial
                - net.minecraft.client.renderer.entity.RenderManager.renderPosZ);
        double yaw = visual != null && visual.state.instance != 0 ? visual.state.yaw : player.renderYawOffset;
        GL11.glRotated(180 - yaw, 0, 1, 0);
        GL11.glScaled(-.9375, -.9375, .9375);
        GL11.glTranslated(0, -1.5078125, 0);
        GL11.glTranslated(
            render.mountX,
            render.mountY - (player.isSneaking() ? 3 / 16.0 : 0),
            render.mountZ + (player.isSneaking() ? 4 / 16.0 : 0));
        GL11.glRotated(render.mountAngle, 1, 0, 0);
        GL11.glScaled(1 / .9375, 1 / .9375, 1 / .9375);
        minecraft.getTextureManager()
            .bindTexture(render.texture);
        sheath(render, drawn(visual, event.partialTicks));
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }
}
