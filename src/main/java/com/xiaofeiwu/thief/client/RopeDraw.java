package com.xiaofeiwu.thief.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xiaofeiwu.thief.ThiefMod;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Draws ropes as plain boxes with a rope texture: nothing to do with the game's own lead, whose line is dark where it meets a block. */
final class RopeDraw {

    private static final ResourceLocation TEXTURE = new ResourceLocation(ThiefMod.MODID, "textures/entity/rope_band.png");

    private RopeDraw() {
    }

    static VertexConsumer consumer(MultiBufferSource buffers) {
        return buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
    }

    /** A box from one corner to the other, in the coordinates of the pose (blocks, from where the entity stands). */
    static void box(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int light) {
        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose();
        Matrix3f n = last.normal();
        quad(vc, m, n, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1, light);          // south
        quad(vc, m, n, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1, light);         // north
        quad(vc, m, n, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0, light);          // east
        quad(vc, m, n, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0, light);         // west
        quad(vc, m, n, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0, light);          // top
        quad(vc, m, n, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, light);         // bottom
    }

    /**
     * A rope between two points (in the coordinates of the pose), sagging a little: a chain of short pieces, each a pair of crossed flat strips,
     * so that it is a rope from any side.
     */
    static void line(PoseStack pose, VertexConsumer vc, net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 to, int light) {
        net.minecraft.world.phys.Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 0.05D) {
            return;
        }
        net.minecraft.world.phys.Vec3 dir = d.scale(1.0D / len);
        net.minecraft.world.phys.Vec3 side = dir.cross(new net.minecraft.world.phys.Vec3(0.0D, 1.0D, 0.0D));
        side = (side.lengthSqr() < 1.0E-4D ? new net.minecraft.world.phys.Vec3(1.0D, 0.0D, 0.0D) : side.normalize()).scale(0.03D);
        net.minecraft.world.phys.Vec3 up = dir.cross(side).normalize().scale(0.03D);
        double sag = Math.min(0.2D, len * 0.06D);
        int pieces = 16;
        net.minecraft.world.phys.Vec3 prev = null;
        for (int i = 0; i <= pieces; i++) {
            double t = i / (double) pieces;
            net.minecraft.world.phys.Vec3 p = from.add(d.scale(t)).add(0.0D, -sag * 4.0D * t * (1.0D - t), 0.0D);
            if (prev != null) {
                strip(pose, vc, prev, p, side, light);
                strip(pose, vc, prev, p, up, light);
            }
            prev = p;
        }
    }

    private static void strip(PoseStack pose, VertexConsumer vc, net.minecraft.world.phys.Vec3 a, net.minecraft.world.phys.Vec3 b, net.minecraft.world.phys.Vec3 off, int light) {
        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose();
        Matrix3f n = last.normal();
        net.minecraft.world.phys.Vec3 normal = off.cross(b.subtract(a)).normalize();
        float nx = (float) normal.x, ny = (float) normal.y, nz = (float) normal.z;
        vertex(vc, m, n, (float) (a.x - off.x), (float) (a.y - off.y), (float) (a.z - off.z), 0, 1, nx, ny, nz, light);
        vertex(vc, m, n, (float) (a.x + off.x), (float) (a.y + off.y), (float) (a.z + off.z), 1, 1, nx, ny, nz, light);
        vertex(vc, m, n, (float) (b.x + off.x), (float) (b.y + off.y), (float) (b.z + off.z), 1, 0, nx, ny, nz, light);
        vertex(vc, m, n, (float) (b.x - off.x), (float) (b.y - off.y), (float) (b.z - off.z), 0, 0, nx, ny, nz, light);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                             float dx, float dy, float dz, float nx, float ny, float nz, int light) {
        vertex(vc, m, n, ax, ay, az, 0, 1, nx, ny, nz, light);
        vertex(vc, m, n, bx, by, bz, 1, 1, nx, ny, nz, light);
        vertex(vc, m, n, cx, cy, cz, 1, 0, nx, ny, nz, light);
        vertex(vc, m, n, dx, dy, dz, 0, 0, nx, ny, nz, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, float nx, float ny, float nz, int light) {
        vc.vertex(m, x, y, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
    }
}
