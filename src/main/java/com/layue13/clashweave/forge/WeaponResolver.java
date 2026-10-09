package com.layue13.clashweave.forge;

import net.minecraft.item.ItemStack;

import com.layue13.clashweave.core.WeaponDefinition;

/** Internal Forge boundary. Client item NBT cannot supply executable content definitions. */
public interface WeaponResolver {

    WeaponDefinition resolve(ItemStack stack);
}
