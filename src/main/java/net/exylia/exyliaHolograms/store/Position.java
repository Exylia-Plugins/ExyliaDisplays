package net.exylia.exyliaHolograms.store;

import net.exylia.lib.config.Comment;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Where a hologram stands: the bottom of its last line.
 *
 * @param world the world's name
 * @param x     east-west
 * @param y     height
 * @param z     north-south
 */
public record Position(
        @Comment("The world's folder name.")
        String world,
        double x,
        double y,
        double z) {

    public Position() {
        this("world", 0.5, 100.0, 0.5);
    }

    public Position {
        if (world == null || world.isBlank()) {
            world = "world";
        }
    }

    /** A location, kept to a thousandth of a block so the file stays readable. */
    public static Position of(Location location) {
        return new Position(location.getWorld() == null ? "world" : location.getWorld().getName(),
                round(location.getX()), round(location.getY()), round(location.getZ()));
    }

    /** The location, or {@code null} while its world is not loaded. */
    public Location toLocation() {
        World loaded = Bukkit.getWorld(world);
        return loaded == null ? null : new Location(loaded, x, y, z);
    }

    /** The same place, moved to the centre of the block it is in. */
    public Position centred() {
        return new Position(world, Math.floor(x) + 0.5, y, Math.floor(z) + 0.5);
    }

    /** The same place, shifted. */
    public Position plus(double dx, double dy, double dz) {
        return new Position(world, round(x + dx), round(y + dy), round(z + dz));
    }

    static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
