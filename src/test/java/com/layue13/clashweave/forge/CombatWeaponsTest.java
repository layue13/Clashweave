package com.layue13.clashweave.forge;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.layue13.clashweave.content.ContentLoader;
import com.layue13.clashweave.content.EffectiveRules;
import com.layue13.clashweave.core.AppearanceState;
import com.layue13.clashweave.core.WeaponDefinition;
import com.layue13.clashweave.network.ContentSyncMessage;
import com.layue13.clashweave.network.HelloMessage;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class CombatWeaponsTest {

    private final Item sword = new Item(), stick = new Item(), other = new Item();
    private EffectiveRules rules;
    private final Map<String, Item> items = new HashMap<>();

    /** Re-derives rules the way a client does, from its own pack plus the announced overrides. */
    private static EffectiveRules derive(String overrides) throws java.io.IOException {
        return EffectiveRules.build(
            ContentLoader.load(CombatWeaponsTest.class.getClassLoader(), null).registry,
            EffectiveRules.Overrides.parse(overrides),
            null);
    }

    @Before
    public void rules() throws Exception {
        rules = derive(new EffectiveRules.Overrides(true, true, 1.2, false).canonical());
        items.put("clashweave:katana", sword);
        items.put("minecraft:stick", stick);
    }

    @After
    public void clear() {
        CombatWeapons.install(null);
    }

    private CombatWeapons.Table table(String... lines) {
        return CombatWeapons.build(Arrays.asList(lines), rules, .5, 2, items::get);
    }

    @Test
    public void registeredItemResolvesToTheServersWeaponAndIgnoresForgedNbt() {
        CombatWeapons.install(table("clashweave:katana=katana:katana"));
        ItemStack stack = new ItemStack(sword);
        NBTTagCompound forged = new NBTTagCompound();
        forged.setString("actionData", "untrusted");
        forged.setString("style", "katana:evil");
        forged.setDouble("damageMultiplier", 1000);
        stack.setTagCompound(forged);
        CombatWeapons.Entry entry = CombatWeapons.entry(stack);
        assertSame(rules.weapon("katana:katana"), entry.definition);
        assertEquals("katana:default", entry.definition.style);
        assertEquals(1, entry.definition.damageMultiplier, 0);
        assertSame(
            "the style catalog is cached, not rebuilt per lookup",
            rules.catalog("katana:default"),
            entry.catalog);
        assertSame(entry, CombatWeapons.entry(new ItemStack(sword)));
        assertSame(rules.wireJson("katana:default"), entry.wireJson);
        assertNull(CombatWeapons.entry(null));
        assertNull(CombatWeapons.entry(new ItemStack(other)));
        stack.stackSize = 0;
        assertNull(CombatWeapons.entry(stack));
        assertTrue(CombatWeapons.armed(new ItemStack(sword)));
        assertFalse(CombatWeapons.armed(new ItemStack(other)));
    }

    @Test
    public void emptyTableArmsNothing() {
        assertFalse(CombatWeapons.armed(new ItemStack(sword)));
        CombatWeapons.install(table("clashweave:katana=katana:katana"));
        CombatWeapons.install(null);
        assertFalse(CombatWeapons.armed(new ItemStack(sword)));
        assertNull(CombatWeapons.fallback());
    }

    @Test
    public void tableRejectsUnknownMalformedDuplicateAndOutOfRangeEntriesNamingThem() {
        for (String[] bad : new String[][] { { "clashweave:katana=nope:nothing" }, { "unknown:item=katana:katana" },
            { "clashweave:katana" }, { "=katana:katana" },
            { "clashweave:katana=katana:katana", "clashweave:katana=katana:katana" }, {} }) {
            assertThrows(Arrays.toString(bad), IllegalArgumentException.class, () -> table(bad));
        }
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> table("clashweave:katana=katana:katana", "unknown:item=katana:katana", "minecraft:stick=nope:x"));
        assertTrue(
            error.getMessage(),
            error.getMessage()
                .contains("unknown:item"));
        assertTrue(
            error.getMessage(),
            error.getMessage()
                .contains("nope:x"));
        // Server bounds on weapon multipliers still apply to data-driven weapons.
        assertThrows(
            IllegalArgumentException.class,
            () -> CombatWeapons
                .build(Collections.singletonList("clashweave:katana=katana:katana"), rules, 1.5, 2, items::get));
    }

    @Test
    public void twoItemsMayShareOneWeaponAndKeepConfigurationOrder() {
        CombatWeapons.install(table("clashweave:katana=katana:katana", "minecraft:stick=katana:katana"));
        assertSame(CombatWeapons.entry(new ItemStack(sword)), CombatWeapons.entry(new ItemStack(stick)));
        assertEquals(
            Arrays.asList("clashweave:katana", "minecraft:stick"),
            Arrays.asList(
                CombatWeapons.table()
                    .names()
                    .keySet()
                    .toArray(new String[0])));
        assertSame(CombatWeapons.entry(new ItemStack(sword)), CombatWeapons.fallback());
    }

    @Test
    public void handshakeMessagesRoundTripAndBoundTheirSizes() throws Exception {
        ByteBuf data = Unpooled.buffer();
        try {
            new HelloMessage(rules.packGameplayHash, rules.presentationHash).toBytes(data);
            HelloMessage back = new HelloMessage();
            back.fromBytes(data);
            assertEquals(rules.packGameplayHash, back.gameplay);
            assertEquals(rules.presentationHash, back.presentation);
            data.clear();
            new HelloMessage(new String(new char[65]).replace('\0', 'a'), "").toBytes(data);
            assertThrows(IllegalArgumentException.class, () -> new HelloMessage().fromBytes(data));
            data.clear();
            ContentSyncMessage sync = new ContentSyncMessage(
                rules.effectiveHash,
                rules.overridesCanonical,
                table("clashweave:katana=katana:katana", "minecraft:stick=katana:katana").names());
            sync.toBytes(data);
            ContentSyncMessage decoded = new ContentSyncMessage();
            decoded.fromBytes(data);
            assertEquals(rules.effectiveHash, decoded.effectiveHash);
            assertEquals(rules.overridesCanonical, decoded.overrides);
            assertEquals(sync.items, decoded.items);
            assertEquals(rules.effectiveHash, derive(decoded.overrides).effectiveHash);
            data.clear();
            data.writeInt(1);
            assertThrows(Exception.class, () -> new ContentSyncMessage().fromBytes(data));
        } finally {
            data.release();
        }
    }

    @Test
    public void weaponDefinitionsCarryTheContentIdsOnTheWire() {
        WeaponDefinition katana = rules.weapon("katana:katana");
        assertEquals(
            new AppearanceState("katana:default", "clashweave:default", "katana:rigid_arc"),
            katana.appearance);
    }
}
