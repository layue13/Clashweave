package com.layue13.clashweave.client.pose;

/** Stateless two-segment IK in Biped units. Caller supplies all storage. */
public final class TwoBoneIk {

    private TwoBoneIk() {}

    /** Positive finite lengths, finite points/pole; outputs must be distinct arrays of length three. */
    public static void solve(float[] pivot, float[] target, float[] pole, float l1, float l2, float[] outUpperDir,
        float[] outLowerDir) {
        if (!(l1 > 0) || !(l2 > 0) || !Float.isFinite(l1) || !Float.isFinite(l2))
            throw new IllegalArgumentException("lengths: expected finite positive values");
        double dx = (double) target[0] - pivot[0], dy = (double) target[1] - pivot[1];
        double dz = (double) target[2] - pivot[2];
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance == 0) {
            dx = 0;
            dy = 1;
            dz = 0;
        } else {
            dx /= distance;
            dy /= distance;
            dz /= distance;
        }
        double dot = dx * pole[0] + dy * pole[1] + dz * pole[2];
        double px = pole[0] - dot * dx, py = pole[1] - dot * dy, pz = pole[2] - dot * dz;
        double norm = Math.sqrt(px * px + py * py + pz * pz);
        if (norm < 1e-10) {
            // Least-aligned Cartesian axis, ties X then Y then Z: deterministic at degeneracy.
            int axis = Math.abs(dx) <= Math.abs(dy) && Math.abs(dx) <= Math.abs(dz) ? 0
                : Math.abs(dy) <= Math.abs(dz) ? 1 : 2;
            dot = axis == 0 ? dx : axis == 1 ? dy : dz;
            px = (axis == 0 ? 1 : 0) - dot * dx;
            py = (axis == 1 ? 1 : 0) - dot * dy;
            pz = (axis == 2 ? 1 : 0) - dot * dz;
            norm = Math.sqrt(px * px + py * py + pz * pz);
        }
        px /= norm;
        py /= norm;
        pz /= norm;
        double radius = Math.max(Math.abs((double) l1 - l2), Math.min((double) l1 + l2, distance));
        double along = radius == 0 ? 0 : (radius * radius + (double) l1 * l1 - (double) l2 * l2) / (2 * radius);
        double height = Math.sqrt(Math.max(0, (double) l1 * l1 - along * along));
        outUpperDir[0] = (float) ((along * dx + height * px) / l1);
        outUpperDir[1] = (float) ((along * dy + height * py) / l1);
        outUpperDir[2] = (float) ((along * dz + height * pz) / l1);
        outLowerDir[0] = (float) (((radius - along) * dx - height * px) / l2);
        outLowerDir[1] = (float) (((radius - along) * dy - height * py) / l2);
        outLowerDir[2] = (float) (((radius - along) * dz - height * pz) / l2);
    }
}
