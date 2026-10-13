package net.exylia.exyliaDisplays.npc;

import net.exylia.lib.config.Comment;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code npcs.yml}: every NPC on this server, by name.
 *
 * @param npcs the NPCs, in the order they were created
 */
@Comment("Every NPC on this server, by name.")
@Comment("")
@Comment("Edit them in game with /npc, or right here: saving this file applies the change")
@Comment("within a second, without a reload. A hand-written NPC only needs a location.")
public record NpcFile(
        @Comment("One block per NPC. The name is what /npc commands take.")
        Map<String, StoredNpc> npcs) {

    public NpcFile() {
        this(Map.of());
    }

    public NpcFile {
        npcs = npcs == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(npcs));
    }
}
