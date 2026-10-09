package com.layue13.clashweave.probe.client;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;

import org.lwjgl.opengl.GL11;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.layue13.clashweave.probe.ProbeBootstrap;
import com.layue13.clashweave.probe.ProbeServer;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public final class ProbeRendering implements IItemRenderer {

    private final ProbeClient client;
    private IModelCustom blade;
    private IModelCustom sheath;
    private final ResourceLocation texture = new ResourceLocation("clashweave", "probe/placeholder.png");
    private float startAngle;
    private float endAngle;
    private float partial;

    ProbeRendering(ProbeClient client) {
        this.client = client;
    }

    void init() {
        blade = AdvancedModelLoader.loadModel(new ResourceLocation("clashweave", "probe/blade.obj"));
        sheath = AdvancedModelLoader.loadModel(new ResourceLocation("clashweave", "probe/sheath.obj"));
        try (InputStreamReader reader = new InputStreamReader(
            Minecraft.getMinecraft()
                .getResourceManager()
                .getResource(new ResourceLocation("clashweave", "probe/presentation.json"))
                .getInputStream(),
            StandardCharsets.UTF_8)) {
            JsonObject data = new JsonParser().parse(reader)
                .getAsJsonObject();
            if (data.get("format")
                .getAsInt() != 1) throw new IllegalArgumentException("S4 format");
            startAngle = data.get("swingStartDegrees")
                .getAsFloat();
            endAngle = data.get("swingEndDegrees")
                .getAsFloat();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        MinecraftForgeClient.registerItemRenderer(ProbeBootstrap.blade, this);
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(this);
        ProbeServer.log("S4_IMPORT loader=AdvancedModelLoader format=OBJ texture=PNG units=blocks up=Y origin=grip");
    }

    @SubscribeEvent
    public void renderTick(cpw.mods.fml.common.gameevent.TickEvent.RenderTickEvent e) {
        partial = e.renderTickTime;
    }

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return type == ItemRenderType.EQUIPPED_FIRST_PERSON || type == ItemRenderType.EQUIPPED
            || type == ItemRenderType.ENTITY;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return false;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        int entity = data.length > 1 && data[1] instanceof net.minecraft.entity.Entity
            ? ((net.minecraft.entity.Entity) data[1]).getEntityId()
            : -1;
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_LIGHTING_BIT);
        try {
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(texture);
            GL11.glColor4f(1, 1, 1, 1);
            if (type == ItemRenderType.EQUIPPED_FIRST_PERSON) {
                GL11.glTranslatef(0.35F, 0.1F, 0);
                GL11.glRotatef(-35, 0, 0, 1);
                GL11.glRotatef(angle(entity, partial), 0, 0, 1);
            }
            blade.renderAll();
        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    private float angle(int entity, float partialTick) {
        ProbeClient.Swing swing = client.swings.get(entity);
        if (swing == null) return 0;
        float p = swing.progress(client.clock(), partialTick);
        if (p >= 1) return 0;
        float t = p < 0.31F ? p / 0.31F : p < 0.46F ? (p - 0.31F) / 0.15F : (p - 0.46F) / 0.54F;
        t = t * t * (3 - 2 * t);
        return p < 0.31F ? startAngle * t : p < 0.46F ? startAngle + (endAngle - startAngle) * t : endAngle * (1 - t);
    }

    private boolean held(RenderPlayerEvent e) {
        return e.entityPlayer.getHeldItem() != null && e.entityPlayer.getHeldItem()
            .getItem() == ProbeBootstrap.blade;
    }

    @SubscribeEvent
    public void pre(RenderPlayerEvent.Specials.Pre e) {
        if (held(e)) e.renderItem = false;
    }

    @SubscribeEvent
    public void post(RenderPlayerEvent.Specials.Post e) {
        if (!held(e)) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_LIGHTING_BIT);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(texture);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glPushMatrix();
        try {
            e.renderer.modelBipedMain.bipedRightArm.postRender(0.0625F);
            GL11.glTranslatef(0, 0.6F, 0);
            GL11.glRotatef(-90, 1, 0, 0);
            GL11.glRotatef(angle(e.entityPlayer.getEntityId(), e.partialRenderTick), 0, 0, 1);
            blade.renderAll();
        } finally {
            GL11.glPopMatrix();
        }
        GL11.glPushMatrix();
        try {
            // ModelBiped adapter: waist is body-local, not a segmented pelvis rig.
            e.renderer.modelBipedMain.bipedBody.postRender(0.0625F);
            GL11.glTranslatef(-0.28F, 0.7F, 0.12F);
            GL11.glRotatef(-25, 0, 0, 1);
            GL11.glScalef(1, -1, 1);
            sheath.renderAll();
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}
