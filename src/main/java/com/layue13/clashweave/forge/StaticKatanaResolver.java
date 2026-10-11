package com.layue13.clashweave.forge;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.layue13.clashweave.core.WeaponDefinition;

/** P0 supports one registered static item; ignores untrusted content-like NBT. */
public final class StaticKatanaResolver implements WeaponResolver {

    private final Item item;
    private final WeaponDefinition definition;

    public StaticKatanaResolver(Item item, WeaponDefinition definition) {
        this.item = item;
        this.definition = definition;
    }

    @Override
    public WeaponDefinition resolve(ItemStack stack) {
        return stack != null && stack.stackSize > 0 && stack.getItem() == item ? definition : null;
    }
}
