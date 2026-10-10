package com.layue13.clashweave.client;

import java.nio.FloatBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.layue13.clashweave.Clashweave;
import com.layue13.clashweave.core.SoulsComposition;
import com.layue13.clashweave.core.ThirdPersonCamera;
import com.layue13.clashweave.forge.CombatConfig;

/** Centered raised camera. No gameplay fields, input or network packets are modified here. */
public final class SoulsCamera {

    private static final FloatBuffer before = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
    private static final ThirdPersonCamera.Spring distanceSpring = new ThirdPersonCamera.Spring();
    private static final ThirdPersonCamera.Spring pitchSpring = new ThirdPersonCamera.Spring();
    private static final ThirdPersonCamera.Spring collisionSpring = new ThirdPersonCamera.Spring();
    private static boolean captured, initialized;
    private static long last;
    private static Object world;
    private static int targetId = -1;
    public static boolean active, hidden;
    public static double distance, pitch, fraction = 1, safe = 1, cameraX, cameraY, cameraZ, delta, verticalFov,
        pitchLimit;

    private SoulsCamera() {}

    private static boolean enabled(Minecraft mc) {
        return Clashweave.config != null && ClientProxy.instance != null
            && mc.thePlayer != null
            && mc.theWorld != null
            && mc.renderViewEntity == mc.thePlayer
            && mc.gameSettings.thirdPersonView == 1
            && !mc.gameSettings.debugCamEnable
            && !mc.thePlayer.isPlayerSleeping()
            && mc.currentScreen == null
            && Clashweave.config.lockCompositionPreset == ThirdPersonCamera.Preset.SOULS
            && ClientProxy.instance.lockCamera.thirdStrong()
            && ClientProxy.instance.lockCamera.cameraReady();
    }

    public static void begin() {
        Minecraft mc = Minecraft.getMinecraft();
        captured = enabled(mc);
        active = false;
        hidden = captured && hidden;
        if (captured) {
            before.clear();
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, before);
        }
    }

    public static void finish(float partial) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!captured || !enabled(mc)) {
            initialized = false;
            last = 0;
            return;
        }
        EntityLivingBase target = ClientProxy.instance.lockCamera.target();
        if (target == null) return;
        CombatConfig c = Clashweave.config;
        long now = System.nanoTime();
        double dt = last == 0 ? 0 : ThirdPersonCamera.dt((now - last) / 1e9);
        last = now;
        double px = mc.thePlayer.prevPosX + (mc.thePlayer.posX - mc.thePlayer.prevPosX) * partial;
        double py = mc.thePlayer.prevPosY + (mc.thePlayer.posY - mc.thePlayer.prevPosY) * partial;
        double pz = mc.thePlayer.prevPosZ + (mc.thePlayer.posZ - mc.thePlayer.prevPosZ) * partial;
        double footOffset = mc.thePlayer.boundingBox.minY - mc.thePlayer.posY;
        // 1.7.10 local-player posY is the eye anchor; getEyeHeight() is only .12 here.
        // Match vanilla orientCamera: its final translation is yOffset - 1.62.
        double eye = 1.62 - mc.thePlayer.yOffset;
        double height = target.boundingBox.maxY - target.boundingBox.minY;
        double blend = SoulsComposition.sizeBlend(height, c.smallTargetHeight, c.largeTargetHeight);
        pitchLimit = c.smallTargetPitchMax + blend * (c.cameraPitchMax - c.smallTargetPitchMax);
        double aim = c.smallTargetAimFraction + blend * (.5 - c.smallTargetAimFraction);
        double[] filtered = ClientProxy.instance.lockCamera.cameraPoint(target, aim);
        projection.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
        verticalFov = SoulsComposition.verticalFov(projection.get(5));
        double range = Math.hypot(filtered[0] - px, filtered[2] - pz);
        SoulsComposition.Pose goal = SoulsComposition.solve(
            range,
            filtered[1] - py,
            footOffset + mc.thePlayer.height / 2,
            eye + c.cameraHeightOffset,
            verticalFov,
            c.cameraDistanceMin,
            c.cameraDistanceMax,
            c.cameraPitchMin,
            pitchLimit,
            c.targetScreenY,
            c.playerScreenY,
            footOffset + mc.thePlayer.height,
            c.cameraHeadClearance);
        if (!initialized || world != mc.theWorld) {
            distance = Math.max(c.cameraDistanceMin, Math.min(c.cameraDistanceMax, c.cameraDistance));
            pitch = Math.max(c.cameraPitchMin, Math.min(pitchLimit, c.baselinePitchDown));
            fraction = 1;
            distanceSpring.clear();
            pitchSpring.clear();
            collisionSpring.clear();
            initialized = true;
            world = mc.theWorld;
            targetId = target.getEntityId();
        }
        if (targetId != target.getEntityId()) {
            // A switch changes only the goal. Keep the existing camera pose continuous.
            targetId = target.getEntityId();
        }
        distance = distanceSpring.step(distance, goal.distance, dt, c.lockStrongSmoothTime, 8);
        pitch = pitchSpring.step(pitch, goal.pitch, dt, c.lockStrongSmoothTime, c.lockFollow.strongSpeed);
        distance = Math.max(c.cameraDistanceMin, Math.min(c.cameraDistanceMax, distance));
        pitch = Math.max(c.cameraPitchMin, Math.min(pitchLimit, pitch));
        double yaw = Math.toRadians(mc.thePlayer.rotationYaw), angle = Math.toRadians(pitch);
        double dx = Math.sin(yaw) * distance * Math.cos(angle) + Math.cos(yaw) * c.lateralOffset;
        double dz = -Math.cos(yaw) * distance * Math.cos(angle) + Math.sin(yaw) * c.lateralOffset;
        double dy = c.cameraHeightOffset + distance * Math.sin(angle);
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz), hitFraction = 1;
        Vec3 origin = Vec3.createVectorHelper(px, py + eye, pz);
        // Current rays enforce safety; surrounding-eye rays anticipate entering a low overhang.
        hitFraction = obstruction(mc, origin, dx, dy, dz, c.cameraCollisionRadius);
        double anticipated = hitFraction;
        for (int i = 0; i < 4; i++) {
            Vec3 ahead = origin.addVector(
                i == 0 ? c.cameraCollisionLookAhead : i == 1 ? -c.cameraCollisionLookAhead : 0,
                0,
                i == 2 ? c.cameraCollisionLookAhead : i == 3 ? -c.cameraCollisionLookAhead : 0);
            anticipated = Math.min(anticipated, obstruction(mc, ahead, dx, dy, dz, c.cameraCollisionRadius));
        }
        safe = Math.max(0, hitFraction - (hitFraction < 1 ? .02 / Math.max(1e-6, length) : 0));
        double desired = Math
            .max(0, anticipated - (anticipated < 1 ? c.cameraCollisionPadding / Math.max(1e-6, length) : 0));
        double old = fraction;
        fraction = collisionSpring.step(fraction, desired, dt, c.lockStrongSmoothTime, c.cameraCollisionMaxSpeed);
        // Immediate hard clamp only when required for nonpenetration, e.g. a block appears on the camera.
        fraction = Math.max(0, Math.min(safe, fraction));
        delta = fraction - old;
        cameraX = px + dx * fraction;
        cameraY = py + eye + dy * fraction;
        cameraZ = pz + dz * fraction;
        before.rewind();
        GL11.glLoadMatrix(before);
        GL11.glRotated(pitch, 1, 0, 0);
        GL11.glRotated(mc.thePlayer.rotationYaw + 180, 0, 1, 0);
        GL11.glTranslated(px - cameraX, py - cameraY, pz - cameraZ);
        active = true;
        hidden = SoulsComposition.hideModel(
            fraction < 1,
            hidden,
            distance * fraction,
            c.cameraHidePlayerDistance,
            c.cameraHidePlayerHysteresis);
        if (Boolean.getBoolean("clashweave.trace")) System.out.println(
            "CW_SOULS_FRAME nano=" + now
                + " dt="
                + dt
                + " target="
                + targetId
                + " distance="
                + distance
                + " fov="
                + verticalFov
                + " pitchLimit="
                + pitchLimit
                + " pitch="
                + pitch
                + " fraction="
                + fraction
                + " safe="
                + safe
                + " change="
                + delta
                + " cameraX="
                + cameraX
                + " playerY="
                + mc.thePlayer.posY
                + " footY="
                + mc.thePlayer.boundingBox.minY
                + " eyeRelative="
                + eye
                + " cameraY="
                + cameraY
                + " cameraZ="
                + cameraZ);
    }

    private static double obstruction(Minecraft mc, Vec3 origin, double dx, double dy, double dz, double radius) {
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz), fraction = 1;
        for (int i = 0; i < 9; i++) {
            double ox = i == 8 ? 0 : ((i & 1) == 0 ? -1 : 1) * radius;
            double oy = i == 8 ? 0 : ((i & 2) == 0 ? -1 : 1) * radius;
            double oz = i == 8 ? 0 : ((i & 4) == 0 ? -1 : 1) * radius;
            double x = origin.xCoord + ox, y = origin.yCoord + oy, z = origin.zCoord + oz;
            MovingObjectPosition hit = mc.theWorld
                .rayTraceBlocks(Vec3.createVectorHelper(x, y, z), Vec3.createVectorHelper(x + dx, y + dy, z + dz));
            // World.rayTraceBlocks mutates its start Vec3, so compare with stored scalars.
            if (hit != null) fraction = Math.min(
                fraction,
                Math.sqrt(
                    Math.pow(hit.hitVec.xCoord - x, 2) + Math.pow(hit.hitVec.yCoord - y, 2)
                        + Math.pow(hit.hitVec.zCoord - z, 2))
                    / Math.max(1e-6, length));
        }
        return fraction;
    }

    /** Cancels only local rendering, leaving the authoritative body/action and observers intact. */
    public static final class LocalVisibility {

        @cpw.mods.fml.common.eventhandler.SubscribeEvent
        public void player(net.minecraftforge.client.event.RenderPlayerEvent.Pre event) {
            if (active && hidden && event.entityPlayer == Minecraft.getMinecraft().thePlayer) event.setCanceled(true);
        }
    }
}
