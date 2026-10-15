package com.layue13.clashweave.client.vfx;

import java.nio.FloatBuffer;

import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * The glow a fast blade leaves behind. While a blade is drawn, the renderer reports where its edge is (in eye space,
 * from the matrix it is drawn with); once per frame the world pass turns that into world positions and keeps the last
 * few moments of them per wielder. A ribbon is drawn between neighbouring samples, fading with age and only where the
 * edge moves quickly relative to its wielder, so a blade held still or carried leaves nothing. Pools are fixed; no
 * allocation after construction.
 */
final class BladeTrails {

    private static final int SLOTS = 16, SAMPLES = 48;
    /** A jump between neighbouring samples longer than this (teleport, respawn) breaks the ribbon. */
    private static final double BREAK = 4;
    /** Where the bright edge band ends, as a share of the way from the tip to the inner end. */
    private static final double EDGE = .3;
    /**
     * The first-person ribbon: how much of the blade it spans from the tip, its life and its strength against the world
     * one.
     */
    private static final double OWN_SPAN = .3, OWN_LIFE = .5, OWN_ALPHA = .45;
    /** Closer to the eye than this, an own-hand ribbon is not drawn. */
    private static final double OWN_NEAR = .7;

    private static final class Slot {

        int entity = -1;
        long touched;
        boolean pending, own;
        float ex0, ey0, ez0, ex1, ey1, ez1; // eye-space base and tip of this frame
        double ox, oy, oz; // wielder position now
        final double[] bx = new double[SAMPLES], by = new double[SAMPLES], bz = new double[SAMPLES];
        final double[] tx = new double[SAMPLES], ty = new double[SAMPLES], tz = new double[SAMPLES];
        final double[] wx = new double[SAMPLES], wy = new double[SAMPLES], wz = new double[SAMPLES];
        final long[] time = new long[SAMPLES];
        int head, size;
    }

    private final Slot[] slots = new Slot[SLOTS];
    private final FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
    private final float[] m = new float[16];
    private EffectDef.Trail def;

    BladeTrails() {
        for (int i = 0; i < SLOTS; i++) slots[i] = new Slot();
    }

    /** Null switches trails off. */
    void use(EffectDef.Trail trail) {
        def = trail;
    }

    private static long now() {
        return System.nanoTime() / 1_000_000L;
    }

    private Slot slot(int entity) {
        Slot oldest = slots[0];
        for (int i = 0; i < SLOTS; i++) {
            Slot s = slots[i];
            if (s.entity == entity) return s;
            if (s.touched < oldest.touched) oldest = s;
        }
        oldest.entity = entity;
        oldest.size = 0;
        oldest.head = 0;
        oldest.pending = false;
        return oldest;
    }

    /** Called while the blade is drawn with its model-view matrix current; {@code x,y,z} is the wielder. */
    void capture(int entity, double x, double y, double z, boolean own) {
        if (def == null || entity < 0) return;
        buffer.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, buffer);
        buffer.get(m);
        Slot s = slot(entity);
        float b = def.baseY, t = def.tipY;
        s.ex0 = m[4] * b + m[12];
        s.ey0 = m[5] * b + m[13];
        s.ez0 = m[6] * b + m[14];
        s.ex1 = m[4] * t + m[12];
        s.ey1 = m[5] * t + m[13];
        s.ez1 = m[6] * t + m[14];
        s.ox = x;
        s.oy = y;
        s.oz = z;
        s.pending = true;
        s.own = own;
        s.touched = now();
    }

    /** Turns this frame's reports into world samples. {@code v} is the world pass's matrix, column-major. */
    void collect(float[] v, double camX, double camY, double camZ) {
        if (def == null) return;
        long t = now();
        for (int i = 0; i < SLOTS; i++) {
            Slot s = slots[i];
            if (s.entity < 0) continue;
            if (!s.pending) {
                if (t - s.touched > 2000) {
                    s.entity = -1;
                    s.size = 0;
                }
                continue;
            }
            s.pending = false;
            int k = (s.head + 1) % SAMPLES;
            s.head = k;
            if (s.size < SAMPLES) s.size++;
            // eye = R * p + v => p = R^T * (eye - v), relative to the camera
            toWorld(v, s.ex0, s.ey0, s.ez0, camX, camY, camZ, s.bx, s.by, s.bz, k);
            toWorld(v, s.ex1, s.ey1, s.ez1, camX, camY, camZ, s.tx, s.ty, s.tz, k);
            s.wx[k] = s.ox;
            s.wy[k] = s.oy;
            s.wz[k] = s.oz;
            s.time[k] = t;
        }
    }

    private static void toWorld(float[] v, float ex, float ey, float ez, double camX, double camY, double camZ,
        double[] ax, double[] ay, double[] az, int k) {
        float dx = ex - v[12], dy = ey - v[13], dz = ez - v[14];
        ax[k] = camX + v[0] * dx + v[1] * dy + v[2] * dz;
        ay[k] = camY + v[4] * dx + v[5] * dy + v[6] * dz;
        az[k] = camZ + v[8] * dx + v[9] * dy + v[10] * dz;
    }

    boolean visible() {
        if (def == null) return false;
        long t = now();
        for (int i = 0; i < SLOTS; i++) {
            Slot s = slots[i];
            if (s.entity >= 0 && s.size > 1 && t - s.time[s.head] < def.life) return true;
        }
        return false;
    }

    /** Draws every ribbon. The caller has set additive blending, no texture, no depth write. */
    void draw(double camX, double camY, double camZ) {
        if (def == null) return;
        long t = now();
        double slow = def.slow, fastSpeed = def.fast;
        Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_QUADS);
        for (int i = 0; i < SLOTS; i++) {
            Slot s = slots[i];
            if (s.entity < 0 || s.size < 2) continue;
            for (int n = 0; n < s.size - 1; n++) {
                int b = ((s.head - n) % SAMPLES + SAMPLES) % SAMPLES; // newer
                int a = ((s.head - n - 1) % SAMPLES + SAMPLES) % SAMPLES; // older
                double life = s.own ? OWN_LIFE * def.life : def.life;
                double age = t - s.time[b];
                if (age >= life) break;
                double dt = Math.max(1, s.time[b] - s.time[a]) / 1000.0;
                double ex = s.tx[b] - s.tx[a], ey = s.ty[b] - s.ty[a], ez = s.tz[b] - s.tz[a];
                if (ex * ex + ey * ey + ez * ez > BREAK * BREAK) break;
                // Edge speed relative to the wielder.
                double mx = ex - (s.wx[b] - s.wx[a]), my = ey - (s.wy[b] - s.wy[a]), mz = ez - (s.wz[b] - s.wz[a]);
                double speed = Math.sqrt(mx * mx + my * my + mz * mz) / dt;
                double fast = Math.min(1, Math.max(0, (speed - slow) / (fastSpeed - slow)));
                if (fast <= 0) continue;
                double fadeNew = 1 - age / life, fadeOld = Math.max(0, 1 - (t - s.time[a]) / life);
                double strength = s.own ? OWN_ALPHA : 1;
                float alpha = (float) (strength * fast * fadeNew * fadeNew),
                    older = (float) (strength * fast * fadeOld * fadeOld);
                double span = s.own ? OWN_SPAN : 1;
                if (s.own) {
                    double nx = s.tx[b] - camX, ny = s.ty[b] - camY, nz = s.tz[b] - camZ;
                    if (nx * nx + ny * ny + nz * nz < OWN_NEAR * OWN_NEAR) continue;
                }
                // The edge is hot and bright, fading through a band to a faint haze at the inner end.
                float mb = (float) EDGE;
                double bxm = s.tx[b] + (s.bx[b] - s.tx[b]) * span * mb, bym = s.ty[b] + (s.by[b] - s.ty[b]) * span * mb,
                    bzm = s.tz[b] + (s.bz[b] - s.tz[b]) * span * mb;
                double axm = s.tx[a] + (s.bx[a] - s.tx[a]) * span * mb, aym = s.ty[a] + (s.by[a] - s.ty[a]) * span * mb,
                    azm = s.tz[a] + (s.bz[a] - s.tz[a]) * span * mb;
                float mid = .45f;
                tess.setColorRGBA_F(1f, 1f, 1f, older * def.tipAlpha);
                tess.addVertex(s.tx[a] - camX, s.ty[a] - camY, s.tz[a] - camZ);
                tess.setColorRGBA_F(1f, 1f, 1f, alpha * def.tipAlpha);
                tess.addVertex(s.tx[b] - camX, s.ty[b] - camY, s.tz[b] - camZ);
                tess.setColorRGBA_F(def.tip[0], def.tip[1], def.tip[2], alpha * def.tipAlpha * mid);
                tess.addVertex(bxm - camX, bym - camY, bzm - camZ);
                tess.setColorRGBA_F(def.tip[0], def.tip[1], def.tip[2], older * def.tipAlpha * mid);
                tess.addVertex(axm - camX, aym - camY, azm - camZ);
                tess.setColorRGBA_F(def.tip[0], def.tip[1], def.tip[2], older * def.tipAlpha * mid);
                tess.addVertex(axm - camX, aym - camY, azm - camZ);
                tess.setColorRGBA_F(def.tip[0], def.tip[1], def.tip[2], alpha * def.tipAlpha * mid);
                tess.addVertex(bxm - camX, bym - camY, bzm - camZ);
                tess.setColorRGBA_F(def.base[0], def.base[1], def.base[2], alpha * def.baseAlpha);
                tess.addVertex(
                    s.tx[b] + (s.bx[b] - s.tx[b]) * span - camX,
                    s.ty[b] + (s.by[b] - s.ty[b]) * span - camY,
                    s.tz[b] + (s.bz[b] - s.tz[b]) * span - camZ);
                tess.setColorRGBA_F(def.base[0], def.base[1], def.base[2], older * def.baseAlpha);
                tess.addVertex(
                    s.tx[a] + (s.bx[a] - s.tx[a]) * span - camX,
                    s.ty[a] + (s.by[a] - s.ty[a]) * span - camY,
                    s.tz[a] + (s.bz[a] - s.tz[a]) * span - camZ);
            }
        }
        tess.draw();
    }
}
