package com.layue13.clashweave.forge;

import static org.junit.Assert.*;

import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.After;
import org.junit.Assume;
import org.junit.Test;

import com.layue13.clashweave.content.ContentLoader;
import com.layue13.clashweave.content.EffectiveRules;
import com.layue13.clashweave.core.ActionCatalog;
import com.layue13.clashweave.core.Intent;
import com.layue13.clashweave.core.Scheduler;

/**
 * The lookups a tick or a frame makes must not allocate. Measured with the JVM's per-thread allocation counter over
 * a million calls each; a single stray String or boxed value per call would show as megabytes.
 */
public class HotPathAllocationTest {

    private static final int CALLS = 1_000_000;

    @After
    public void clear() {
        CombatWeapons.install(null);
    }

    private static long allocatedBytes() {
        return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getThreadAllocatedBytes(
            Thread.currentThread()
                .getId());
    }

    private static long perMillion(Runnable work) {
        for (int i = 0; i < 20_000; i++) work.run();
        long before = allocatedBytes();
        for (int i = 0; i < CALLS; i++) work.run();
        long used = allocatedBytes() - before;
        System.out.println("WF1C_ALLOC bytesPerMillionCalls=" + used);
        return used;
    }

    @Test
    public void tableCatalogAndSchedulerLookupsDoNotAllocate() throws Exception {
        Assume.assumeTrue(
            ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean
                && ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean())
                    .isThreadAllocatedMemorySupported());
        EffectiveRules rules = EffectiveRules.build(
            ContentLoader.load(getClass().getClassLoader(), null).registry,
            new EffectiveRules.Overrides(true, true, 1.2, false),
            null);
        Item sword = new Item();
        Map<String, Item> items = new HashMap<>();
        items.put("clashweave:katana", sword);
        CombatWeapons.install(
            CombatWeapons
                .build(Collections.singletonList("clashweave:katana=katana:katana"), rules, .5, 2, items::get));
        ItemStack held = new ItemStack(sword);
        ActionCatalog catalog = rules.catalog("katana:default");
        long slack = 64 * 1024; // thread bookkeeping and the measurement itself

        // Server tick and every frame: is this stack a weapon, and which one.
        assertTrue(perMillion(() -> assertNotNull(CombatWeapons.entry(held))) < slack);
        assertTrue(perMillion(() -> CombatWeapons.armed(held)) < slack);
        // Client prediction, rendering and scheduler: qualified-ID lookups.
        assertTrue(perMillion(() -> catalog.byId("katana:light_1")) < slack);
        assertTrue(perMillion(() -> catalog.require("katana:light_1")) < slack);
        assertTrue(perMillion(() -> catalog.entryTarget(true, Intent.LIGHT)) < slack);
        assertTrue(perMillion(() -> catalog.entersSheathed("katana:sheathe")) < slack);
        assertTrue(perMillion(() -> catalog.normalize("katana:light_1")) < slack);
        // The scheduler's per-tick work with nothing buffered, idle and mid-action.
        Scheduler scheduler = new Scheduler(rules.weapon("katana:katana"), catalog);
        long[] tick = { 0 };
        assertTrue(perMillion(() -> scheduler.tick(tick[0]++, true)) < slack);
    }

    @Test
    public void shortIdCompletionIsTheOnlyPathThatBuildsAString() {
        EffectiveRules rules;
        try {
            rules = EffectiveRules.build(
                ContentLoader.load(getClass().getClassLoader(), null).registry,
                new EffectiveRules.Overrides(true, true, 1.2, false),
                null);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        ActionCatalog catalog = rules.catalog("katana:default");
        assertSame("a qualified ID is returned as is", "katana:light_1", catalog.normalize("katana:light_1"));
        assertEquals("katana:light_1", catalog.normalize("light_1"));
        assertNull("byId never completes a short ID", catalog.byId("light_1"));
    }
}
