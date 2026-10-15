package com.layue13.clashweave.forge;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;

/** Body-height layers of the same timed blade sweep; no additional hit or segment. */
final class SweepGeometry {

    private SweepGeometry() {}

    /** Whether the blow of this action, at this point of its active ticks, reaches the target. */
    static boolean intersects(EntityPlayer player, EntityLivingBase target,
        com.layue13.clashweave.core.ActionCatalog.Definition definition, double from, double to, double previous,
        double next, float yaw, float pitch, int layers, double bottom, double top) {
        String shape = definition.shape;
        if (com.layue13.clashweave.core.HitShapes.THRUST.equals(shape)
            || com.layue13.clashweave.core.HitShapes.BURST.equals(shape)) {
            net.minecraft.util.AxisAlignedBB box = target.boundingBox;
            double angle = Math.toRadians(yaw), dx = -Math.sin(angle), dz = Math.cos(angle);
            boolean hit;
            if (com.layue13.clashweave.core.HitShapes.THRUST.equals(shape)) {
                // The line is at body height, so the target must overlap the heights the sweep layers cover.
                if (box.maxY < player.posY + player.height * bottom - .15
                    || box.minY > player.posY + player.height * top + .15) return false;
                hit = com.layue13.clashweave.core.HitShapes.thrustHits(
                    player.posX,
                    player.posZ,
                    dx,
                    dz,
                    definition.reach,
                    to,
                    definition.width + .24,
                    box.minX,
                    box.minZ,
                    box.maxX,
                    box.maxZ);
            } else {
                // A burst runs along the ground: whatever stands on it, not what hangs well above.
                if (box.maxY < player.posY - .5 || box.minY > player.posY + 1.0) return false;
                hit = com.layue13.clashweave.core.HitShapes.burstHits(
                    player.posX,
                    player.posZ,
                    dx,
                    dz,
                    definition.offset,
                    definition.width,
                    box.minX,
                    box.minZ,
                    box.maxX,
                    box.maxZ);
            }
            if (!hit) return false;
            // Walls stop both: nothing is reached through a block between the attacker and the target.
            Vec3 origin = Vec3.createVectorHelper(player.posX, player.posY + player.height * .5, player.posZ);
            Vec3 centre = Vec3
                .createVectorHelper(target.posX, Math.max(box.minY, Math.min(box.maxY, origin.yCoord)), target.posZ);
            return player.worldObj.rayTraceBlocks(origin, centre) == null;
        }
        return intersects(player, target, definition.reach, previous, next, yaw, pitch, layers, bottom, top);
    }

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
