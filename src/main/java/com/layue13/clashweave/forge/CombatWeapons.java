package com.layue13.clashweave.forge;

import net.minecraft.item.ItemStack;

import com.layue13.clashweave.Clashweave;
import com.layue13.clashweave.core.ActionCatalog;
import com.layue13.clashweave.core.WeaponDefinition;

/** Owns the P0 type registry and server validation, extracted from the adapter. */
public final class CombatWeapons {

    public static final WeaponDefinition DEFAULT = WeaponDefinition.katana();
    public static final WeaponResolver RESOLVER = new StaticKatanaResolver(Clashweave.katana, DEFAULT);
    private final CombatConfig config;

    public CombatWeapons(CombatConfig config) {
        this.config = config;
    }

    public WeaponDefinition resolve(ItemStack stack) {
        WeaponDefinition definition = RESOLVER.resolve(stack);
        if (definition != null) catalog(definition);
        return definition;
    }

    public ActionCatalog catalog(WeaponDefinition definition) {
        definition.validate(config.multiplierMinimum, config.multiplierMaximum);
        // One registered type in P0. Material/appearance cannot select a different timing/geometry catalog.
        if (!DEFAULT.style.equals(definition.style) || !DEFAULT.actionData.equals(definition.actionData))
            throw new IllegalArgumentException("Unregistered weapon type");
        return config.actions;
    }
}
