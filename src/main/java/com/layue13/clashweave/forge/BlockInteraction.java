package com.layue13.clashweave.forge;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/** Detect an activation override without calling it and causing a second interaction. */
public final class BlockInteraction {

    private BlockInteraction() {}

    public static boolean interactive(Block block) {
        for (Class<?> type = block.getClass(); type != null && type != Block.class; type = type.getSuperclass()) {
            for (String name : new String[] { "onBlockActivated", "func_149727_a" }) {
                try {
                    type.getDeclaredMethod(
                        name,
                        World.class,
                        int.class,
                        int.class,
                        int.class,
                        EntityPlayer.class,
                        int.class,
                        float.class,
                        float.class,
                        float.class);
                    return true;
                } catch (NoSuchMethodException ignored) {
                    // Try the runtime mapping and then the superclass; no world side effects.
                }
            }
        }
        return false;
    }
}
