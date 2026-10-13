package net.exylia.exyliaDisplays;

/** The permission nodes this plugin checks, as plugin.yml declares them. */
public final class Permissions {

    /** Every module's editing, /displays reload and imports. */
    public static final String ADMIN = "exyliadisplays.admin";

    /** Create, edit, move and import holograms with /holo. */
    public static final String HOLOGRAMS = "exyliadisplays.holograms";

    /** Create, edit, move and import NPCs with /npc. */
    public static final String NPCS = "exyliadisplays.npcs";

    private Permissions() {
        throw new AssertionError("No instances.");
    }
}
