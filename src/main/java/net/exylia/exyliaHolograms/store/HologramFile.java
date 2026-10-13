package net.exylia.exyliaHolograms.store;

import net.exylia.lib.config.Comment;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code holograms.yml}: every hologram on this server, by name.
 *
 * @param holograms the holograms, in the order they were created
 */
@Comment("Every hologram on this server, by name.")
@Comment("")
@Comment("Edit them in game with /holo, or right here: saving this file applies the change")
@Comment("within a second, without a reload. Every key is described in the README.")
public record HologramFile(
        @Comment("One block per hologram. The name is what /holo commands take.")
        Map<String, StoredHologram> holograms) {

    public HologramFile() {
        this(Map.of());
    }

    public HologramFile {
        holograms = holograms == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(holograms));
    }
}
