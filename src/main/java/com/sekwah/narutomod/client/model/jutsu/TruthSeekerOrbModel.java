package com.sekwah.narutomod.client.model.jutsu;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * The Truth-Seeking Orb: a hand-sized sphere of solid black chakra.
 *
 * <p>One piece of geometry shared by everything that wears the look: the halo behind a Six Paths
 * caster (see {@code SixPathsOrbRenderer}) and the thrown Tailed Beast Bomb (see
 * {@code TailedBallRenderer}). It is drawn with the black concrete texture and a white vertex
 * colour, so the sphere comes out flat black, and fullbright, so it reads as chakra rather than
 * stone even at night.</p>
 */
public final class TruthSeekerOrbModel {

    private static final int LAT_SEGMENTS = 8;
    private static final int LON_SEGMENTS = 12;

    private TruthSeekerOrbModel() {
    }

    /**
     * Draws one black UV sphere centred on the given point, in the current pose.
     *
     * @param poseStack the pose to draw in
     * @param consumer  the black-textured buffer to write into
     * @param cx        centre of the sphere
     * @param cy        centre of the sphere
     * @param cz        centre of the sphere
     * @param radius    radius of the sphere
     * @param packedLight lightmap coordinates for the sphere
     */
    public static void renderSphere(PoseStack poseStack, VertexConsumer consumer,
                                    double cx, double cy, double cz,
                                    double radius, int packedLight) {
        for (int lat = 0; lat < LAT_SEGMENTS; lat++) {
            double lat0 = Math.PI * (-0.5d + (double) lat / LAT_SEGMENTS);
            double lat1 = Math.PI * (-0.5d + (double) (lat + 1) / LAT_SEGMENTS);
            double z0 = Math.sin(lat0);
            double zr0 = Math.cos(lat0);
            double z1 = Math.sin(lat1);
            double zr1 = Math.cos(lat1);

            for (int lon = 0; lon < LON_SEGMENTS; lon++) {
                double lon0 = 2 * Math.PI * (double) lon / LON_SEGMENTS;
                double lon1 = 2 * Math.PI * (double) (lon + 1) / LON_SEGMENTS;

                double x00 = cx + radius * zr0 * Math.cos(lon0);
                double y00 = cy + radius * z0;
                double z00 = cz + radius * zr0 * Math.sin(lon0);

                double x10 = cx + radius * zr1 * Math.cos(lon0);
                double y10 = cy + radius * z1;
                double z10 = cz + radius * zr1 * Math.sin(lon0);

                double x11 = cx + radius * zr1 * Math.cos(lon1);
                double y11 = cy + radius * z1;
                double z11 = cz + radius * zr1 * Math.sin(lon1);

                double x01 = cx + radius * zr0 * Math.cos(lon1);
                double y01 = cy + radius * z0;
                double z01 = cz + radius * zr0 * Math.sin(lon1);

                vertex(poseStack, consumer, x00, y00, z00, 0, 0, packedLight);
                vertex(poseStack, consumer, x10, y10, z10, 0, 1, packedLight);
                vertex(poseStack, consumer, x11, y11, z11, 1, 1, packedLight);
                vertex(poseStack, consumer, x01, y01, z01, 1, 0, packedLight);
            }
        }
    }

    private static void vertex(PoseStack poseStack, VertexConsumer consumer,
                               double x, double y, double z, float u, float v, int packedLight) {
        consumer.vertex(poseStack.last().pose(), (float) x, (float) y, (float) z)
                .color(1.0f, 1.0f, 1.0f, 1.0f).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(packedLight).normal(0.0f, 1.0f, 0.0f).endVertex();
    }
}
