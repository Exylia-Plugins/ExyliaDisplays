package net.exylia.exyliaDisplays.common.store;

import net.exylia.lib.config.Comment;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Where something that faces a way stands: a {@link Position} plus the direction it looks.
 *
 * <p>Kept apart from {@code Position} because a hologram has no facing, and giving it one would
 * write two meaningless keys into every hologram in {@code holograms.yml}.
 *
 * @param world the world's name
 * @param x     east-west
 * @param y     height
 * @param z     north-south
 * @param yaw   which way it faces: 0 south, 90 west, 180 north, -90 east
 * @param pitch how far it looks down (positive) or up (negative)
 */
public record Place(
        @Comment("The world's folder name.")
        String world,
        double x,
        double y,
        double z,
        @Comment("Which way it faces: 0 south, 90 west, 180 north, -90 east.")
        double yaw,
        @Comment("Looking down (up to 90) or up (down to -90).")
        double pitch) {

    public Place() {
        this("world", 0.5, 100.0, 0.5, 0, 0);
    }

    public Place {
        if (world == null || world.isBlank()) {
            world = "world";
        }
        pitch = Math.clamp(pitch, -90, 90);
    }

    /** A location, kept to a thousandth of a block and a tenth of a degree so the file stays readable. */
    public static Place of(Location location) {
        return new Place(location.getWorld() == null ? "world" : location.getWorld().getName(),
                Position.round(location.getX()), Position.round(location.getY()), Position.round(location.getZ()),
                angle(location.getYaw()), angle(location.getPitch()));
    }

    /** The location, or {@code null} while its world is not loaded. */
    public Location toLocation() {
        World loaded = Bukkit.getWorld(world);
        return loaded == null ? null : new Location(loaded, x, y, z, (float) yaw, (float) pitch);
    }

    /** The point it stands on, shifted up, for whatever floats above it. */
    public Position above(double height) {
        return new Position(world, x, y, z).plus(0, height, 0);
    }

    /** The same place, moved to the centre of the block it is in. */
    public Place centred() {
        return new Place(world, Math.floor(x) + 0.5, y, Math.floor(z) + 0.5, yaw, pitch);
    }

    /** The same place, turned to face a point; the pitch is left level. */
    public Place facing(double targetX, double targetZ) {
        double dx = targetX - x;
        double dz = targetZ - z;
        if (dx == 0 && dz == 0) {
            return this;
        }
        return new Place(world, x, y, z, angle(Math.toDegrees(Math.atan2(-dx, dz))), 0);
    }

    /** The same place, turned to face the opposite way of a yaw: towards whoever had that yaw. */
    public Place facingBack(double yaw) {
        return new Place(world, x, y, z, angle(yaw + 180), 0);
    }

    /** A yaw folded into -180..180 and kept to a tenth of a degree. */
    static double angle(double degrees) {
        double folded = ((degrees % 360) + 540) % 360 - 180;
        return Math.round(folded * 10.0) / 10.0;
    }
}
