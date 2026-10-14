package com.layue13.clashweave.client.vfx;

import java.nio.FloatBuffer;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.layue13.clashweave.Clashweave;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * The effect layer's client side: owns the particle pool and the trails, plays effects by handle, and draws everything
 * in one untextured additive pass after the world. Presentation only: nothing here reaches rules or the network.
 * {@code -Dcw.vfx.off=true} switches the layer off, and callers then keep using vanilla particles.
 */
public final class Vfx {

    public static final boolean ENABLED = !Boolean.getBoolean("cw.vfx.off");
    private static final boolean DEBUG = Boolean.getBoolean("cw.vfx.debug");
    public static final Vfx INSTANCE = new Vfx();

    private final ParticlePool pool = new ParticlePool();
    private final Random random = new Random();
    private final BladeTrails trails = new BladeTrails();
    private EffectLibrary library = new EffectLibrary();
    private final FloatBuffer viewBuffer = BufferUtils.createFloatBuffer(16);
    private final float[] view = new float[16];

    private Vfx() {}

    /** Reads the data and subscribes to the events it needs. Call once from the client proxy. */
    public static void register() {
        if (!ENABLED) return;
        INSTANCE.library = EffectLibrary.load();
        for (String error : INSTANCE.library.errors())
            if (Clashweave.log != null) Clashweave.log.warn("vfx: {}", error);
        INSTANCE.trails.use(INSTANCE.library.trail());
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        FMLCommonHandler.instance()
            .bus()
            .register(INSTANCE);
    }

    public EffectLibrary library() {
        return library;
    }

    public ParticlePool pool() {
        return pool;
    }

    /** Throws the effect from the anchor in the direction; does nothing for an unknown handle. */
    public void play(int handle, double x, double y, double z, float dx, float dy, float dz) {
        if (!ENABLED || handle < 0 || handle >= library.size()) return;
        pool.emit(library.get(handle), x, y, z, dx, dy, dz, random);
        if (DEBUG) System.out.println(
            "VFX_PLAY handle=" + handle
                + " at="
                + x
                + ","
                + y
                + ","
                + z
                + " dir="
                + dx
                + ","
                + dy
                + ","
                + dz
                + " live="
                + pool.count);
    }

    /** The effect bound to a semantic event kind name, or -1 (also when the layer is off). */
    public int bound(String eventKind) {
        return ENABLED ? library.forEvent(eventKind) : -1;
    }

    /**
     * Reports the blade drawn right now: the current model-view matrix places it. The wielder's position is used to
     * tell the blade's own motion from its carrier's.
     */
    public void reportBlade(int entity, double x, double y, double z) {
        if (ENABLED) trails.capture(entity, x, y, z);
    }

    private static final int FOLLOWERS = 32;
    private final int[] followEntity = new int[FOLLOWERS], followEffect = new int[FOLLOWERS],
        followLeft = new int[FOLLOWERS], followEvery = new int[FOLLOWERS], followPhase = new int[FOLLOWERS];
    private final float[] followHeight = new float[FOLLOWERS];
    private int followers;
    private final java.util.Map<Class<?>, String> classNames = new java.util.HashMap<>();

    private String className(Object entity) {
        Class<?> type = entity.getClass();
        String name = classNames.get(type);
        if (name == null) {
            name = type.getSimpleName();
            classNames.put(type, name);
        }
        return name;
    }

    /**
     * Plays what the data binds to this event kind for the entity it happened to (or at, per the binding anchor), and
     * starts a following effect if the binding asks for one. Returns whether anything was bound.
     */
    public boolean playEvent(String kind, Entity entity) {
        if (!ENABLED || entity == null) return false;
        EffectLibrary.Binding binding = library.resolve(kind, className(entity));
        if (binding == null) return false;
        if (DEBUG)
            System.out.println("VFX_EVENT kind=" + kind + " entity=" + className(entity) + " effect=" + binding.effect);
        double x = entity.posX, y = entity.boundingBox.minY + entity.height * binding.height, z = entity.posZ;
        float dx = 0, dy = 1, dz = 0;
        if ("side".equals(binding.dir)) {
            // Across the line of sight, so a slash always reads as a slash on screen.
            Entity viewer = Minecraft.getMinecraft().renderViewEntity;
            double vx = viewer == null ? 0 : entity.posX - viewer.posX,
                vz = viewer == null ? 1 : entity.posZ - viewer.posZ;
            double length = Math.sqrt(vx * vx + vz * vz);
            if (length > 1e-4) {
                dx = (float) (-vz / length);
                dy = 0;
                dz = (float) (vx / length);
            } else {
                dx = 1;
                dy = 0;
            }
        }
        play(binding.effect(), x, y, z, dx, dy, dz);
        if (binding.followEffect() >= 0 && followers < FOLLOWERS) {
            int i = followers++;
            followEntity[i] = entity.getEntityId();
            followEffect[i] = binding.followEffect();
            followLeft[i] = binding.followDuration;
            followEvery[i] = binding.followInterval;
            followPhase[i] = 0;
            followHeight[i] = binding.height;
        }
        return true;
    }

    /** Plays a bound effect for an entity every {@code interval} of its own ticks (an aura while something holds). */
    public void ambient(String kind, Entity entity, int interval) {
        if (ENABLED && entity != null && entity.ticksExisted % interval == 0) playEvent(kind, entity);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        pool.tick();
        Minecraft minecraft = Minecraft.getMinecraft();
        for (int i = 0; i < followers;) {
            Entity entity = minecraft.theWorld == null ? null : minecraft.theWorld.getEntityByID(followEntity[i]);
            if (entity == null || entity.isDead || --followLeft[i] < 0) {
                int last = --followers;
                followEntity[i] = followEntity[last];
                followEffect[i] = followEffect[last];
                followLeft[i] = followLeft[last];
                followEvery[i] = followEvery[last];
                followPhase[i] = followPhase[last];
                followHeight[i] = followHeight[last];
                continue;
            }
            if (++followPhase[i] >= followEvery[i]) {
                followPhase[i] = 0;
                play(
                    followEffect[i],
                    entity.posX,
                    entity.boundingBox.minY + entity.height * followHeight[i],
                    entity.posZ,
                    0,
                    1,
                    0);
            }
            i++;
        }
    }

    @SubscribeEvent
    public void world(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.theWorld == null) return;
        viewBuffer.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, viewBuffer);
        viewBuffer.get(view);
        double cx = RenderManager.renderPosX, cy = RenderManager.renderPosY, cz = RenderManager.renderPosZ;
        trails.collect(view, cx, cy, cz);
        if (pool.count == 0 && !trails.visible()) return;
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_LIGHTING_BIT);
        // The world pass can leave flat shading on, which would colour a whole polygon with its last vertex.
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDepthMask(false);
        // Sparks and flashes are bright colours blended over the scene, so they read against a daylit sky as well.
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        drawParticles(event.partialTicks, cx, cy, cz);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        trails.draw(cx, cy, cz);
        GL11.glPopAttrib();
    }

    private void drawParticles(float partial, double cx, double cy, double cz) {
        ParticlePool p = pool;
        // The camera's axes in the world are the rows of the view matrix's rotation, whatever entity the view follows.
        float rx = view[0], ry = view[4], rz = view[8];
        float ux = view[1], uy = view[5], uz = view[9];
        float lx = -view[2], ly = -view[6], lz = -view[10];
        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_QUADS);
        for (int i = 0; i < p.count; i++) {
            EffectDef.Emitter em = p.emitter[i];
            if (em.kind != EffectDef.Kind.STREAK) continue;
            float f = (p.age[i] + partial) / p.life[i];
            float a = em.alphaCurve()
                .at(f);
            double hx = p.px[i] + (p.x[i] - p.px[i]) * partial - cx, hy = p.py[i] + (p.y[i] - p.py[i]) * partial - cy,
                hz = p.pz[i] + (p.z[i] - p.pz[i]) * partial - cz;
            float sx = p.vx[i], sy = p.vy[i], sz = p.vz[i];
            float speed = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (speed < 1e-4f) continue;
            float length = Math.min(.9f, .06f + speed * 2.6f);
            float dx = sx / speed, dy = sy / speed, dz = sz / speed;
            // Sideways: across both the streak and the view.
            float wx = dy * lz - dz * ly, wy = dz * lx - dx * lz, wz = dx * ly - dy * lx;
            float wl = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
            if (wl < 1e-5f) continue;
            float w = p.size[i] * .5f / wl;
            wx *= w;
            wy *= w;
            wz *= w;
            double tx = hx - dx * length, ty = hy - dy * length, tz = hz - dz * length;
            // Hot white at the head cooling to the emitter colour along the tail.
            t.setColorRGBA_F(1f, 1f, 1f, a);
            t.addVertex(hx - wx * .6, hy - wy * .6, hz - wz * .6);
            t.addVertex(hx + wx * .6, hy + wy * .6, hz + wz * .6);
            t.setColorRGBA_F(em.color[0], em.color[1], em.color[2], 0f);
            t.addVertex(tx + wx * .15, ty + wy * .15, tz + wz * .15);
            t.addVertex(tx - wx * .15, ty - wy * .15, tz - wz * .15);
        }
        t.draw();
        for (int i = 0; i < p.count; i++) {
            EffectDef.Emitter em = p.emitter[i];
            if (em.kind == EffectDef.Kind.STREAK) continue;
            float f = (p.age[i] + partial) / p.life[i];
            float a = em.alphaCurve()
                .at(f);
            float radius = p.size[i] * em.scaleCurve()
                .at(f);
            double ox = p.x[i] - cx, oy = p.y[i] - cy, oz = p.z[i] - cz;
            // A disc is a ring with no hole: bright at the centre, clear at the rim.
            boolean disc = em.kind == EffectDef.Kind.DISC;
            float inner = disc ? 0 : radius * .86f;
            t.startDrawing(GL11.GL_QUAD_STRIP);
            for (int k = 0; k <= 20; k++) {
                double angle = k * Math.PI / 10;
                float c = (float) Math.cos(angle), sn = (float) Math.sin(angle);
                float cx2, cy2, cz2;
                if (em.facing == EffectDef.Facing.GROUND) {
                    cx2 = c;
                    cy2 = 0;
                    cz2 = sn;
                } else {
                    cx2 = rx * c + ux * sn;
                    cy2 = ry * c + uy * sn;
                    cz2 = rz * c + uz * sn;
                }
                if (disc) t.setColorRGBA_F(1f, 1f, 1f, a);
                else t.setColorRGBA_F(em.color[0], em.color[1], em.color[2], 0f);
                t.addVertex(ox + cx2 * inner, oy + cy2 * inner, oz + cz2 * inner);
                if (disc) t.setColorRGBA_F(em.color[0], em.color[1], em.color[2], 0f);
                else t.setColorRGBA_F(1f, 1f, 1f, a);
                t.addVertex(ox + cx2 * radius, oy + cy2 * radius, oz + cz2 * radius);
            }
            t.draw();
        }
    }
}
