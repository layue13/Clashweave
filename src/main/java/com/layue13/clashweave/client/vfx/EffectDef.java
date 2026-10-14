package com.layue13.clashweave.client.vfx;

import java.util.ArrayList;
import java.util.List;

/**
 * One effect as data: a few emitters that each throw particles of one look, or the shared blade-trail description. Read
 * from {@code assets/clashweave/vfx/effects.json}; {@link #validate()} rejects what the renderer cannot draw.
 */
public final class EffectDef {

    public enum Kind {
        /** A hot streak stretched along its velocity. */
        STREAK,
        /** A soft disc that blooms and fades. */
        DISC,
        /** A thin ring that spreads. */
        RING,
        /** A still line of the given length in a random direction, bright in the middle and clear at the ends. */
        LINE
    }

    public enum Facing {
        /** Turned to the viewer. */
        CAMERA,
        /** Lying flat on the ground (discs and rings only). */
        GROUND
    }

    public static final class Emitter {

        public Kind kind = Kind.STREAK;
        public int count = 1;
        /** Blocks per tick along the effect direction. */
        public float[] speed = { 0.1f, 0.2f };
        /** Half angle of the cone the particles leave in, degrees; 180 is every direction. */
        public float spread = 25;
        public float[] life = { 8, 14 };
        public float[] size = { 0.04f, 0.06f };
        public float gravity = 0.03f, drag = 0.9f;
        public float[] color = { 1f, 0.7f, 0.25f };
        /** Distance along the direction from the anchor at which the particles start. */
        public float forward;
        public float[] alpha = { 1f, 0f };
        public float[] scale = { 1f, 1f };
        public Facing facing = Facing.CAMERA;
        /** -1 throws the particles the opposite way along the effect direction. */
        public float dirSign = 1;
        /** Each particle starts at a random point within this distance of the anchor. */
        public float scatter;
        /** LINE only: how thick the line is, in blocks (its length is {@code size}). */
        public float width = 0.03f;

        transient Curve alphaCurve, scaleCurve;

        public Curve alphaCurve() {
            return alphaCurve;
        }

        public Curve scaleCurve() {
            return scaleCurve;
        }
    }

    public static final class Trail {

        /** Milliseconds a trail sample lives. */
        public float life = 230;
        /** Edge speed relative to the wielder, blocks per second: nothing below {@code slow}, full at {@code fast}. */
        public float slow = 5, fast = 12;
        public float[] tip = { 0.8f, 0.93f, 1f }, base = { 0.25f, 0.5f, 1f };
        public float tipAlpha = 0.95f, baseAlpha = 0.12f;
        /** The edge along the blade's own axis, in the units the model is drawn in. */
        public float baseY = 0.5f, tipY = 1.2f;
    }

    public String id;
    public List<Emitter> emitters = new ArrayList<>();
    /** Present only on the trail definition. */
    public Trail trail;

    /** Throws {@link IllegalArgumentException} naming the first problem. */
    public void validate() {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("effect without an id");
        if (trail != null) {
            if (!(trail.life >= 20 && trail.life <= 2000))
                throw new IllegalArgumentException(id + ": trail.life 20-2000 ms");
            if (!(trail.slow >= 0 && trail.fast > trail.slow))
                throw new IllegalArgumentException(id + ": trail speeds need 0 <= slow < fast");
            if (trail.tip == null || trail.tip.length != 3 || trail.base == null || trail.base.length != 3)
                throw new IllegalArgumentException(id + ": trail colours are [r,g,b]");
            if (!(trail.tipY > trail.baseY)) throw new IllegalArgumentException(id + ": trail.tipY must exceed baseY");
        } else if (emitters.isEmpty()) throw new IllegalArgumentException(id + ": no emitters");
        for (int i = 0; i < emitters.size(); i++) {
            Emitter e = emitters.get(i);
            String where = id + ".emitters[" + i + "]";
            if (e == null || e.kind == null) throw new IllegalArgumentException(where + ": missing kind");
            if (e.facing == null || !(e.dirSign == 1 || e.dirSign == -1))
                throw new IllegalArgumentException(where + ": facing and dirSign (1 or -1)");
            if (!(e.scatter >= 0 && e.scatter <= 4)) throw new IllegalArgumentException(where + ".scatter 0-4");
            if (!(e.width >= 0.002f && e.width <= 1)) throw new IllegalArgumentException(where + ".width 0.002-1");
            if (e.count < 1 || e.count > 64) throw new IllegalArgumentException(where + ".count 1-64");
            range(e.speed, 0, 2, where + ".speed");
            range(e.life, 1, 200, where + ".life");
            range(e.size, 0.001f, 8, where + ".size");
            if (!(e.spread >= 0 && e.spread <= 180)) throw new IllegalArgumentException(where + ".spread 0-180");
            if (!(e.drag > 0 && e.drag <= 1)) throw new IllegalArgumentException(where + ".drag (0,1]");
            if (!(e.gravity >= -1 && e.gravity <= 1)) throw new IllegalArgumentException(where + ".gravity -1..1");
            if (e.color == null || e.color.length != 3) throw new IllegalArgumentException(where + ".color is [r,g,b]");
            if (e.alpha == null || e.alpha.length == 0 || e.scale == null || e.scale.length == 0)
                throw new IllegalArgumentException(where + ": alpha and scale need at least one value");
            e.alphaCurve = new Curve(e.alpha);
            e.scaleCurve = new Curve(e.scale);
        }
    }

    private static void range(float[] r, float min, float max, String where) {
        if (r == null || r.length != 2 || !(r[0] >= min && r[1] >= r[0] && r[1] <= max))
            throw new IllegalArgumentException(where + " is [low, high] within " + min + ".." + max);
    }
}
