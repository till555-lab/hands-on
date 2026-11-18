package com.handson.client;

/**
 * Tiny 4x4 matrix helper. It mirrors the transforms applied to the MatrixStack so that the
 * position of the arm tip can be computed (used to attach the held item to the hand).
 * Same conventions as the game: right-handed rotations, every operation post-multiplies.
 */
public class Pose {
    private double[] m = {
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1};

    private void mul(double[] o) {
        double[] r = new double[16];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                double s = 0.0D;
                for (int k = 0; k < 4; k++) {
                    s += m[i * 4 + k] * o[k * 4 + j];
                }
                r[i * 4 + j] = s;
            }
        }
        m = r;
    }

    public void translate(double x, double y, double z) {
        mul(new double[]{
                1, 0, 0, x,
                0, 1, 0, y,
                0, 0, 1, z,
                0, 0, 0, 1});
    }

    public void scale(double x, double y, double z) {
        mul(new double[]{
                x, 0, 0, 0,
                0, y, 0, 0,
                0, 0, z, 0,
                0, 0, 0, 1});
    }

    public void rotX(double deg) {
        double a = Math.toRadians(deg);
        double c = Math.cos(a);
        double s = Math.sin(a);
        mul(new double[]{
                1, 0, 0, 0,
                0, c, -s, 0,
                0, s, c, 0,
                0, 0, 0, 1});
    }

    public void rotY(double deg) {
        double a = Math.toRadians(deg);
        double c = Math.cos(a);
        double s = Math.sin(a);
        mul(new double[]{
                c, 0, s, 0,
                0, 1, 0, 0,
                -s, 0, c, 0,
                0, 0, 0, 1});
    }

    public void rotZ(double deg) {
        double a = Math.toRadians(deg);
        double c = Math.cos(a);
        double s = Math.sin(a);
        mul(new double[]{
                c, -s, 0, 0,
                s, c, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, 1});
    }

    /** Transforms a point from the current local space into the space the pose started in. */
    public double[] apply(double x, double y, double z) {
        return new double[]{
                m[0] * x + m[1] * y + m[2] * z + m[3],
                m[4] * x + m[5] * y + m[6] * z + m[7],
                m[8] * x + m[9] * y + m[10] * z + m[11]};
    }
}
