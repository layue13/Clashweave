package com.layue13.clashweave.client;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;

import com.layue13.clashweave.client.body.Channels;

/**
 * Moves a ModelBiped's pose to and from the body rig's channels: what vanilla posed goes in as the starting targets,
 * the smoothed result is written back onto the same parts. Shared by the player model and the monster models.
 */
final class BipedChannels {

    /** Offsets into a home-pivot array: the rest pivot (x, y, z) of each part. */
    static final int HEAD = 0, BODY = 3, RARM = 6, LARM = 9, RLEG = 12, LLEG = 15;
    /** The player model's rest pivots. */
    static final float[] PLAYER_HOME = { 0, 0, 0, 0, 0, 0, -5, 2, 0, 5, 2, 0, -1.9f, 12, 0, 1.9f, 12, 0 };

    private BipedChannels() {}

    /** The rest pivots a model was built with, read before anything has moved them. */
    static float[] homeOf(ModelBiped m) {
        ModelRenderer[] parts = { m.bipedHead, m.bipedBody, m.bipedRightArm, m.bipedLeftArm, m.bipedRightLeg,
            m.bipedLeftLeg };
        float[] home = new float[18];
        for (int i = 0; i < 6; i++) {
            home[i * 3] = parts[i].rotationPointX;
            home[i * 3 + 1] = parts[i].rotationPointY;
            home[i * 3 + 2] = parts[i].rotationPointZ;
        }
        return home;
    }

    /** Puts every pivot back where the model was built with it. */
    static void restoreHome(ModelBiped m, float[] home) {
        m.bipedHead.setRotationPoint(home[HEAD], home[HEAD + 1], home[HEAD + 2]);
        m.bipedHeadwear.setRotationPoint(home[HEAD], home[HEAD + 1], home[HEAD + 2]);
        m.bipedBody.setRotationPoint(home[BODY], home[BODY + 1], home[BODY + 2]);
        m.bipedRightArm.setRotationPoint(home[RARM], home[RARM + 1], home[RARM + 2]);
        m.bipedLeftArm.setRotationPoint(home[LARM], home[LARM + 1], home[LARM + 2]);
        m.bipedRightLeg.setRotationPoint(home[RLEG], home[RLEG + 1], home[RLEG + 2]);
        m.bipedLeftLeg.setRotationPoint(home[LLEG], home[LLEG + 1], home[LLEG + 2]);
    }

    static void readVanilla(ModelBiped m, float[] t) {
        t[Channels.HEAD_X] = m.bipedHead.rotateAngleX;
        t[Channels.HEAD_Y] = m.bipedHead.rotateAngleY;
        t[Channels.BODY_X] = m.bipedBody.rotateAngleX;
        t[Channels.BODY_Y] = m.bipedBody.rotateAngleY;
        t[Channels.BODY_Z] = m.bipedBody.rotateAngleZ;
        t[Channels.R_ARM_X] = m.bipedRightArm.rotateAngleX;
        t[Channels.R_ARM_Y] = m.bipedRightArm.rotateAngleY;
        t[Channels.R_ARM_Z] = m.bipedRightArm.rotateAngleZ;
        t[Channels.L_ARM_X] = m.bipedLeftArm.rotateAngleX;
        t[Channels.L_ARM_Y] = m.bipedLeftArm.rotateAngleY;
        t[Channels.L_ARM_Z] = m.bipedLeftArm.rotateAngleZ;
        t[Channels.R_LEG_X] = m.bipedRightLeg.rotateAngleX;
        t[Channels.R_LEG_Y] = m.bipedRightLeg.rotateAngleY;
        t[Channels.R_LEG_Z] = m.bipedRightLeg.rotateAngleZ;
        t[Channels.L_LEG_X] = m.bipedLeftLeg.rotateAngleX;
        t[Channels.L_LEG_Y] = m.bipedLeftLeg.rotateAngleY;
        t[Channels.L_LEG_Z] = m.bipedLeftLeg.rotateAngleZ;
        t[Channels.R_FORE] = t[Channels.L_FORE] = t[Channels.R_SHIN] = t[Channels.L_SHIN] = 0;
        t[Channels.ROOT_Y] = 0;
    }

    /** The smoothed base pose: vanilla's, or the locomotion layer's where it took over, chased frame by frame. */
    static void writeBase(ModelBiped m, ModelRenderer rightForearm, ModelRenderer leftForearm, ModelRenderer rightShin,
        ModelRenderer leftShin, boolean rigidArms, boolean rigidLegs, float[] home, float[] c) {
        m.bipedHead.rotateAngleX = c[Channels.HEAD_X];
        m.bipedHead.rotateAngleY = c[Channels.HEAD_Y];
        m.bipedHeadwear.rotateAngleX = m.bipedHead.rotateAngleX;
        m.bipedHeadwear.rotateAngleY = m.bipedHead.rotateAngleY;
        m.bipedBody.rotateAngleX = c[Channels.BODY_X];
        m.bipedBody.rotateAngleY = c[Channels.BODY_Y];
        m.bipedBody.rotateAngleZ = c[Channels.BODY_Z];
        m.bipedRightArm.rotateAngleX = c[Channels.R_ARM_X];
        m.bipedRightArm.rotateAngleY = c[Channels.R_ARM_Y];
        m.bipedRightArm.rotateAngleZ = c[Channels.R_ARM_Z];
        m.bipedLeftArm.rotateAngleX = c[Channels.L_ARM_X];
        m.bipedLeftArm.rotateAngleY = c[Channels.L_ARM_Y];
        m.bipedLeftArm.rotateAngleZ = c[Channels.L_ARM_Z];
        m.bipedRightLeg.rotateAngleX = c[Channels.R_LEG_X];
        m.bipedRightLeg.rotateAngleY = c[Channels.R_LEG_Y];
        m.bipedRightLeg.rotateAngleZ = c[Channels.R_LEG_Z];
        m.bipedLeftLeg.rotateAngleX = c[Channels.L_LEG_X];
        m.bipedLeftLeg.rotateAngleY = c[Channels.L_LEG_Y];
        m.bipedLeftLeg.rotateAngleZ = c[Channels.L_LEG_Z];
        if (rightForearm != null) {
            // Elbows bend the forearm forward (-X), knees bend the shin back (+X); a worn piece keeps the limb
            // straight.
            if (!rigidArms) {
                rightForearm.rotateAngleX = -c[Channels.R_FORE];
                leftForearm.rotateAngleX = -c[Channels.L_FORE];
            }
            if (!rigidLegs) {
                rightShin.rotateAngleX = c[Channels.R_SHIN];
                leftShin.rotateAngleX = c[Channels.L_SHIN];
            }
        }
        // The torso turns about the hips, not about the neck its box is hinged on: the neck, the shoulders and the
        // head ride the same rotation, and the legs (independent parts) keep the hip where it is. Otherwise a lean
        // swings the bottom of the torso away from the legs, a gap the size of 12 px times the sine of the lean.
        m.bipedRightLeg.setRotationPoint(home[RLEG], home[RLEG + 1], home[RLEG + 2]);
        m.bipedLeftLeg.setRotationPoint(home[LLEG], home[LLEG + 1], home[LLEG + 2]);
        // The hips are where the legs hang from: 12 px on a player, elsewhere for a taller body.
        float hip = home[RLEG + 1];
        aboutHips(m.bipedBody, home[BODY], home[BODY + 1] - hip, home[BODY + 2], hip, c);
        aboutHips(m.bipedHead, home[HEAD], home[HEAD + 1] - hip, home[HEAD + 2], hip, c);
        aboutHips(m.bipedHeadwear, home[HEAD], home[HEAD + 1] - hip, home[HEAD + 2], hip, c);
        aboutHips(m.bipedRightArm, home[RARM], home[RARM + 1] - hip, home[RARM + 2], hip, c);
        aboutHips(m.bipedLeftArm, home[LARM], home[LARM + 1] - hip, home[LARM + 2], hip, c);
        float bob = c[Channels.ROOT_Y];
        m.bipedBody.rotationPointY += bob;
        m.bipedHead.rotationPointY += bob;
        m.bipedHeadwear.rotationPointY += bob;
        m.bipedRightArm.rotationPointY += bob;
        m.bipedLeftArm.rotationPointY += bob;
    }

    /**
     * Puts the part's pivot where the point (x, y px from the hips, in the upright torso) goes under the torso turn.
     */
    static void aboutHips(ModelRenderer part, float x, float y, float z, float hip, float[] c) {
        double ax = c[Channels.BODY_X], ay = c[Channels.BODY_Y], az = c[Channels.BODY_Z];
        double y1 = y * Math.cos(ax) - z * Math.sin(ax), z1 = y * Math.sin(ax) + z * Math.cos(ax);
        double x2 = x * Math.cos(ay) + z1 * Math.sin(ay), z2 = -x * Math.sin(ay) + z1 * Math.cos(ay);
        double x3 = x2 * Math.cos(az) - y1 * Math.sin(az), y3 = x2 * Math.sin(az) + y1 * Math.cos(az);
        part.rotationPointX = (float) x3;
        part.rotationPointY = (float) y3 + hip;
        part.rotationPointZ = (float) z2;
    }
}
