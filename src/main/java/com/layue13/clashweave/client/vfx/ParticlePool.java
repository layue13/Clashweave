package com.layue13.clashweave.client.vfx;

import java.util.Random;

/**
 * Every live particle of every effect, in parallel arrays of fixed size: spawning writes into a slot, ticking moves and
 * ages them, and a full pool recycles slots in a ring instead of growing or failing. No object is created after
 * construction.
 */
public final class ParticlePool {

    public static final int CAPACITY = 512;

    public final double[] x = new double[CAPACITY], y = new double[CAPACITY], z = new double[CAPACITY];
    public final double[] px = new double[CAPACITY], py = new double[CAPACITY], pz = new double[CAPACITY];
    public final float[] vx = new float[CAPACITY], vy = new float[CAPACITY], vz = new float[CAPACITY];
    public final float[] size = new float[CAPACITY], age = new float[CAPACITY], life = new float[CAPACITY];
    public final EffectDef.Emitter[] emitter = new EffectDef.Emitter[CAPACITY];
    public int count;
    private int cursor;

    private int slot() {
        if (count < CAPACITY) return count++;
        cursor = (cursor + 1) % CAPACITY;
        return cursor;
    }

    private static float between(float[] range, Random random) {
        return range[0] + (range[1] - range[0]) * random.nextFloat();
    }

    /** Throws one effect from an anchor. {@code (dx,dy,dz)} is the direction it leaves in (need not be unit length). */
    public void emit(EffectDef def, double ax, double ay, double az, float dx, float dy, float dz, Random random) {
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-6f) {
            dx = 0;
            dy = 1;
            dz = 0;
        } else {
            dx /= length;
            dy /= length;
            dz /= length;
        }
        // Two axes across the direction, for sampling the cone.
        float hx = Math.abs(dy) > .9f ? 1 : 0, hy = Math.abs(dy) > .9f ? 0 : 1;
        float ux = -dz * hy, uy = dz * hx, uz = dx * hy - dy * hx;
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul;
        uy /= ul;
        uz /= ul;
        float wx = dy * uz - dz * uy, wy = dz * ux - dx * uz, wz = dx * uy - dy * ux;
        for (int e = 0; e < def.emitters.size(); e++) {
            EffectDef.Emitter em = def.emitters.get(e);
            float sg = em.dirSign;
            float cosMax = (float) Math.cos(Math.toRadians(em.spread));
            for (int n = 0; n < em.count; n++) {
                int i = slot();
                float cos = 1 - random.nextFloat() * (1 - cosMax);
                float sin = (float) Math.sqrt(Math.max(0, 1 - cos * cos));
                float phi = random.nextFloat() * (float) (Math.PI * 2);
                float cu = (float) Math.cos(phi) * sin, cw = (float) Math.sin(phi) * sin;
                float sx = sg * dx * cos + ux * cu + wx * cw, sy = sg * dy * cos + uy * cu + wy * cw,
                    sz = sg * dz * cos + uz * cu + wz * cw;
                float speed = em.kind == EffectDef.Kind.STREAK ? between(em.speed, random) : 0;
                double ox = em.scatter == 0 ? 0 : (random.nextFloat() * 2 - 1) * em.scatter,
                    oy = em.scatter == 0 ? 0 : (random.nextFloat() * 2 - 1) * em.scatter,
                    oz = em.scatter == 0 ? 0 : (random.nextFloat() * 2 - 1) * em.scatter;
                x[i] = px[i] = ax + dx * em.forward + ox;
                y[i] = py[i] = ay + dy * em.forward + oy;
                z[i] = pz[i] = az + dz * em.forward + oz;
                // A line does not move; its velocity slots hold the way it points.
                boolean line = em.kind == EffectDef.Kind.LINE;
                vx[i] = line ? sx : sx * speed;
                vy[i] = line ? sy : sy * speed;
                vz[i] = line ? sz : sz * speed;
                size[i] = between(em.size, random);
                age[i] = 0;
                life[i] = Math.max(1, Math.round(between(em.life, random)));
                emitter[i] = em;
            }
        }
    }

    /** One game tick. */
    public void tick() {
        for (int i = 0; i < count;) {
            age[i]++;
            if (age[i] >= life[i]) {
                kill(i);
                continue;
            }
            px[i] = x[i];
            py[i] = y[i];
            pz[i] = z[i];
            EffectDef.Emitter em = emitter[i];
            if (em.kind == EffectDef.Kind.STREAK) {
                vy[i] -= em.gravity;
                vx[i] *= em.drag;
                vy[i] *= em.drag;
                vz[i] *= em.drag;
                x[i] += vx[i];
                y[i] += vy[i];
                z[i] += vz[i];
            }
            i++;
        }
    }

    private void kill(int i) {
        int last = --count;
        if (i != last) {
            x[i] = x[last];
            y[i] = y[last];
            z[i] = z[last];
            px[i] = px[last];
            py[i] = py[last];
            pz[i] = pz[last];
            vx[i] = vx[last];
            vy[i] = vy[last];
            vz[i] = vz[last];
            size[i] = size[last];
            age[i] = age[last];
            life[i] = life[last];
            emitter[i] = emitter[last];
        }
        emitter[last] = null;
    }
}
