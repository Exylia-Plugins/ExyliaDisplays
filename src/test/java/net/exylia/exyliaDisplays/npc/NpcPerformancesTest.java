package net.exylia.exyliaDisplays.npc;

import net.exylia.lib.npc.NpcAnimations;
import net.exylia.lib.ragdoll.RagdollAnimations;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Performances: what is checked before one is saved, and ExyliaEmotes' file read into the catalog. */
class NpcPerformancesTest {

    @TempDir
    Path folder;

    @AfterEach
    void forget() {
        NpcPerformances.use(null);
    }

    private static Map<String, NpcPerformances.Emote> sample() {
        var stream = Objects.requireNonNull(NpcPerformancesTest.class.getClassLoader().getResourceAsStream("emotes/emotes.yml"));
        return NpcPerformances.read(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("emotes.yml reads into the catalog: solo emotes with their name and item, duets and empty ones left out")
    void readsEmotes() {
        Map<String, NpcPerformances.Emote> emotes = sample();
        assertEquals(List.of("bow", "wave"), List.copyOf(emotes.keySet()));
        NpcPerformances.Emote bow = emotes.get("bow");
        assertEquals("{primary}&lBOW", bow.name());
        assertEquals("FEATHER", bow.material());
        assertEquals(4, bow.performance().lines().size());
        assertTrue(bow.performance().lines().get(0).startsWith("[RAGDOLL] {victim};"));
        assertEquals("{primary}&lWAVE", emotes.get("wave").name(), "a missing name falls back to the id");

        NpcPerformances.use(new NpcPerformances.Catalog(true, emotes, Map::of));
        assertNotNull(NpcPerformances.emote("bow"));
        assertNotNull(NpcPerformances.emote("BOW"), "ids are matched without case");
        assertNull(NpcPerformances.emote("handshake"));
    }

    /** A folder of one Blockbench project with one clip, as ExyliaLib lists it. */
    private static Map<String, List<RagdollAnimations.Clip>> dance() {
        return Map.of("dance.bbmodel", List.of(new RagdollAnimations.Clip("dance", List.of("dance"), true,
                Duration.ofMillis(1600), Duration.ZERO)));
    }

    @Test
    @DisplayName("the Blockbench templates are written once, and their wave clip is what anim: offers")
    void templates() throws Exception {
        assertEquals(2, RagdollAnimations.writeTemplates(folder.toFile()).size());
        assertEquals(0, RagdollAnimations.writeTemplates(folder.toFile()).size(), "a file already there is kept");
        List<String> problems = new java.util.ArrayList<>();
        Map<String, List<RagdollAnimations.Clip>> files = new java.util.TreeMap<>();
        for (String file : List.of("player_classic.bbmodel", "player_slim.bbmodel")) {
            files.put(file, RagdollAnimations.clips(Files.readString(folder.resolve(file)), file, problems::add));
        }
        assertTrue(problems.isEmpty(), problems.toString());
        NpcPerformances.use(new NpcPerformances.Catalog(false, Map.of(), () -> files));
        assertEquals(List.of("wave"), NpcPerformances.catalog().animations(), "the same clip in both is offered once");
        assertEquals(List.of("anim:wave"), NpcPerformances.names());
        assertEquals("player_slim", NpcAdminCommand.base("player_slim.bbmodel"));
        assertEquals("bow", NpcAdminCommand.base("bow.animation.json"));
    }

    @Test
    @DisplayName("vanilla moves are left to their own parser; anim: and keys: are checked")
    void validation() {
        assertNull(NpcPerformances.problem("wave"));
        assertNull(NpcPerformances.problem("swing, 250ms, hurt"));
        assertNull(NpcPerformances.problem(""));
        assertNull(NpcPerformances.problem("anim:dance"));
        assertNull(NpcPerformances.problem("follow:2;breathe:0;keys:0.3 arm_r=150,0,20 | 0.4 stand"));
        assertNull(NpcPerformances.problem(NpcsConfig.GREETER_BOW));

        String frames = NpcPerformances.problem("keys:0.3 bogus=1 | x");
        assertNotNull(frames);
        assertTrue(frames.contains("bogus"), frames);
        assertNotNull(NpcPerformances.problem("keys:"));
        assertNotNull(NpcPerformances.problem("anim:"));
        assertNotNull(NpcPerformances.problem("nonsense:1"));
    }

    @Test
    @DisplayName("emote: needs ExyliaEmotes, and an emote it has")
    void emoteValidation() {
        String missing = NpcPerformances.problem("emote:bow");
        assertNotNull(missing);
        assertTrue(missing.contains("ExyliaEmotes"), missing);

        NpcPerformances.use(new NpcPerformances.Catalog(true, sample(), NpcPerformancesTest::dance));
        assertNull(NpcPerformances.problem("emote:bow"));
        String unknown = NpcPerformances.problem("emote:moonwalk");
        assertNotNull(unknown);
        assertTrue(unknown.contains("moonwalk") && unknown.contains("bow"), unknown);
        assertNotNull(NpcPerformances.problem("emote:handshake"), "a duet is not offered");
    }

    @Test
    @DisplayName("the performance windows and commands read emote: and anim: values")
    void dialogsAndSuggestions() {
        NpcPerformances.use(new NpcPerformances.Catalog(true, sample(), NpcPerformancesTest::dance));
        assertTrue(NpcDialogs.unknownWords("emote:bow").isEmpty());
        assertFalse(NpcDialogs.unknownWords("wave, jump").isEmpty());
        assertEquals(List.of("emote:bow", "emote:wave", "anim:dance"), NpcPerformances.names());

        List<String> offered = NpcSuggestions.suggest(new StoredNpc(), List.of("npc", "guide", "play", ""), null, List.of());
        assertTrue(offered.containsAll(List.of("wave", "emote:bow", "anim:dance")), offered.toString());
        List<String> trigger = NpcSuggestions.animation(new NpcAnimations(), "greet");
        assertTrue(trigger.containsAll(List.of("emote:wave", "anim:dance")), trigger.toString());
        assertTrue(NpcSuggestions.animation(new NpcAnimations(), "perform-range").contains("24"));
        assertEquals(List.of("off", "on"), NpcSuggestions.animation(new NpcAnimations(), "idle-continuous"));
        assertEquals(List.of("stage", "none"), NpcSuggestions.animation(new NpcAnimations().withIdleGroup("stage"), "idle-group"));
    }
}
