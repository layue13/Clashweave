package com.layue13.clashweave.forge;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

public final class KatanaItem extends Item {

    public KatanaItem() {
        setUnlocalizedName("clashweave.katana");
        setTextureName("clashweave:katana");
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabCombat);
    }
}
