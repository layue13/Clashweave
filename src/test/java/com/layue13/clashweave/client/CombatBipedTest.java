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
}
