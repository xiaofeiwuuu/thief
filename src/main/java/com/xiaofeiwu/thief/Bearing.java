package com.xiaofeiwu.thief;

/** Where something is, as seen by a player looking in some direction: an arrow ("↑" is straight ahead) and how far. Pure logic. */
final class Bearing {

    static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private Bearing() {
    }

    /** @param dx,dz from the player to the target; @param yaw the player's facing, as Minecraft counts it (0 = south, 90 = west) */
    static int arrow(double dx, double dz, float yaw) {
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));      // the yaw one would have to face to look at the target
        double rel = targetYaw - yaw;
        rel = ((rel % 360.0D) + 540.0D) % 360.0D - 180.0D;           // -180 .. 180, positive is to the right
        return (((int) Math.round(rel / 45.0D)) % 8 + 8) % 8;
    }

    static int distance(double dx, double dz) {
        return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
    }
}
