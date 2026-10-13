package net.exylia.exyliaDisplays.common.store;

import net.exylia.lib.config.Comment;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Where a hologram stands, the bottom of its last line, and which way it faces.
 *
 * <p>The facing only shows on a hologram whose billboard is {@code FIXED} (or the axis a
 * {@code VERTICAL} or {@code HORIZONTAL} one keeps): a {@code CENTER} hologram turns to every
 * viewer anyway.
 *
 * @param world the world's name
 * @param x     east-west
 * @param y     height
 * @param z     north-south
 * @param yaw   which way it faces: 0 south, 90 west, 180 north, -90 east
 * @param pitch how far it leans: 0 upright
 */
public record Position(
        @Comment("The world's folder name.")
        String world,
        double x,
        double y,
        double z,
        @Comment("Which way it faces, for a FIXED hologram: 0 south, 90 west, 180 north, -90 east.")
        float yaw,
        @Comment("How far it leans forward or back, for a FIXED hologram. 0 is upright.")
        float pitch) {

    public Position() {
        this("world", 0.5, 100.0, 0.5, 0f, 0f);
    }

    /** A place facing south and upright. */
    public Position(String world, double x, double y, double z) {
        this(world, x, y, z, 0f, 0f);
    }

    public Position {
        if (world == null || world.isBlank()) {
            world = "world";
        }
        // Imports and hand edits carry float noise (90.12000274658203); the file keeps what of() keeps.
        x = round(x);
        y = round(y);
        z = round(z);
        yaw = roundAngle(yaw);
        pitch = roundAngle(pitch);
    }

    /** A location, kept to a thousandth of a block so the file stays readable. */
    public static Position of(Location location) {
        return new Position(location.getWorld() == null ? "world" : location.getWorld().getName(),
                round(location.getX()), round(location.getY()), round(location.getZ()),
                roundAngle(location.getYaw()), roundAngle(location.getPitch()));
    }

    /**
     * Where a player stands, facing them: what a hologram they create or move should be.
     * A text display is read from the side it faces, so it faces back along their look.
     */
    public static Position facing(Location viewer) {
        Position at = of(viewer);
        return at.withFacing(normalise(viewer.getYaw() + 180f), 0f);
    }

    /** The location, or {@code null} while its world is not loaded. */
    public Location toLocation() {
        World loaded = Bukkit.getWorld(world);
        return loaded == null ? null : new Location(loaded, x, y, z, yaw, pitch);
    }

    /** The same place, moved to the centre of the block it is in. */
    public Position centred() {
        return new Position(world, Math.floor(x) + 0.5, y, Math.floor(z) + 0.5, yaw, pitch);
    }

    /** The same place, shifted. */
    public Position plus(double dx, double dy, double dz) {
        return new Position(world, round(x + dx), round(y + dy), round(z + dz), yaw, pitch);
    }

    /** The same place, facing another way. */
    public Position withFacing(float newYaw, float newPitch) {
        return new Position(world, x, y, z, roundAngle(newYaw), roundAngle(newPitch));
    }

    static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static float roundAngle(float degrees) {
        return Math.round(degrees * 10f) / 10f;
    }

    private static float normalise(float degrees) {
        float wrapped = degrees % 360f;
        if (wrapped >= 180f) wrapped -= 360f;
        if (wrapped < -180f) wrapped += 360f;
        return wrapped;
    }
}
