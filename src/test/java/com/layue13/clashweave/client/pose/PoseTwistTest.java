package com.layue13.clashweave.client.pose;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PoseTwistTest {

    @Test
    public void turnsTheUpperBodyOnceAndKeepsLengths() {
        Pose pose = new Pose();
        pose.swordP[0] = 1;
        pose.handR[1] = 2;
        pose.armR[0] = 0;
        pose.armR[1] = 1;
        pose.torsoYaw = 3;
        pose.twistTo(90);
        pose.twistTo(90);
        assertEquals(0, pose.swordP[0], 1e-5);
        assertEquals(1, pose.swordP[1], 1e-5);
        assertEquals(-2, pose.handR[0], 1e-5);
        assertEquals(-1, pose.armR[0], 1e-5);
        assertEquals(93, pose.torsoYaw, 1e-5);
        double length = Math.sqrt(
            pose.swordQ[0] * pose.swordQ[0] + pose.swordQ[1] * pose.swordQ[1]
                + pose.swordQ[2] * pose.swordQ[2]
                + pose.swordQ[3] * pose.swordQ[3]);
        assertEquals(1, length, 1e-5);
        pose.twistTo(0);
        assertEquals(1, pose.swordP[0], 1e-5);
        assertEquals(3, pose.torsoYaw, 1e-5);
    }
}
