package com.layue13.clashweave.client.pose;

import static com.layue13.clashweave.client.pose.TrackDataTest.*;
import static org.junit.Assert.*;

import org.junit.Test;

import com.google.gson.JsonObject;

public class TrackSamplingTest {

    @Test
    public void endpointsMidpointAndClamping() {
        JsonObject tree = fixture();
        samples(tree).get(1)
            .getAsJsonObject()
            .getAsJsonObject("sword")
            .add("p", vector(2, 4, 6));
        samples(tree).get(1)
            .getAsJsonObject()
            .addProperty("torsoYaw", 30);
        Track track = track(tree);
        Pose out = new Pose();
        for (double time : new double[] { -100, Double.NEGATIVE_INFINITY, 0 }) {
            track.sample(time, out);
            assertArrayEquals(new float[3], out.swordP, 0);
        }
        track.sample(1, out);
        assertArrayEquals(new float[] { 1, 2, 3 }, out.swordP, 0);
        assertEquals(15, out.torsoYaw, 0);
        for (double time : new double[] { 2, 100, Double.POSITIVE_INFINITY }) {
            track.sample(time, out);
            assertArrayEquals(new float[] { 2, 4, 6 }, out.swordP, 0);
        }
    }

    @Test
    public void shortestQuaternionArcAndSphericalMidpoint() {
        JsonObject tree = fixture();
        samples(tree).get(1)
            .getAsJsonObject()
            .getAsJsonObject("sword")
            .add("q", vector(0, 0, 0, -1));
        Pose out = new Pose();
        track(tree).sample(1, out);
        assertArrayEquals(new float[] { 0, 0, 0, 1 }, out.swordQ, 0);
        samples(tree).get(1)
            .getAsJsonObject()
            .getAsJsonObject("sword")
            .add("q", vector(0, 0, -Math.sqrt(.5), -Math.sqrt(.5)));
        track(tree).sample(1, out);
        assertArrayEquals(
            new float[] { 0, 0, (float) Math.sin(Math.PI / 8), (float) Math.cos(Math.PI / 8) },
            out.swordQ,
            1e-6f);
    }

    @Test
    public void booleansSwitchOnlyAtNextSample() {
        JsonObject tree = fixture();
        samples(tree).get(1)
            .getAsJsonObject()
            .addProperty("bladeDrawn", true);
        samples(tree).get(1)
            .getAsJsonObject()
            .getAsJsonObject("handL")
            .addProperty("mode", "free");
        Track track = track(tree);
        Pose out = new Pose();
        for (double time : new double[] { 0, .5, 1, 1.999999999 }) {
            track.sample(time, out);
            assertFalse(out.bladeDrawn);
            assertFalse(out.handLFree);
        }
        track.sample(2, out);
        assertTrue(out.bladeDrawn);
        assertTrue(out.handLFree);
    }

    @Test
    public void directionsRemainUnitIncludingAntipodalCancellation() throws Exception {
        JsonObject tree = fixture();
        samples(tree).get(1)
            .getAsJsonObject()
            .getAsJsonObject("baked")
            .add("armR", vector(1, 0, 0));
        Pose out = new Pose();
        track(tree).sample(1, out);
        assertArrayEquals(new float[] { (float) Math.sqrt(.5), 0, (float) -Math.sqrt(.5) }, out.armR, 1e-6f);
        samples(tree).get(1)
            .getAsJsonObject()
            .getAsJsonObject("baked")
            .add("armR", vector(0, 0, 1));
        track(tree).sample(1, out);
        assertArrayEquals(new float[] { 0, 0, 1 }, out.armR, 0);
        TrackData data = realData();
        for (String id : data.actionIds()) {
            Track track = data.track(id);
            for (double time = 0; time <= track.duration(); time += .125) {
                track.sample(time, out);
                for (float[] dir : new float[][] { out.armR, out.armL, out.legR, out.legL })
                    assertEquals(1, Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]), 1e-6);
            }
        }
    }

    @Test
    public void everyRealSampleReproducesStoredPositionsAndFlags() throws Exception {
        TrackData data = realData();
        Pose out = new Pose();
        for (String id : data.actionIds()) {
            for (com.google.gson.JsonElement element : realTree().getAsJsonObject("actions")
                .getAsJsonObject(id)
                .getAsJsonArray("samples")) {
                JsonObject sample = element.getAsJsonObject();
                data.track(id)
                    .sample(
                        sample.get("t")
                            .getAsDouble(),
                        out);
                float[][] arrays = { out.swordP, out.handR, out.handL, out.pelvis, out.footR, out.footL };
                com.google.gson.JsonArray[] raw = { sample.getAsJsonObject("sword")
                    .getAsJsonArray("p"), sample.getAsJsonArray("handR"),
                    sample.getAsJsonObject("handL")
                        .getAsJsonArray("p"),
                    sample.getAsJsonArray("pelvis"), sample.getAsJsonArray("footR"), sample.getAsJsonArray("footL") };
                for (int i = 0; i < arrays.length; i++) for (int j = 0; j < arrays[i].length; j++) assertEquals(
                    raw[i].get(j)
                        .getAsFloat(),
                    arrays[i][j],
                    0);
                assertEquals(
                    sample.get("torsoYaw")
                        .getAsFloat(),
                    out.torsoYaw,
                    0);
                assertEquals(
                    sample.get("bladeDrawn")
                        .getAsBoolean(),
                    out.bladeDrawn);
                assertEquals(
                    sample.getAsJsonObject("handL")
                        .get("mode")
                        .getAsString()
                        .equals("free"),
                    out.handLFree);
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void nanTimeIsRejected() {
        track(fixture()).sample(Double.NaN, new Pose());
    }
}
