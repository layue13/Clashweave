package com.layue13.clashweave.forge;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;

/** Body-height layers of the same timed blade sweep; no additional hit or segment. */
final class SweepGeometry {

    private SweepGeometry() {}

    static boolean intersects(EntityPlayer player, EntityLivingBase target, double reach, double previous, double next,
        float yaw, float pitch, int layers, double bottom, double top) {
        double tilt = Math.toRadians(pitch);
        for (int layer = 0; layer < layers; layer++) {
            double height = player.height * (bottom + (top - bottom) * layer / (layers - 1.0));
            Vec3 origin = Vec3.createVectorHelper(player.posX, player.posY + height, player.posZ);
            for (int sample = 0; sample <= 8; sample++) {
                double angle = Math.toRadians(yaw + previous + (next - previous) * sample / 8);
                Vec3 tip = origin.addVector(
                    -Math.sin(angle) * reach * Math.cos(tilt),
                    -Math.sin(tilt) * reach,
                    Math.cos(angle) * reach * Math.cos(tilt));
                if (target.boundingBox.expand(.12, .15, .12)
                    .calculateIntercept(origin, tip) != null && player.worldObj.rayTraceBlocks(origin, tip) == null)
                    return true;
            }
        }
        return false;
    }
}
