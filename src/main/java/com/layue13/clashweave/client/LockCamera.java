package com.layue13.clashweave.client;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.layue13.clashweave.Clashweave;
import com.layue13.clashweave.core.ActionCatalog;
import com.layue13.clashweave.core.LockAssist;
import com.layue13.clashweave.core.LockFollow;
import com.layue13.clashweave.core.LockView;
import com.layue13.clashweave.forge.CombatServer;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Owner-only lock display and local camera; never writes a committed action yaw. */
public final class LockCamera {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final ClientProxy proxy;
    private final LockFollow follow = new LockFollow();
    private final LockView view = new LockView();
    private Object world;
    private int previousTarget = -1;
    private long previousNano;
    public LockFollow.Result last;

    LockCamera(ClientProxy proxy) {
        this.proxy = proxy;
    }

    public void clear() {
        follow.clear();
        view.clear();
        previousTarget = -1;
        previousNano = 0;
        world = null;
        last = null;
    }

    public void manualViewChange() {
        view.manualChange();
    }

    public void mouse(long now, double yawDelta) {
        follow.mouse(now, yawDelta, Clashweave.config.lockFollow);
    }

    public net.minecraft.util.MouseHelper wrap(final net.minecraft.util.MouseHelper original) {
        return new net.minecraft.util.MouseHelper() {

            @Override
            public void grabMouseCursor() {
                original.grabMouseCursor();
                deltaX = 0;
                deltaY = 0;
            }

            @Override
            public void ungrabMouseCursor() {
                original.ungrabMouseCursor();
            }

            @Override
            public void mouseXYChange() {
                original.mouseXYChange();
                deltaX = original.deltaX;
                deltaY = original.deltaY;
                if (deltaX != 0 || deltaY != 0) {
                    double sensitivity = mc.gameSettings.mouseSensitivity * .6 + .2;
                    mouse(System.nanoTime(), deltaX * sensitivity * sensitivity * sensitivity * 8 * .15);
                }
                EntityLivingBase target = target();
                boolean strong = mc.gameSettings.thirdPersonView == 0
                    ? Clashweave.config.lockFollowFirstPerson == LockFollow.Mode.STRONG
                    : Clashweave.config.lockFollowThirdPerson == LockFollow.Mode.STRONG;
                if (strong && target != null
                    && mc.currentScreen == null
                    && (last == null || !last.reason.equals("OCCLUDED") || visible(target))) {
                    // Strong mode consumes mouse as bounded offset; vanilla must not add a second camera turn.
                    deltaX = 0;
                    deltaY = 0;
                }
            }
        };
    }

    public EntityLivingBase target() {
        ClientProxy.Visual own = proxy.own();
        if (own == null || mc.theWorld == null || mc.thePlayer == null || mc.thePlayer.isDead) return null;
        Entity entity = mc.theWorld.getEntityByID(own.state.lockTarget);
        if (!(entity instanceof EntityLivingBase)) return null;
        EntityLivingBase target = (EntityLivingBase) entity;
        return LockAssist.valid(
            CombatServer.armed(mc.thePlayer),
            own.state.lockTarget,
            !target.isDead && target.getHealth() > 0,
            distance(target),
            own.state.lockKeepRange) ? target : null;
    }

    private double distance(EntityLivingBase target) {
        double dx = target.posX - mc.thePlayer.posX;
        double dz = target.posZ - mc.thePlayer.posZ;
        double dy = target.boundingBox.minY - mc.thePlayer.boundingBox.minY;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private boolean visible(EntityLivingBase target) {
        return mc.theWorld.rayTraceBlocks(
            mc.thePlayer.getPosition(1),
            net.minecraft.util.Vec3.createVectorHelper(target.posX, target.posY + target.getEyeHeight(), target.posZ))
            == null;
    }

    public void tick() {
        if (mc.theWorld != world) {
            clear();
            world = mc.theWorld;
        }
        long now = System.nanoTime();
        double dt = previousNano == 0 ? 0 : (now - previousNano) / 1_000_000_000.0;
        previousNano = now;
        EntityLivingBase target = target();
        int id = target == null ? -1 : target.getEntityId();
        if (id != previousTarget) {
            follow.clear();
            previousTarget = id;
            if (Boolean.getBoolean("clashweave.trace"))
                System.out.println("CW_LOCK_MARKER target=" + id + " nano=" + now);
        }
        mc.gameSettings.thirdPersonView = view.update(
            target != null,
            mc.gameSettings.thirdPersonView,
            Clashweave.config.lockAutoThirdPerson,
            Clashweave.config.lockRestoreView);
        if (target == null) {
            last = null;
            return;
        }
        ClientProxy.Visual own = proxy.own();
        boolean weakAllowed = proxy.action(own)
            .isEmpty();
        if (!weakAllowed && proxy.actions != null && !own.state.action.isEmpty()) {
            ActionCatalog.Definition definition = proxy.actions.get(own.state.action);
            weakAllowed = proxy.elapsed(own, 0) >= definition.startup + definition.active;
        }
        double dx = target.posX - mc.thePlayer.posX;
        double dz = target.posZ - mc.thePlayer.posZ;
        double dy = target.posY + target.getEyeHeight() - mc.thePlayer.getPosition(1).yCoord;
        LockFollow.Mode mode = mc.gameSettings.thirdPersonView == 0 ? Clashweave.config.lockFollowFirstPerson
            : Clashweave.config.lockFollowThirdPerson;
        float beforeYaw = mc.thePlayer.rotationYaw, beforePitch = mc.thePlayer.rotationPitch;
        last = follow.step(
            mode,
            now,
            dt,
            beforeYaw,
            beforePitch,
            Math.toDegrees(Math.atan2(-dx, dz)),
            -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))),
            visible(target),
            mc.currentScreen == null,
            weakAllowed,
            Clashweave.config.lockFollow);
        mc.thePlayer.rotationYaw = last.yaw;
        mc.thePlayer.rotationPitch = last.pitch;
        if (Boolean.getBoolean("clashweave.trace")) System.out.println(
            "CW_LOCK_FOLLOW mode=" + mode
                + " reason="
                + last.reason
                + " target="
                + id
                + " nano="
                + now
                + " dt="
                + dt
                + " before="
                + beforeYaw
                + " after="
                + last.yaw
                + " pitchBefore="
                + beforePitch
                + " pitchAfter="
                + last.pitch
                + " offset="
                + last.offset
                + " instance="
                + own.state.instance
                + " committed="
                + own.state.yaw);
    }

    @SubscribeEvent
    public void marker(RenderWorldLastEvent event) {
        EntityLivingBase target = target();
        if (target == null) return;
        String label = String.format(Locale.ROOT, "[LOCK] %.1f m", distance(target));
        float partial = event.partialTicks;
        double x = target.lastTickPosX + (target.posX - target.lastTickPosX) * partial - RenderManager.renderPosX;
        double y = target.lastTickPosY + (target.posY - target.lastTickPosY) * partial
            + target.height
            + .35
            - RenderManager.renderPosY;
        double z = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * partial - RenderManager.renderPosZ;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x, y, z);
            GL11.glRotatef(-RenderManager.instance.playerViewY, 0, 1, 0);
            GL11.glRotatef(RenderManager.instance.playerViewX, 1, 0, 0);
            GL11.glScalef(-.025f, -.025f, .025f);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            mc.fontRenderer.drawStringWithShadow(label, -mc.fontRenderer.getStringWidth(label) / 2, 0, 0xFFFF66);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}
