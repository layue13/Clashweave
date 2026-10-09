package com.layue13.clashweave.experiments.grip;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.Project;

/** Original ModelBiped adapter, with an explicit first-person hand mount. */
public class GripRendering {
    public static int pose;
    private IModelCustom blade,sheath;
    private final ResourceLocation texture=new ResourceLocation("cwgrip","palette.png");
    private final ModelBiped arm=new ModelBiped();
    private void load() {
        if(blade!=null) return;
        blade=AdvancedModelLoader.loadModel(new ResourceLocation("cwgrip","blade.obj"));
        sheath=AdvancedModelLoader.loadModel(new ResourceLocation("cwgrip","sheath.obj"));
        GripExperiment.log("IMPORT OBJ PNG unit=block grip=(0,0,0) bladeTip=(0,1.12,0) sourceToGame=(x,z,-y)");
    }
    private boolean held(net.minecraft.entity.player.EntityPlayer p) {
        return p.getHeldItem()!=null && p.getHeldItem().getItem()==GripExperiment.BLADE;
    }
    @SubscribeEvent public void hand(RenderHandEvent e) {
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.thePlayer==null || !held(mc.thePlayer) || mc.gameSettings.thirdPersonView!=0) return;
        load(); e.setCanceled(true);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPushMatrix();GL11.glLoadIdentity();
        Project.gluPerspective(70,(float)mc.displayWidth/mc.displayHeight,.05F,128);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();GL11.glLoadIdentity();
        try {
            GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glColor4f(1,1,1,1);
            // Forearm enters from below; palm sits at y=-0.55 in camera space.
            GL11.glTranslatef(.50F,-1.15F,-1.15F);
            arm.bipedRightArm.rotationPointX=0;arm.bipedRightArm.rotationPointY=0;arm.bipedRightArm.rotationPointZ=0;
            arm.bipedRightArm.rotateAngleX=0;arm.bipedRightArm.rotateAngleY=0;arm.bipedRightArm.rotateAngleZ=0;
            mc.getTextureManager().bindTexture(mc.thePlayer.getLocationSkin());
            arm.bipedRightArm.render(.0625F);
            GL11.glTranslatef(-.0625F,.60F,0);
            GL11.glRotatef(pose==0 ? 16 : -25,0,0,1);
            mc.getTextureManager().bindTexture(texture);blade.renderAll();
        } finally {
            GL11.glPopMatrix();GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPopAttrib();
        }
    }
    @SubscribeEvent public void pre(RenderPlayerEvent.Specials.Pre e) { if(held(e.entityPlayer)) e.renderItem=false; }
    @SubscribeEvent public void post(RenderPlayerEvent.Specials.Post e) {
        if(!held(e.entityPlayer)) return;
        load();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_CULL_FACE);
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);GL11.glColor4f(1,1,1,1);
        GL11.glPushMatrix();
        try {
            e.renderer.modelBipedMain.bipedRightArm.postRender(.0625F);
            GL11.glTranslatef(-.0625F,.60F,0);
            GL11.glRotatef(-90,1,0,0);
            GL11.glRotatef(pose==0 ? 0 : 35,0,0,1);
            blade.renderAll();
        } finally { GL11.glPopMatrix(); }
        GL11.glPushMatrix();
        try {
            e.renderer.modelBipedMain.bipedBody.postRender(.0625F);
            GL11.glTranslatef(.30F,.65F,.04F);
            GL11.glRotatef(65,1,0,0);
            GL11.glRotatef(-15,0,0,1);
            sheath.renderAll();
        } finally { GL11.glPopMatrix();GL11.glPopAttrib(); }
    }
}
