package com.layue13.clashweave.client;

import static org.junit.Assert.*;

import net.minecraft.client.model.ModelRenderer;

import org.junit.Test;

import com.layue13.clashweave.client.pose.Pose;
import com.layue13.clashweave.client.pose.TrackData;

/** One model draws every player: a posed player must not leave its pivots to the next one. */
public class CombatBipedTest {

    @Test
    public void anUnposedPlayerDrawnAfterAPosedOneGetsTheVanillaPivots() throws Exception {
        CombatBiped fresh = new CombatBiped(0), model = new CombatBiped(0);
        fresh.setRotationAngles(0, 0, 0, 0, 0, 1 / 16f, null);

        Pose pose = new Pose();
        // Mid-lunge: pelvis forward and down, torso twisted.
        try (java.io.Reader reader = new java.io.InputStreamReader(
            CombatBipedTest.class.getResourceAsStream("/assets/clashweave/tracks/katana.tracks.json"),
            java.nio.charset.StandardCharsets.UTF_8)) {
            TrackData.parse(reader)
                .track("iai")
                .sample(8, pose);
        }
        assertNotEquals(0, pose.pelvis[1], 0);
        model.pose = pose;
        model.weight = 1;
        model.setRotationAngles(0, 0, 0, 0, 0, 1 / 16f, null);
        assertNotEquals(fresh.bipedBody.rotationPointZ, model.bipedBody.rotationPointZ, 1e-3f);

        model.weight = 0;
        model.setRotationAngles(0, 0, 0, 0, 0, 1 / 16f, null);
        ModelRenderer[][] parts = { { fresh.bipedHead, model.bipedHead }, { fresh.bipedHeadwear, model.bipedHeadwear },
            { fresh.bipedBody, model.bipedBody }, { fresh.bipedRightArm, model.bipedRightArm },
            { fresh.bipedLeftArm, model.bipedLeftArm }, { fresh.bipedRightLeg, model.bipedRightLeg },
            { fresh.bipedLeftLeg, model.bipedLeftLeg } };
        for (ModelRenderer[] pair : parts) {
            assertEquals(pair[0].rotationPointX, pair[1].rotationPointX, 0);
            assertEquals(pair[0].rotationPointY, pair[1].rotationPointY, 0);
            assertEquals(pair[0].rotationPointZ, pair[1].rotationPointZ, 0);
            assertEquals(pair[0].rotateAngleX, pair[1].rotateAngleX, 0);
            assertEquals(pair[0].rotateAngleY, pair[1].rotateAngleY, 0);
            assertEquals(pair[0].rotateAngleZ, pair[1].rotateAngleZ, 0);
        }
    }

    private static net.minecraft.client.model.TexturedQuad[] quads(ModelRenderer part) throws Exception {
        net.minecraft.client.model.ModelBox box = (net.minecraft.client.model.ModelBox) part.cubeList.get(0);
        for (java.lang.reflect.Field field : net.minecraft.client.model.ModelBox.class.getDeclaredFields()) {
            if (field.getType() == net.minecraft.client.model.TexturedQuad[].class) {
                field.setAccessible(true);
                return (net.minecraft.client.model.TexturedQuad[]) field.get(box);
            }
        }
        throw new AssertionError("no quad list");
    }

    /** The faces that meet at a joint show skin colour from the limb's own rows; every other face keeps its region. */
    @Test
    public void jointFacesSampleTheLimbSkinAndNoOtherFaceChanges() throws Exception {
        CombatBiped biped = new CombatBiped(0);
        ModelRenderer upper = biped.bipedRightArm;
        ModelRenderer forearm = (ModelRenderer) upper.childModels.get(0);
        int capped = 0, untouched = 0;
        for (net.minecraft.client.model.TexturedQuad quad : quads(upper)) {
            double maxY = Double.NEGATIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
            for (net.minecraft.client.model.PositionTextureVertex vertex : quad.vertexPositions) {
                maxY = Math.max(maxY, vertex.vector3D.yCoord);
                minY = Math.min(minY, vertex.vector3D.yCoord);
            }
            boolean joint = minY == maxY && maxY == ((net.minecraft.client.model.ModelBox) upper.cubeList.get(0)).posY2;
            for (net.minecraft.client.model.PositionTextureVertex vertex : quad.vertexPositions) {
                boolean patch = vertex.texturePositionX == 46 / 64f && vertex.texturePositionY == 25.5f / 32f;
                assertEquals("quad at y=" + maxY, joint, patch);
            }
            if (joint) capped++;
            else untouched++;
        }
        assertEquals(1, capped);
        assertEquals(5, untouched);
        int forearmCapped = 0;
        for (net.minecraft.client.model.TexturedQuad quad : quads(forearm))
            if (quad.vertexPositions[0].texturePositionX == 46 / 64f
                && quad.vertexPositions[0].texturePositionY == 28.5f / 32f) forearmCapped++;
        assertEquals(1, forearmCapped);
    }
}
