package net.exylia.exyliaHolograms;

/** The permission nodes this plugin checks, as plugin.yml declares them. */
public final class Permissions {

    /** Create, edit, move, import and reload holograms. */
    public static final String ADMIN = "exyliaholograms.admin";

    private Permissions() {
        throw new AssertionError("No instances.");
    }
}
