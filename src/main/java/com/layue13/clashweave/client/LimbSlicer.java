package com.layue13.clashweave.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.PositionTextureVertex;
import net.minecraft.client.model.TexturedQuad;

/**
 * Cuts the single box of a limb part in two at a joint, in place: the part keeps the upper piece, a child renderer
 * hinged at the cut carries the lower one, and a short octagonal prism on the hinge fills the wedge a bend opens on the
 * outside. Works on whatever box the model already has, reading its faces rather than assuming a skin layout, so the
 * same cut serves the 4 px limbs of a zombie, the 2 px limbs of a skeleton and a mirrored left arm: every new face
 * takes
 * its texture coordinates from the original face it was cut from.
 *
 * <p>
 * The idea of slicing a model's own boxes at a joint and re-deriving the texture of each face is how Mo' Bends (MIT)
 * makes its bendable limbs; this is a rewrite for this code base, nothing copied.
 */
final class LimbSlicer {

    /** The pieces added to the sliced part. */
    static final class Joint {

        final ModelRenderer lower, prism;

        Joint(ModelRenderer lower, ModelRenderer prism) {
            this.lower = lower;
            this.prism = prism;
        }
    }

    private static final float EPS = .05f;

    private LimbSlicer() {}

    /** Maps a point on one original face to the texture coordinates that face gives it. */
    private static final class FaceMap {

        final float[] origin = new float[3], edgeA = new float[3], edgeB = new float[3];
        final float[] uv0 = new float[2], uvA = new float[2], uvB = new float[2];

        FaceMap(TexturedQuad q) {
            PositionTextureVertex v0 = q.vertexPositions[0], v1 = q.vertexPositions[1], v3 = q.vertexPositions[3];
            set(origin, v0);
            for (int i = 0; i < 3; i++) {
                edgeA[i] = (i == 0 ? (float) v1.vector3D.xCoord
                    : i == 1 ? (float) v1.vector3D.yCoord : (float) v1.vector3D.zCoord) - origin[i];
                edgeB[i] = (i == 0 ? (float) v3.vector3D.xCoord
                    : i == 1 ? (float) v3.vector3D.yCoord : (float) v3.vector3D.zCoord) - origin[i];
            }
            uv0[0] = v0.texturePositionX;
            uv0[1] = v0.texturePositionY;
            uvA[0] = v1.texturePositionX - uv0[0];
            uvA[1] = v1.texturePositionY - uv0[1];
            uvB[0] = v3.texturePositionX - uv0[0];
            uvB[1] = v3.texturePositionY - uv0[1];
        }

        private static void set(float[] out, PositionTextureVertex v) {
            out[0] = (float) v.vector3D.xCoord;
            out[1] = (float) v.vector3D.yCoord;
            out[2] = (float) v.vector3D.zCoord;
        }

        /** Texture coordinates for the point, projected onto the face's own plane. */
        void at(float x, float y, float z, float[] uv) {
            float dx = x - origin[0], dy = y - origin[1], dz = z - origin[2];
            float la = edgeA[0] * edgeA[0] + edgeA[1] * edgeA[1] + edgeA[2] * edgeA[2];
            float lb = edgeB[0] * edgeB[0] + edgeB[1] * edgeB[1] + edgeB[2] * edgeB[2];
            float s = (dx * edgeA[0] + dy * edgeA[1] + dz * edgeA[2]) / la;
            float r = (dx * edgeB[0] + dy * edgeB[1] + dz * edgeB[2]) / lb;
            uv[0] = uv0[0] + s * uvA[0] + r * uvB[0];
            uv[1] = uv0[1] + s * uvA[1] + r * uvB[1];
        }
    }

    private static Field quadField() {
        for (Field field : ModelBox.class.getDeclaredFields()) if (field.getType() == TexturedQuad[].class) {
            field.setAccessible(true);
            return field;
        }
        throw new IllegalStateException("ModelBox has no quad list");
    }

    private static Field invertField() {
        for (Field field : TexturedQuad.class.getDeclaredFields()) if (field.getType() == boolean.class) {
            field.setAccessible(true);
            return field;
        }
        throw new IllegalStateException("TexturedQuad has no invert flag");
    }

    static TexturedQuad[] quadsOf(ModelBox box) {
        try {
            return (TexturedQuad[]) quadField().get(box);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot reach the faces of a model box", e);
        }
    }

    private static void setQuads(ModelBox box, List<TexturedQuad> quads) {
        try {
            quadField().set(box, quads.toArray(new TexturedQuad[0]));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot replace the faces of a model box", e);
        }
    }

    private static boolean inverted(TexturedQuad quad) {
        try {
            return invertField().getBoolean(quad);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot read a quad's flag", e);
        }
    }

    private static TexturedQuad copyOf(TexturedQuad from, PositionTextureVertex[] vertices) {
        TexturedQuad quad = new TexturedQuad(vertices);
        if (inverted(from)) quad.flipFace();
        return quad;
    }

    private static PositionTextureVertex vertex(float x, float y, float z, float u, float v) {
        return new PositionTextureVertex(x, y, z, u, v);
    }

    /**
     * Cuts {@code part}'s only box at {@code fraction} of its height (0 at the top). Returns null when the part is not
     * a single box tall enough to bend.
     */
    static Joint slice(ModelBase model, ModelRenderer part, float fraction) {
        if (part.cubeList.size() != 1 || part.childModels != null && !part.childModels.isEmpty()) return null;
        ModelBox box = (ModelBox) part.cubeList.get(0);
        float y1 = box.posY1, y2 = box.posY2, x1 = box.posX1, x2 = box.posX2, z1 = box.posZ1, z2 = box.posZ2;
        if (y2 - y1 < 6) return null;
        float cut = y1 + fraction * (y2 - y1);
        float t = (cut - y1) / (y2 - y1);
        TexturedQuad[] original = quadsOf(box);
        FaceMap front = null, back = null, right = null, left = null;
        TexturedQuad top = null, bottom = null;
        List<TexturedQuad> upper = new ArrayList<>(), lower = new ArrayList<>();
        for (TexturedQuad q : original) {
            boolean allTop = true, allBottom = true;
            for (PositionTextureVertex v : q.vertexPositions) {
                if (Math.abs(v.vector3D.yCoord - y1) > EPS) allTop = false;
                if (Math.abs(v.vector3D.yCoord - y2) > EPS) allBottom = false;
            }
            if (allTop) {
                top = q;
                upper.add(q);
                continue;
            }
            if (allBottom) {
                bottom = q;
                PositionTextureVertex[] shifted = new PositionTextureVertex[4];
                for (int i = 0; i < 4; i++) {
                    PositionTextureVertex v = q.vertexPositions[i];
                    shifted[i] = vertex(
                        (float) v.vector3D.xCoord,
                        (float) v.vector3D.yCoord - cut,
                        (float) v.vector3D.zCoord,
                        v.texturePositionX,
                        v.texturePositionY);
                }
                lower.add(copyOf(q, shifted));
                continue;
            }
            // A side face: classify by which coordinate is constant, then cut each vertical edge at the joint.
            double zMin = Double.MAX_VALUE, zMax = -Double.MAX_VALUE, xMin = Double.MAX_VALUE, xMax = -Double.MAX_VALUE;
            for (PositionTextureVertex v : q.vertexPositions) {
                zMin = Math.min(zMin, v.vector3D.zCoord);
                zMax = Math.max(zMax, v.vector3D.zCoord);
                xMin = Math.min(xMin, v.vector3D.xCoord);
                xMax = Math.max(xMax, v.vector3D.xCoord);
            }
            FaceMap map = new FaceMap(q);
            if (zMax - zMin < EPS) {
                if (Math.abs(zMin - z1) < EPS * 2) front = map;
                else back = map;
            } else if (Math.abs(xMin - x1) < EPS * 2) right = map;
            else left = map;
            PositionTextureVertex[] up = new PositionTextureVertex[4], down = new PositionTextureVertex[4];
            for (int i = 0; i < 4; i++) {
                PositionTextureVertex v = q.vertexPositions[i];
                float x = (float) v.vector3D.xCoord, z = (float) v.vector3D.zCoord;
                boolean atTop = Math.abs(v.vector3D.yCoord - y1) < EPS;
                PositionTextureVertex partner = null;
                for (PositionTextureVertex other : q.vertexPositions)
                    if (other != v && Math.abs(other.vector3D.xCoord - x) < EPS
                        && Math.abs(other.vector3D.zCoord - z) < EPS) partner = other;
                if (partner == null) return null;
                float tu = atTop ? v.texturePositionX : partner.texturePositionX;
                float tv = atTop ? v.texturePositionY : partner.texturePositionY;
                float bu = atTop ? partner.texturePositionX : v.texturePositionX;
                float bv = atTop ? partner.texturePositionY : v.texturePositionY;
                float mu = tu + t * (bu - tu), mv = tv + t * (bv - tv);
                up[i] = atTop ? vertex(x, y1, z, v.texturePositionX, v.texturePositionY) : vertex(x, cut, z, mu, mv);
                down[i] = atTop ? vertex(x, 0, z, mu, mv)
                    : vertex(x, (float) v.vector3D.yCoord - cut, z, v.texturePositionX, v.texturePositionY);
            }
            upper.add(copyOf(q, up));
            lower.add(copyOf(q, down));
        }
        if (top == null || bottom == null || front == null || back == null || right == null || left == null)
            return null;
        // Which way ModelBox's vertex order faces, from the box's own top face (outward is -y).
        float sign = Math.signum(-normalY(top)) * (inverted(top) ? -1 : 1);
        float[] uv = new float[2];
        // The faces where the pieces meet: seen only from inside until the joint bends; the limb's own skin at the cut.
        front.at((x1 + x2) / 2, cut, z1, uv);
        float capU = uv[0], capV = uv[1];
        PositionTextureVertex[] capDown = new PositionTextureVertex[4], capUp = new PositionTextureVertex[4];
        for (int i = 0; i < 4; i++) {
            PositionTextureVertex v = bottom.vertexPositions[i];
            capDown[i] = vertex((float) v.vector3D.xCoord, cut, (float) v.vector3D.zCoord, capU, capV);
            capUp[i] = vertex((float) v.vector3D.xCoord, 0, (float) v.vector3D.zCoord, capU, capV);
        }
        upper.add(copyOf(bottom, capDown));
        PositionTextureVertex[] topFace = top.vertexPositions;
        for (int i = 0; i < 4; i++)
            capUp[i] = vertex((float) topFace[i].vector3D.xCoord, 0, (float) topFace[i].vector3D.zCoord, capU, capV);
        lower.add(copyOf(top, capUp));

        setQuads(box, upper);
        ModelRenderer lowerPart = new ModelRenderer(model);
        lowerPart.addBox(0, 0, 0, 1, 1, 1, 0);
        setQuads((ModelBox) lowerPart.cubeList.get(0), lower);
        lowerPart.setRotationPoint(0, cut, 0);

        ModelRenderer prism = prism(
            model,
            cut,
            x1,
            x2,
            (z1 + z2) / 2,
            (z2 - z1) / 2,
            front,
            back,
            right,
            left,
            capU,
            capV,
            sign);
        part.addChild(lowerPart);
        part.addChild(prism);
        return new Joint(lowerPart, prism);
    }

    private static float normalY(TexturedQuad quad) {
        PositionTextureVertex v0 = quad.vertexPositions[0], v1 = quad.vertexPositions[1], v2 = quad.vertexPositions[2];
        double ax = v1.vector3D.xCoord - v0.vector3D.xCoord, az = v1.vector3D.zCoord - v0.vector3D.zCoord;
        double bx = v1.vector3D.xCoord - v2.vector3D.xCoord, bz = v1.vector3D.zCoord - v2.vector3D.zCoord;
        return (float) (az * bx - ax * bz);
    }

    private static float[] normal(float[][] p) {
        float ax = p[1][0] - p[0][0], ay = p[1][1] - p[0][1], az = p[1][2] - p[0][2];
        float bx = p[1][0] - p[2][0], by = p[1][1] - p[2][1], bz = p[1][2] - p[2][2];
        return new float[] { ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx };
    }

    /** The octagonal filler: inradius the limb's half depth, faces textured from the face they continue. */
    private static ModelRenderer prism(ModelBase model, float cut, float x0, float x1, float zc, float half,
        FaceMap front, FaceMap back, FaceMap right, FaceMap left, float capU, float capV, float sign) {
        ModelRenderer part = new ModelRenderer(model);
        part.addBox(0, 0, 0, 1, 1, 1, 0);
        part.setRotationPoint(0, cut, 0);
        float r = half + .01f, a = r * 0.41421356f;
        float[][] ring = { { -r, -a }, { -r, a }, { -a, r }, { a, r }, { r, a }, { r, -a }, { a, -r }, { -a, -r } };
        List<TexturedQuad> quads = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            float[] p = ring[i], q = ring[(i + 1) % 8];
            float my = (p[0] + q[0]) / 2, mz = (p[1] + q[1]) / 2;
            boolean flat = Math.abs(my) > r - .01f;
            float[][] pts = { { x0, p[0], p[1] + zc }, { x1, p[0], p[1] + zc }, { x1, q[0], q[1] + zc },
                { x0, q[0], q[1] + zc } };
            quads.add(skinQuad(pts, 0, my, mz, sign, flat ? null : mz < 0 ? front : back, null, capU, capV, cut));
        }
        float[][][] cuts = { { { -r, -a }, { r, -a }, { r, a }, { -r, a } },
            { { -a, a }, { a, a }, { a, r }, { -a, r } }, { { -a, -r }, { a, -r }, { a, -a }, { -a, -a } } };
        for (float[][] shape : cuts) for (int end = 0; end < 2; end++) {
            float ex = end == 0 ? x0 : x1;
            float[][] pts = new float[4][];
            for (int k = 0; k < 4; k++) pts[k] = new float[] { ex, shape[k][0], shape[k][1] + zc };
            quads.add(skinQuad(pts, end == 0 ? -1 : 1, 0, 0, sign, null, end == 0 ? right : left, capU, capV, cut));
        }
        setQuads((ModelBox) part.cubeList.get(0), quads);
        return part;
    }

    /**
     * One prism face; {@code face} maps a front/back face, {@code sideFace} an end face, neither means the cap patch.
     */
    private static TexturedQuad skinQuad(float[][] p, int side, float my, float mz, float sign, FaceMap face,
        FaceMap sideFace, float capU, float capV, float cut) {
        float ox = side, oy = side == 0 ? my : 0, oz = side == 0 ? mz : 0;
        float[] n = normal(p);
        if ((n[0] * ox + n[1] * oy + n[2] * oz) * sign < 0) {
            float[] swap = p[1];
            p[1] = p[3];
            p[3] = swap;
        }
        PositionTextureVertex[] vs = new PositionTextureVertex[4];
        float[] uv = new float[2];
        FaceMap map = face != null ? face : sideFace;
        for (int i = 0; i < 4; i++) {
            if (map != null) map.at(p[i][0], p[i][1] + cut, p[i][2], uv);
            else {
                uv[0] = capU;
                uv[1] = capV;
            }
            vs[i] = vertex(p[i][0], p[i][1], p[i][2], uv[0], uv[1]);
        }
        return new TexturedQuad(vs);
    }
}
