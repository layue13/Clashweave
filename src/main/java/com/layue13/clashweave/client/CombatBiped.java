package com.layue13.clashweave.client;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;

import com.layue13.clashweave.client.body.Channels;
import com.layue13.clashweave.client.pose.LimbAngles;
import com.layue13.clashweave.client.pose.LimbSolver;
import com.layue13.clashweave.client.pose.Pose;

/**
 * The player model with the baked combat pose laid over vanilla's own angles. Vanilla still runs first, so head look
 * and
 * everything the pose leaves alone stay as they were; {@link #weight} fades the pose in and out. The parts of
 * ModelBiped are independent (arms do not follow the torso), so every pivot is set from the pose here.
 *
 * <p>
 * The skin model (expand 0) has arms and legs in two segments: an upper box and a forearm/shin child hinged at the
 * elbow/knee, the 64x32 skin cut across its rows so the texture still maps. A limb whose armor slot is occupied stays
 * one
 * rigid block ({@link #rigidArms}, {@link #rigidLegs}) so skin and armor never disagree about where the joint is. Armor
 * models (expand above 0) are always rigid; they follow the pose because this class is their model.
 */
final class CombatBiped extends ModelBiped {

    /** Pixels from the shoulder pivot to the elbow, and from the hip pivot to the knee. */
    private static final int ELBOW = 5, KNEE = 6;

    /** Pose and weight for the next render; written by the renderer immediately before it draws this model. */
    Pose pose;
    float weight;
    /** Set by the renderer from the worn armor: that limb pair is drawn as one straight block. */
    boolean rigidArms, rigidLegs;
    /** The entity's body rig and this frame's locomotion inputs; the renderer fills them before drawing. */
    com.layue13.clashweave.client.body.BodyRig rig;
    final com.layue13.clashweave.client.body.LocomotionInputs inputs = new com.layue13.clashweave.client.body.LocomotionInputs();
    boolean locomotion;
    long frameNanos, tickPeriod;

    private final boolean segmented;
    private ModelRenderer rightForearm, leftForearm, rightShin, leftShin;
    /** Fills the wedge the two segments open on the outside of a bend: a cube on the hinge turned half the bend. */
    private ModelRenderer rightElbow, leftElbow, rightKnee, leftKnee;
    private final LimbAngles solved = new LimbAngles();
    private final float[] angles = new float[3];
    private final float[] point = new float[3];

    CombatBiped(float expand) {
        super(expand);
        segmented = expand == 0;
        if (segmented) {
            // ModelBiped(float) uses the 64x32 skin layout: right arm (40,16), left arm mirrored, legs (0,16).
            bipedRightArm = arm(false);
            rightForearm = (ModelRenderer) bipedRightArm.childModels.get(0);
            bipedLeftArm = arm(true);
            leftForearm = (ModelRenderer) bipedLeftArm.childModels.get(0);
            bipedRightLeg = leg(false);
            rightShin = (ModelRenderer) bipedRightLeg.childModels.get(0);
            bipedLeftLeg = leg(true);
            leftShin = (ModelRenderer) bipedLeftLeg.childModels.get(0);
            rightElbow = (ModelRenderer) bipedRightArm.childModels.get(1);
            leftElbow = (ModelRenderer) bipedLeftArm.childModels.get(1);
            rightKnee = (ModelRenderer) bipedRightLeg.childModels.get(1);
            leftKnee = (ModelRenderer) bipedLeftLeg.childModels.get(1);
        }
    }

    /** Upper arm from -2 to ELBOW px below the pivot (including the shoulder cap), forearm below it. */
    private ModelRenderer arm(boolean left) {
        ModelRenderer upper = new ModelRenderer(this, 40, 16);
        upper.mirror = left;
        upper.addBox(left ? -1 : -3, -2, -2, 4, ELBOW + 2, 4, 0);
        upper.setRotationPoint(left ? 5 : -5, 2, 0);
        // The forearm's side faces start where the upper's end: row 16 + 4 + (ELBOW + 2) of the skin.
        ModelRenderer lower = new ModelRenderer(this, 40, 16 + ELBOW + 2);
        lower.mirror = left;
        lower.addBox(left ? -1 : -3, 0, -2, 4, 10 - ELBOW, 4, 0);
        lower.setRotationPoint(0, ELBOW, 0);
        upper.addChild(lower);
        // Skin colour on the faces that meet at the elbow, from the rows either side of the cut on the arm's front.
        cap(upper, true, 46, 25.5f);
        cap(lower, false, 46, 28.5f);
        shiftEnd(lower, ELBOW + 2);
        upper.addChild(joint(left, 40, 16 + 4 + ELBOW + 2, left ? -1 : -3, ELBOW, 46, 25.5f));
        return upper;
    }

    /**
     * The faces where the two segments meet are inside the box until the joint bends, then the outside of the bend
     * shows
     * them; their texture region is the wrong part of the skin (a dark wedge). Point them at a patch of the limb's own
     * skin.
     * The quads are found by position, the lowest or highest face in Y, not by the order ModelBox happens to build them
     * in.
     */
    /**
     * The piece that fills the wedge a bend opens on its outside: an octagonal prism (inradius 2 px, the limb's own
     * half-thickness) on the hinge, turned half the bend by {@link #syncJoints}. A cube would do the same but its
     * corners stick out of the limb by almost a pixel at 90 degrees, which reads as a swollen joint. Textured from the
     * rows either side of the cut ({@code cutRow}) of the limb's skin at {@code u0}.
     */
    private ModelRenderer joint(boolean mirror, int u0, int cutRow, int x, int hingeY, float capU, float capV) {
        ModelRenderer part = new ModelRenderer(this, u0, 0);
        part.addBox(x, -2, -2, 4, 4, 4, 0);
        part.setRotationPoint(0, hingeY, 0);
        net.minecraft.client.model.ModelBox box = (net.minecraft.client.model.ModelBox) part.cubeList.get(0);
        java.lang.reflect.Field field = quadField();
        net.minecraft.client.model.TexturedQuad[] reference = quadsOf(box);
        // Which way ModelBox's vertex order faces: the sign of (v1-v0)x(v1-v2) against the outward direction.
        float sign = 0;
        for (net.minecraft.client.model.TexturedQuad quad : reference) {
            boolean top = true;
            for (net.minecraft.client.model.PositionTextureVertex vertex : quad.vertexPositions)
                if (Math.abs(vertex.vector3D.yCoord - box.posY1) > .05f) top = false;
            if (top) sign = Math.signum(-faceNormalY(quad));
        }
        java.util.List<net.minecraft.client.model.TexturedQuad> quads = new java.util.ArrayList<>();
        final float r = 2.01f, a = r * 0.41421356f, x0 = x, x1 = x + 4;
        float[][] ring = { { -r, -a }, { -r, a }, { -a, r }, { a, r }, { r, a }, { r, -a }, { a, -r }, { -a, -r } };
        for (int i = 0; i < 8; i++) {
            float[] p = ring[i], q = ring[(i + 1) % 8];
            float mz = (p[1] + q[1]) / 2, my = (p[0] + q[0]) / 2;
            boolean flat = Math.abs(my) > r - .01f;
            float[][] pts = { { x0, p[0], p[1] }, { x1, p[0], p[1] }, { x1, q[0], q[1] }, { x0, q[0], q[1] } };
            quads.add(skinQuad(pts, 0, my, mz, sign, mirror, u0, cutRow, flat ? capU : -1, capV));
        }
        // The two flat ends (the limb's sides): the octagon as a rectangle and two caps, each end.
        float[][][] cuts = { { { -r, -a }, { r, -a }, { r, a }, { -r, a } },
            { { -a, a }, { a, a }, { a, r }, { -a, r } }, { { -a, -r }, { a, -r }, { a, -a }, { -a, -a } } };
        for (float[][] cut : cuts) {
            for (int end = 0; end < 2; end++) {
                float ex = end == 0 ? x0 : x1;
                float[][] pts = new float[4][];
                for (int k = 0; k < 4; k++) pts[k] = new float[] { ex, cut[k][0], cut[k][1] };
                quads.add(skinQuad(pts, end == 0 ? -1 : 1, 0, 0, sign, mirror, u0, cutRow, -1, capV));
            }
        }
        try {
            field.set(box, quads.toArray(new net.minecraft.client.model.TexturedQuad[0]));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot replace the faces of a model box", e);
        }
        return part;
    }

    /** y component of the normal ModelBox's quads carry, (v1-v0)x(v1-v2). */
    private static float faceNormalY(net.minecraft.client.model.TexturedQuad quad) {
        net.minecraft.util.Vec3 v0 = quad.vertexPositions[0].vector3D, v1 = quad.vertexPositions[1].vector3D,
            v2 = quad.vertexPositions[2].vector3D;
        double ax = v1.xCoord - v0.xCoord, az = v1.zCoord - v0.zCoord, bx = v1.xCoord - v2.xCoord,
            bz = v1.zCoord - v2.zCoord;
        return (float) (az * bx - ax * bz);
    }

    private static float[] normal(float[][] p) {
        float ax = p[1][0] - p[0][0], ay = p[1][1] - p[0][1], az = p[1][2] - p[0][2];
        float bx = p[1][0] - p[2][0], by = p[1][1] - p[2][1], bz = p[1][2] - p[2][2];
        return new float[] { ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx };
    }

    /**
     * One face of the joint prism. {@code side} is -1/+1 for an end face at the limb's -x/+x side; otherwise (my, mz)
     * is the outward direction in the Y-Z plane. Texture: front/back faces from the limb's front/back columns, side
     * ends from its side columns, the flat top and bottom (inside the limb) from the cap patch.
     */
    private static net.minecraft.client.model.TexturedQuad skinQuad(float[][] p, int side, float my, float mz,
        float sign, boolean mirror, int u0, int cutRow, float capU, float capV) {
        float ox = side, oy = side == 0 ? my : 0, oz = side == 0 ? mz : 0;
        float[] n = normal(p);
        if ((n[0] * ox + n[1] * oy + n[2] * oz) * sign < 0) {
            float[] t = p[1];
            p[1] = p[3];
            p[3] = t;
        }
        net.minecraft.client.model.PositionTextureVertex[] vs = new net.minecraft.client.model.PositionTextureVertex[4];
        for (int i = 0; i < 4; i++) {
            float x = p[i][0], y = p[i][1], z = p[i][2], u, v = (cutRow + y) / 32f;
            // fx 0..1 across the limb's width, flipped on the left limb whose skin is a mirror image.
            float fx = (x - (side == 0 ? Math.min(p[0][0], p[1][0]) : x)) / 4f;
            if (side == 0) {
                float lo = Math.min(Math.min(p[0][0], p[1][0]), Math.min(p[2][0], p[3][0]));
                fx = (x - lo) / 4f;
            }
            if (side != 0) {
                boolean outer = (side < 0) != mirror;
                u = outer ? u0 + (2 - z) : u0 + 8 + (z + 2);
            } else if (capU >= 0 && Math.abs(my) > 1) {
                u = capU + fx;
                v = capV / 32f;
            } else if (mz < 0) {
                u = u0 + 4 + 4 * (mirror ? fx : 1 - fx);
            } else {
                u = u0 + 12 + 4 * (mirror ? 1 - fx : fx);
            }
            vs[i] = new net.minecraft.client.model.PositionTextureVertex(x, y, z, u / 64f, v);
        }
        return new net.minecraft.client.model.TexturedQuad(vs);
    }

    private static java.lang.reflect.Field quadField() {
        for (java.lang.reflect.Field field : net.minecraft.client.model.ModelBox.class.getDeclaredFields())
            if (field.getType() == net.minecraft.client.model.TexturedQuad[].class) {
                field.setAccessible(true);
                return field;
            }
        throw new IllegalStateException("ModelBox has no quad list");
    }

    private static net.minecraft.client.model.TexturedQuad[] quadsOf(net.minecraft.client.model.ModelBox box) {
        try {
            return (net.minecraft.client.model.TexturedQuad[]) quadField().get(box);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot reach the faces of a model box", e);
        }
    }

    private void syncJoints() {
        rightElbow.rotateAngleX = rightForearm.rotateAngleX * .5f;
        leftElbow.rotateAngleX = leftForearm.rotateAngleX * .5f;
        rightKnee.rotateAngleX = rightShin.rotateAngleX * .5f;
        leftKnee.rotateAngleX = leftShin.rotateAngleX * .5f;
        boolean show = !rigidArms;
        rightElbow.showModel = leftElbow.showModel = show;
        rightKnee.showModel = leftKnee.showModel = !rigidLegs;
    }

    static void cap(ModelRenderer part, boolean bottom, float u, float v) {
        for (net.minecraft.client.model.TexturedQuad quad : edgeQuads(part, bottom))
            for (net.minecraft.client.model.PositionTextureVertex vertex : quad.vertexPositions) {
                vertex.texturePositionX = u / 64f;
                vertex.texturePositionY = v / 32f;
            }
    }

    /**
     * The end face of a lower segment samples the skin as if the segment started at the limb's top: move it back by
     * the height of the part above so the palm and the sole show their own pixels (Mo' Bends does the same with
     * offsetTextureQuad(BOTTOM, 0, -cut)).
     */
    static void shiftEnd(ModelRenderer part, float rows) {
        for (net.minecraft.client.model.TexturedQuad quad : edgeQuads(part, true))
            for (net.minecraft.client.model.PositionTextureVertex vertex : quad.vertexPositions)
                vertex.texturePositionY -= rows / 32f;
    }

    private static java.util.List<net.minecraft.client.model.TexturedQuad> edgeQuads(ModelRenderer part,
        boolean bottom) {
        java.util.List<net.minecraft.client.model.TexturedQuad> found = new java.util.ArrayList<>();
        for (Object cube : part.cubeList) {
            net.minecraft.client.model.ModelBox box = (net.minecraft.client.model.ModelBox) cube;
            net.minecraft.client.model.TexturedQuad[] quads = null;
            for (java.lang.reflect.Field field : net.minecraft.client.model.ModelBox.class.getDeclaredFields()) {
                if (field.getType() == net.minecraft.client.model.TexturedQuad[].class) {
                    try {
                        field.setAccessible(true);
                        quads = (net.minecraft.client.model.TexturedQuad[]) field.get(box);
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException("cannot reach the faces of a model box", e);
                    }
                }
            }
            if (quads == null) throw new IllegalStateException("ModelBox has no quad list");
            float edge = bottom ? box.posY2 : box.posY1;
            for (net.minecraft.client.model.TexturedQuad quad : quads) {
                boolean onEdge = true;
                for (net.minecraft.client.model.PositionTextureVertex vertex : quad.vertexPositions)
                    if (Math.abs(vertex.vector3D.yCoord - edge) > .05f) onEdge = false;
                if (onEdge) found.add(quad);
            }
        }
        return found;
    }

    private ModelRenderer leg(boolean left) {
        ModelRenderer upper = new ModelRenderer(this, 0, 16);
        upper.mirror = left;
        upper.addBox(-2, 0, -2, 4, KNEE, 4, 0);
        upper.setRotationPoint(left ? 1.9f : -1.9f, 12, 0);
        ModelRenderer lower = new ModelRenderer(this, 0, 16 + KNEE);
        lower.mirror = left;
        lower.addBox(-2, 0, -2, 4, 12 - KNEE, 4, 0);
        lower.setRotationPoint(0, KNEE, 0);
        upper.addChild(lower);
        cap(upper, true, 6, 24.5f);
        cap(lower, false, 6, 27.5f);
        shiftEnd(lower, KNEE);
        upper.addChild(joint(left, 0, 16 + 4 + KNEE, -2, KNEE, 6, 25.5f));
        return upper;
    }

    @Override
    public void setRotationAngles(float limb, float amount, float age, float headYaw, float pitch, float scale,
        Entity entity) {
        pose(limb, amount, age, headYaw, pitch, scale, entity);
        if (segmented) syncJoints();
    }

    private void pose(float limb, float amount, float age, float headYaw, float pitch, float scale, Entity entity) {
        restore();
        super.setRotationAngles(limb, amount, age, headYaw, pitch, scale, entity);
        if (segmented) {
            rightForearm.rotateAngleX = 0;
            leftForearm.rotateAngleX = 0;
            rightShin.rotateAngleX = 0;
            leftShin.rotateAngleX = 0;
        }
        if (rig != null) {
            if (rig.needsStep()) {
                readVanilla(rig.target());
                if (locomotion) com.layue13.clashweave.client.body.Locomotion.compute(inputs, rig.target());
                rig.chase(frameNanos, tickPeriod);
            }
            writeBase(rig.smoothed());
        }
        if (weight <= 0 || pose == null) return;
        float w = weight;
        // The parts are independent: every pivot follows the pelvis, the shoulders also follow the torso twist.
        LimbSolver.shoulderPoint(true, pose, point);
        pivot(bipedRightArm, point, w);
        arm(bipedRightArm, rightForearm, true, pose.armR, w);
        LimbSolver.shoulderPoint(false, pose, point);
        pivot(bipedLeftArm, point, w);
        arm(bipedLeftArm, leftForearm, false, pose.armL, w);
        LimbSolver.hipPoint(true, pose.pelvis, point);
        pivot(bipedRightLeg, point, w);
        leg(bipedRightLeg, rightShin, true, pose.legR, w);
        LimbSolver.hipPoint(false, pose.pelvis, point);
        pivot(bipedLeftLeg, point, w);
        leg(bipedLeftLeg, leftShin, false, pose.legL, w);
        // Body box, head and headwear hang from the neck, which the torso carries about the waist.
        LimbSolver.neckPoint(pose, point);
        pivot(bipedBody, point, w);
        pivot(bipedHead, point, w);
        pivot(bipedHeadwear, point, w);
        LimbSolver.torsoAngles(pose, angles);
        apply(bipedBody, angles, w);
    }

    /**
     * One model draws every player. Vanilla's setRotationAngles never resets the head, headwear and body pivots, the
     * arm
     * pivot Y, the leg pivot X or the leg Z angle (only the constructor sets them), and the pose moves exactly those,
     * so
     * without this the next player drawn inherits the previous one's pelvis and twist: torso and limbs come apart.
     */
    private void restore() {
        bipedHead.setRotationPoint(0, 0, 0);
        bipedHeadwear.setRotationPoint(0, 0, 0);
        bipedBody.setRotationPoint(0, 0, 0);
        bipedBody.rotateAngleZ = 0;
        bipedRightArm.setRotationPoint(-5, 2, 0);
        bipedLeftArm.setRotationPoint(5, 2, 0);
        bipedRightLeg.setRotationPoint(-1.9f, 12, 0);
        bipedLeftLeg.setRotationPoint(1.9f, 12, 0);
        bipedRightLeg.rotateAngleZ = 0;
        bipedLeftLeg.rotateAngleZ = 0;
    }

    private void readVanilla(float[] t) {
        t[Channels.HEAD_X] = bipedHead.rotateAngleX;
        t[Channels.HEAD_Y] = bipedHead.rotateAngleY;
        t[Channels.BODY_X] = bipedBody.rotateAngleX;
        t[Channels.BODY_Y] = bipedBody.rotateAngleY;
        t[Channels.BODY_Z] = bipedBody.rotateAngleZ;
        t[Channels.R_ARM_X] = bipedRightArm.rotateAngleX;
        t[Channels.R_ARM_Y] = bipedRightArm.rotateAngleY;
        t[Channels.R_ARM_Z] = bipedRightArm.rotateAngleZ;
        t[Channels.L_ARM_X] = bipedLeftArm.rotateAngleX;
        t[Channels.L_ARM_Y] = bipedLeftArm.rotateAngleY;
        t[Channels.L_ARM_Z] = bipedLeftArm.rotateAngleZ;
        t[Channels.R_LEG_X] = bipedRightLeg.rotateAngleX;
        t[Channels.R_LEG_Y] = bipedRightLeg.rotateAngleY;
        t[Channels.R_LEG_Z] = bipedRightLeg.rotateAngleZ;
        t[Channels.L_LEG_X] = bipedLeftLeg.rotateAngleX;
        t[Channels.L_LEG_Y] = bipedLeftLeg.rotateAngleY;
        t[Channels.L_LEG_Z] = bipedLeftLeg.rotateAngleZ;
        t[Channels.R_FORE] = t[Channels.L_FORE] = t[Channels.R_SHIN] = t[Channels.L_SHIN] = 0;
        t[Channels.ROOT_Y] = 0;
    }

    /** The smoothed base pose: vanilla's, or the locomotion layer's where it took over, chased frame by frame. */
    private void writeBase(float[] c) {
        bipedHead.rotateAngleX = c[Channels.HEAD_X];
        bipedHead.rotateAngleY = c[Channels.HEAD_Y];
        bipedHeadwear.rotateAngleX = bipedHead.rotateAngleX;
        bipedHeadwear.rotateAngleY = bipedHead.rotateAngleY;
        bipedBody.rotateAngleX = c[Channels.BODY_X];
        bipedBody.rotateAngleY = c[Channels.BODY_Y];
        bipedBody.rotateAngleZ = c[Channels.BODY_Z];
        bipedRightArm.rotateAngleX = c[Channels.R_ARM_X];
        bipedRightArm.rotateAngleY = c[Channels.R_ARM_Y];
        bipedRightArm.rotateAngleZ = c[Channels.R_ARM_Z];
        bipedLeftArm.rotateAngleX = c[Channels.L_ARM_X];
        bipedLeftArm.rotateAngleY = c[Channels.L_ARM_Y];
        bipedLeftArm.rotateAngleZ = c[Channels.L_ARM_Z];
        bipedRightLeg.rotateAngleX = c[Channels.R_LEG_X];
        bipedRightLeg.rotateAngleY = c[Channels.R_LEG_Y];
        bipedRightLeg.rotateAngleZ = c[Channels.R_LEG_Z];
        bipedLeftLeg.rotateAngleX = c[Channels.L_LEG_X];
        bipedLeftLeg.rotateAngleY = c[Channels.L_LEG_Y];
        bipedLeftLeg.rotateAngleZ = c[Channels.L_LEG_Z];
        if (segmented) {
            // Elbows bend the forearm forward (-X), knees bend the shin back (+X); a worn piece keeps the limb
            // straight.
            if (!rigidArms) {
                rightForearm.rotateAngleX = -c[Channels.R_FORE];
                leftForearm.rotateAngleX = -c[Channels.L_FORE];
            }
            if (!rigidLegs) {
                rightShin.rotateAngleX = c[Channels.R_SHIN];
                leftShin.rotateAngleX = c[Channels.L_SHIN];
            }
        }
        float bob = c[Channels.ROOT_Y];
        bipedBody.rotationPointY += bob;
        bipedHead.rotationPointY += bob;
        bipedHeadwear.rotationPointY += bob;
        bipedRightArm.rotationPointY += bob;
        bipedLeftArm.rotationPointY += bob;
    }

    private void arm(ModelRenderer upper, ModelRenderer forearm, boolean right, float[] rigidDirection, float w) {
        if (segmented && !rigidArms) {
            LimbSolver.solveArm(right, pose, solved);
            apply(upper, solved.upper, w);
            forearm.rotateAngleX = mix(forearm.rotateAngleX, solved.childAngleX, w);
        } else {
            LimbSolver.solveLimb(rigidDirection[0], rigidDirection[1], rigidDirection[2], angles);
            apply(upper, angles, w);
        }
    }

    private void leg(ModelRenderer upper, ModelRenderer shin, boolean right, float[] rigidDirection, float w) {
        if (segmented && !rigidLegs) {
            LimbSolver.solveLeg(right, pose, solved);
            apply(upper, solved.upper, w);
            shin.rotateAngleX = mix(shin.rotateAngleX, solved.childAngleX, w);
        } else {
            LimbSolver.solveLimb(rigidDirection[0], rigidDirection[1], rigidDirection[2], angles);
            apply(upper, angles, w);
        }
    }

    private static void apply(ModelRenderer part, float[] euler, float w) {
        part.rotateAngleX = mix(part.rotateAngleX, euler[0], w);
        part.rotateAngleY = mix(part.rotateAngleY, euler[1], w);
        part.rotateAngleZ = mix(part.rotateAngleZ, euler[2], w);
    }

    private static void pivot(ModelRenderer part, float[] target, float w) {
        part.rotationPointX = part.rotationPointX + (target[0] - part.rotationPointX) * w;
        part.rotationPointY = part.rotationPointY + (target[1] - part.rotationPointY) * w;
        part.rotationPointZ = part.rotationPointZ + (target[2] - part.rotationPointZ) * w;
    }

    /** Along the shorter way around, so a fade never spins a limb through a full turn. */
    private static float mix(float from, float to, float w) {
        float delta = (float) (to - from - Math.PI * 2 * Math.floor((to - from + Math.PI) / (Math.PI * 2)));
        return from + delta * w;
    }
}
