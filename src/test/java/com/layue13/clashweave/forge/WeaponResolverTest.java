package com.layue13.clashweave.forge;

import static org.junit.Assert.*;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.Test;

import com.layue13.clashweave.core.WeaponDefinition;

public class WeaponResolverTest {

    @Test
    public void registeredStaticItemIgnoresForgedDefinitionsInNbt() {
        Item item = new Item();
        WeaponDefinition definition = WeaponDefinition.katana();
        WeaponResolver resolver = new StaticKatanaResolver(item, definition);
        ItemStack stack = new ItemStack(item);
        NBTTagCompound forged = new NBTTagCompound();
        forged.setString("actionData", "untrusted");
        forged.setDouble("damageMultiplier", 1000);
        stack.setTagCompound(forged);
        assertSame(definition, resolver.resolve(stack));
        assertEquals("katana", resolver.resolve(stack).style);
        assertEquals(1, resolver.resolve(stack).damageMultiplier, 0);
        assertTrue(definition.saSlots.isEmpty());
        assertTrue(definition.effectSlots.isEmpty());
        assertNull(resolver.resolve(null));
        assertNull(resolver.resolve(new ItemStack(new Item())));
        stack.stackSize = 0;
        assertNull(resolver.resolve(stack));
    }

    @Test
    public void serverCatalogRejectsUnknownStyleAndActionReference() {
        CombatConfig config = new CombatConfig();
        config.multiplierMinimum = .5;
        config.multiplierMaximum = 2;
        config.actions = new com.layue13.clashweave.core.ActionCatalog(
            new java.io.InputStreamReader(
                getClass().getResourceAsStream("/assets/clashweave/combat/katana.json"),
                java.nio.charset.StandardCharsets.UTF_8));
        CombatWeapons registry = new CombatWeapons(config);
        assertSame(config.actions, registry.catalog(WeaponDefinition.katana()));
        for (String[] ids : new String[][] { { "unknown", "katana" }, { "katana", "unknown" } }) {
            WeaponDefinition invalid = new WeaponDefinition(
                ids[0],
                ids[1],
                1,
                1,
                1,
                java.util.Collections.emptyList(),
                java.util.Collections.emptyList(),
                com.layue13.clashweave.core.AppearanceState.DEFAULT);
            assertThrows(IllegalArgumentException.class, () -> registry.catalog(invalid));
        }
    }

    @Test
    public void serverBoundsRejectNonfiniteOrOutOfRangeNumbers() {
        WeaponDefinition.katana()
            .validate(.5, 2);
        for (double value : new double[] { 0, 3, Double.NaN, Double.POSITIVE_INFINITY }) {
            WeaponDefinition invalid = new WeaponDefinition(
                "katana",
                "katana",
                value,
                1,
                1,
                java.util.Collections.emptyList(),
                java.util.Collections.emptyList(),
                com.layue13.clashweave.core.AppearanceState.DEFAULT);
            assertThrows(IllegalArgumentException.class, () -> invalid.validate(.5, 2));
        }
    }
}
