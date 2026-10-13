package net.exylia.exyliaDisplays.common.store;

import java.util.Locale;
import java.util.regex.Pattern;

/** What a hologram, an NPC or anything else this plugin stores by name may be called. */
public final class Names {

    /** What a command can type and a file key can hold. */
    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    private Names() {
        throw new AssertionError("No instances.");
    }

    /** Whether a name can be used. */
    public static boolean valid(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    /** A name turned into one that can be used, for an import: lowercase, no spaces or symbols. */
    public static String sanitize(String name) {
        String cleaned = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "_");
        cleaned = cleaned.replaceAll("^_+|_+$", "");
        if (cleaned.isEmpty()) {
            cleaned = "unnamed";
        }
        return cleaned.length() > 32 ? cleaned.substring(0, 32) : cleaned;
    }
}
