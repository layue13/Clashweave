package com.layue13.clashweave.probe;

import java.lang.reflect.Method;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

public final class ProbeInteraction {

    private ProbeInteraction() {}

    public static boolean interactive(World world, int x, int y, int z) {
        Block block = world.getBlock(x, y, z);
        if (world.getTileEntity(x, y, z) != null) return true;
        for (String name : new String[] { "onBlockActivated", "func_149727_a" }) {
            try {
                Method method = block.getClass()
                    .getMethod(
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
                return method.getDeclaringClass() != Block.class;
            } catch (NoSuchMethodException ignored) {
                // Dev/SRG names differ; no invocation or speculative block side effect here.
            }
        }
        return false;
    }
}
