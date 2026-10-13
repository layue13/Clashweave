package com.layue13.clashweave.client.pose;

import static org.junit.Assert.*;

import java.lang.management.ManagementFactory;

import org.junit.Assume;
import org.junit.Test;

public class PoseAllocationTest {

    private static final int CALLS = 1_000_000;
    private static volatile float sink;

    private static long allocatedBytes() {
        return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getThreadAllocatedBytes(
            Thread.currentThread()
                .getId());
    }

    private static void perMillion(String name, Runnable work) {
        for (int i = 0; i < 20_000; i++) work.run();
        // Warm the management counter too, before measuring the first hot path.
        for (int i = 0; i < 20; i++) allocatedBytes();
        long before = allocatedBytes();
        for (int i = 0; i < CALLS; i++) work.run();
        long used = allocatedBytes() - before;
        System.out.println("C2A_ALLOC " + name + " bytesPerMillionCalls=" + used);
        long slack = 64 * 1024; // Same JVM bookkeeping allowance as HotPathAllocationTest.
        assertTrue(name + " allocated " + used, used >= 0 && used < slack);
    }

    @Test
    public void allPoseHotPathsDoNotAllocate() throws Exception {
        Assume.assumeTrue(ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean);
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        Assume.assumeTrue(bean.isThreadAllocatedMemorySupported());
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        // Warm the measurement loop with an observable allocation-free control.
        perMillion("counter baseline", () -> sink = 0);
        Track track = TrackDataTest.realData()
            .track("light_1");
        Pose a = new Pose(), b = new Pose(), out = new Pose();
        track.sample(0, a);
        track.sample(5, b);
        float[] angles = new float[3], matrix = new float[16], shoulder = new float[3];
        double[] time = { 0 };
        perMillion("Track.sample", () -> {
            time[0] = (time[0] + .125) % track.duration();
            track.sample(time[0], out);
            sink = out.swordP[0];
        });
        perMillion("LimbSolver.solveLimb", () -> {
            LimbSolver.solveLimb(b.armR[0], b.armR[1], b.armR[2], angles);
            sink = angles[0];
        });
        perMillion("LimbSolver.swordMatrix", () -> {
            LimbSolver.swordMatrix(b, matrix);
            sink = matrix[0];
        });
        perMillion("LimbSolver.shoulderPoint", () -> {
            LimbSolver.shoulderPoint(true, b.torsoYaw, b.pelvis, shoulder);
            sink = shoulder[0];
        });
        perMillion("Pose.blend", () -> {
            Pose.blend(a, b, .4f, out);
            sink = out.swordP[0];
        });
        PoseBlend blend = new PoseBlend();
        blend.start(a, Integer.MAX_VALUE);
        perMillion("PoseBlend.update", () -> {
            blend.update(b, .125, out);
            sink = out.swordP[0];
        });
        assertTrue(blend.active());
    }

    @Test
    public void segmentedHotPathsDoNotAllocate() throws Exception {
        Assume.assumeTrue(ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean);
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        Assume.assumeTrue(bean.isThreadAllocatedMemorySupported());
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        perMillion("counter baseline", () -> sink = 0);
        Pose pose = new Pose();
        TrackDataTest.realData()
            .track("light_1")
            .sample(5, pose);
        LimbAngles out = new LimbAngles();
        float[] pivot = new float[3], target = { .1f, .5f, -.1f }, pole = { 0, 1, .2f };
        float[] upper = new float[3], lower = new float[3], angles = new float[3], end = new float[3];
        perMillion("TwoBoneIk.solve", () -> {
            TwoBoneIk.solve(pivot, target, pole, .375f, .375f, upper, lower);
            sink = upper[0] + lower[2];
        });
        perMillion("LimbSolver.solveHinged", () -> {
            LimbSolver.solveHinged(upper, pole, angles);
            sink = angles[0];
        });
        perMillion("LimbSolver.solveArm", () -> {
            LimbSolver.solveArm(true, pose, out);
            sink = out.upper[0] + out.childAngleX;
        });
        perMillion("LimbSolver.solveLeg", () -> {
            LimbSolver.solveLeg(true, pose, out);
            sink = out.upper[0] + out.childAngleX;
        });
        perMillion("LimbSolver.endPoint", () -> {
            LimbSolver.endPoint(pivot, angles, -.7f, end);
            sink = end[0];
        });
    }
}
