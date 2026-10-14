package com.layue13.clashweave.client;

import static org.junit.Assert.*;

import java.lang.management.ManagementFactory;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelEnderman;
import net.minecraft.client.model.ModelSkeleton;
import net.minecraft.client.model.ModelZombie;
import net.minecraft.client.model.ModelZombieVillager;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.Assume;
import org.junit.Test;

public class MobBodiesTest {

    /** A mod's own model that happens to extend a vanilla one keeps its own posing. */
    private static final class ModdedZombie extends ModelZombie {
    }

    @Test
    public void onlyTheThreePlainModelClassesAreTakenOver() {
        assertTrue(MobBodies.accepts(ModelZombie.class));
        assertTrue(MobBodies.accepts(ModelSkeleton.class));
        assertTrue(MobBodies.accepts(ModelBiped.class));
        assertFalse(MobBodies.accepts(ModdedZombie.class));
        assertTrue(MobBodies.accepts(ModelZombieVillager.class));
        assertFalse(MobBodies.accepts(ModelEnderman.class));
        assertFalse(MobBodies.accepts(Object.class));
    }

    private static final class Stub extends Entity {

        Stub() {
            super(null);
        }

        @Override
        protected void entityInit() {}

        @Override
        protected void readEntityFromNBT(NBTTagCompound tag) {}

        @Override
        protected void writeEntityToNBT(NBTTagCompound tag) {}
    }

    private static long allocatedBytes() {
        return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getThreadAllocatedBytes(
            Thread.currentThread()
                .getId());
    }

    @Test
    public void aMonstersFramePosingDoesNotAllocate() {
        Assume.assumeTrue(ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean);
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        Assume.assumeTrue(bean.isThreadAllocatedMemorySupported());
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        MobBiped body = MobBiped.of(new ModelZombie(), true, com.layue13.clashweave.client.body.Gait.ZOMBIE);
        assertNotNull(body);
        Entity entity = new Stub();
        float swing = 0;
        for (int i = 0; i < 20_000; i++) body.setRotationAngles(swing += .3f, .8f, i, 10, 5, 1 / 16f, entity);
        for (int i = 0; i < 20; i++) allocatedBytes();
        long before = allocatedBytes();
        for (int i = 0; i < 1_000_000; i++) body.setRotationAngles(swing += .3f, .8f, i, 10, 5, 1 / 16f, entity);
        long used = allocatedBytes() - before;
        System.out.println("B4_ALLOC MobBiped.setRotationAngles bytesPerMillionCalls=" + used);
        assertTrue("allocated " + used, used >= 0 && used < 64 * 1024);
    }
}
