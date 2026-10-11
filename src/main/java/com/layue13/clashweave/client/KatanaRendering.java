package com.layue13.clashweave.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;

import org.lwjgl.opengl.GL11;

import com.layue13.clashweave.core.ActionCatalog;
import com.layue13.clashweave.forge.CombatServer;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Rigid placeholder adapter: block units, grip origin, blade +Y; Biped coordinates have downward Y. */
public final class KatanaRendering {

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final ModelBiped arm = new ModelBiped();
    public static double sheathAngle = 45;
    public static double sheathX = 0.32;
    public static double sheathY = 0.55;
    public static double sheathZ = 0.18;

    public static void load() {
        net.minecraftforge.common.config.Configuration config = new net.minecraftforge.common.config.Configuration(
            new java.io.File(Minecraft.getMinecraft().mcDataDir, "config/clashweave/render.cfg"));
        config.load();
        sheathAngle = config.getFloat("sheathAngle", "mount", 45, 30, 60, "Angle to downward body axis");
        sheathX = config.getFloat("sheathX", "mount", 0.32f, 0.25f, 0.6f, "Left hip in block units");
        sheathY = config.getFloat("sheathY", "mount", 0.55f, 0.4f, 0.8f, "Height in Biped downward axis");
        sheathZ = config.getFloat("sheathZ", "mount", 0.18f, 0.1f, 0.4f, "Rear offset in block units");
        config.save();
        PresentationAssets.load();
        System.out.println("CW_RENDER models loaded units=block blade=+Y sheath=downwardBipedY angle=" + sheathAngle);
    }

    private double swing(ClientProxy.Visual visual, float partial) {
        String action = ClientProxy.instance.action(visual);
        if (action.isEmpty() || ClientProxy.instance.actions == null) return 0;
        String preset = PresentationAssets.animation(visual, action);
        if (!preset.equals("rigid_arc")) throw new IllegalArgumentException("Unknown animation preset");
        ActionCatalog.Definition definition = ClientProxy.instance.actions.get(action);
        double elapsed = ClientProxy.instance.elapsed(visual, partial);
        double angle;
        if (elapsed < definition.startup) angle = -definition.arc / 2 * elapsed / Math.max(1, definition.startup);
        else if (elapsed < definition.startup + definition.active) angle = -definition.arc / 2
            + definition.arc * (elapsed - definition.startup) / Math.max(1, definition.active);
        else angle = definition.arc / 2
            * Math.max(0, 1 - (elapsed - definition.startup - definition.active) / definition.recovery);
        return angle;
    }

    private boolean visible(ClientProxy.Visual visual, float partial) {
        if (visual == null) return true;
        String action = ClientProxy.instance.action(visual);
        if (action.equals("sheathe")) return ClientProxy.instance.elapsed(visual, partial) < 6;
        return !visual.state.sheathed || !action.isEmpty();
    }

    @SubscribeEvent
    public void specialPre(RenderPlayerEvent.Specials.Pre event) {
        if (CombatServer.armed(event.entityPlayer)) event.renderItem = false;
    }

    @SubscribeEvent
    public void specialPost(RenderPlayerEvent.Specials.Post event) {
        if (!CombatServer.armed(event.entityPlayer)) return;
        ClientProxy.Visual visual = ClientProxy.instance.visuals.get(event.entityPlayer.getEntityId());
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glEnable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
        GL11.glColor4f(1, 1, 1, 1);
        minecraft.getTextureManager()
            .bindTexture(PresentationAssets.models(visual).texture);
        GL11.glPushMatrix();
        // The pelvis follows Biped's crouch translation, but not torso lean or walking leg rotation.
        ModelBiped biped = event.renderer.modelBipedMain;
        GL11.glTranslated(
            sheathX,
            sheathY + (biped.bipedLeftLeg.rotationPointY - 12) / 16.0,
            sheathZ + biped.bipedLeftLeg.rotationPointZ / 16.0);
        GL11.glRotated(sheathAngle, 1, 0, 0);
        GL11.glScaled(1 / .9375, 1 / .9375, 1 / .9375);
        PresentationAssets.models(visual).sheath.renderAll();
        GL11.glPopMatrix();
        if (visible(visual, event.partialRenderTick)) {
            GL11.glPushMatrix();
            event.renderer.modelBipedMain.bipedRightArm.postRender(1 / 16f);
            GL11.glTranslated(-0.0625, 0.55, 0);
            GL11.glRotated(-90, 1, 0, 0);
            GL11.glRotated(swing(visual, event.partialRenderTick), 0, 0, 1);
            GL11.glRotated(90, 0, 1, 0);
            GL11.glScaled(1 / .9375, 1 / .9375, 1 / .9375);
            PresentationAssets.models(visual).blade.renderAll();
            GL11.glPopMatrix();
        }
        GL11.glPopAttrib();
    }

    @SubscribeEvent
    public void hand(RenderHandEvent event) {
        if (minecraft.thePlayer == null || minecraft.gameSettings.thirdPersonView != 0
            || !CombatServer.armed(minecraft.thePlayer)) return;
        event.setCanceled(true);
        ClientProxy.Visual visual = ClientProxy.instance.own();
        if (!visible(visual, event.partialTicks)) return;
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
        GL11.glTranslated(-0.0625, 0.55, 0);
        GL11.glRotated(-55, 1, 0, 0);
        GL11.glRotated(swing(visual, event.partialTicks), 0, 0, 1);
        minecraft.getTextureManager()
            .bindTexture(PresentationAssets.models(visual).texture);
        PresentationAssets.models(visual).blade.renderAll();
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopAttrib();
    }

    @SubscribeEvent
    public void ownWaist(net.minecraftforge.client.event.RenderWorldLastEvent event) {
        if (minecraft.thePlayer == null || minecraft.gameSettings.thirdPersonView != 0
            || !CombatServer.armed(minecraft.thePlayer)) return;
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
        ClientProxy.Visual visual = ClientProxy.instance.own();
        double yaw = visual != null && visual.state.instance != 0 ? visual.state.yaw : player.renderYawOffset;
        GL11.glRotated(180 - yaw, 0, 1, 0);
        GL11.glScaled(-.9375, -.9375, .9375);
        GL11.glTranslated(0, -1.5078125, 0);
        GL11.glTranslated(
            sheathX,
            sheathY - (player.isSneaking() ? 3 / 16.0 : 0),
            sheathZ + (player.isSneaking() ? 4 / 16.0 : 0));
        GL11.glRotated(sheathAngle, 1, 0, 0);
        GL11.glScaled(1 / .9375, 1 / .9375, 1 / .9375);
        minecraft.getTextureManager()
            .bindTexture(PresentationAssets.models(visual).texture);
        PresentationAssets.models(visual).sheath.renderAll();
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }
}
