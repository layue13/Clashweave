package com.layue13.clashweave.client;

import static org.junit.Assert.*;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.layue13.clashweave.content.ContentLoader;
import com.layue13.clashweave.content.EffectiveRules;
import com.layue13.clashweave.forge.CombatWeapons;

public class WeaponRenderingTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    private final Item local = new Item(), remote = new Item(), unknown = new Item();
    private EffectiveRules rules;

    @Before
    public void bind() throws Exception {
        File packs = temp.newFolder();
        try (InputStream stream = getClass().getResourceAsStream("/wf1test/glaive.json")) {
            Files.copy(stream, new File(packs, "glaive.json").toPath());
        }
        rules = EffectiveRules.build(
            ContentLoader.load(getClass().getClassLoader(), packs).registry,
            new EffectiveRules.Overrides(true, true, 1.2, false),
            null);
        Map<String, Item> items = new HashMap<>();
        items.put("test:local", local);
        items.put("test:remote", remote);
        CombatWeapons.install(
            CombatWeapons.build(
                Arrays.asList("test:local=katana:katana", "test:remote=wf1test:glaive"),
                rules,
                .5,
                2,
                items::get));
    }

    @After
    public void clear() {
        CombatWeapons.install(null);
    }

    @Test
    public void fallbackUsesTheRenderedEntityWeaponWithoutLocalPlayerState() {
        assertEquals("katana:default", WeaponRendering.resourceFor(new ItemStack(local)));
        assertEquals("wf1test:glaive", WeaponRendering.resourceFor(new ItemStack(remote)));
        assertEquals("katana:default", WeaponRendering.resourceFor(new ItemStack(local)));
    }

    @Test
    public void unknownAndEmptyStacksHaveNoResourceBinding() {
        assertEquals("", WeaponRendering.resourceFor(null));
        assertEquals("", WeaponRendering.resourceFor(new ItemStack(unknown)));
        ItemStack empty = new ItemStack(remote);
        empty.stackSize = 0;
        assertEquals("", WeaponRendering.resourceFor(empty));
    }

    @Test
    public void fallbackResourceLookupDoesNotAllocatePerFrame() {
        java.lang.management.ThreadMXBean general = java.lang.management.ManagementFactory.getThreadMXBean();
        org.junit.Assume.assumeTrue(general instanceof com.sun.management.ThreadMXBean);
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) general;
        org.junit.Assume.assumeTrue(bean.isThreadAllocatedMemorySupported());
        ItemStack held = new ItemStack(remote);
        for (int i = 0; i < 20_000; i++) WeaponRendering.resourceFor(held);
        long thread = Thread.currentThread()
            .getId(), before = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 1_000_000; i++) WeaponRendering.resourceFor(held);
        long bytes = bean.getThreadAllocatedBytes(thread) - before;
        System.out.println("WF1C_RENDER_FALLBACK allocatedBytesPerMillion=" + bytes);
        assertTrue(bytes < 64 * 1024);
    }

}
