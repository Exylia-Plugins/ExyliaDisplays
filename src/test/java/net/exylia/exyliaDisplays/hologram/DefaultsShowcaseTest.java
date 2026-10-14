package net.exylia.exyliaDisplays.hologram;

import net.exylia.exyliaDisplays.npc.NpcsConfig;
import net.exylia.lib.hologram.HologramLine;
import net.exylia.lib.npc.NpcAnimation;
import net.exylia.lib.npc.NpcAnimations;
import net.exylia.lib.config.Configs;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;
import java.util.logging.Logger;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The templates and presets a new install ships with read without a typo reaching the screen. */
class DefaultsShowcaseTest {

    /** A line tag left in the text: it did not parse, so a player would read it. */
    private static final Pattern LEAKED = Pattern.compile("^<(size|opacity|background|shadow|item|block|head|glow"
            + "|spin|bob|pulse|shine|flow|typewriter|range|perm|if|gap|align|width|billboard|seethrough|brightness"
            + "|autoscale|sway|rainbow|frames|marquee|fade|blink|scramble|button)[:>]");

    private static void assertParses(String where, String raw) {
        String text = HologramLine.parse(raw).text();
        assertFalse(LEAKED.matcher(text).find(), where + ": a tag stayed in the text of '" + raw + "'");
        String again = HologramLine.parse(HologramLine.parse(raw).raw()).text();
        assertFalse(LEAKED.matcher(again).find(), where + ": a tag leaked on the round trip of '" + raw + "'");
    }

    @Test
    @DisplayName("every default template line parses, and every button has its click lines")
    void templates() {
        Map<String, HologramTemplate> templates = new HologramTemplate.Templates().templates();
        assertTrue(templates.size() >= 6);
        templates.forEach((name, template) -> {
            List<String> lines = new ArrayList<>(template.lines());
            template.pages().forEach(page -> lines.addAll(page.lines()));
            lines.forEach(line -> assertParses("template " + name, line));
            lines.stream().map(HologramLine::parse).map(HologramLine::button).filter(b -> b != null && !b.isBlank())
                    .forEach(button -> assertTrue(template.click().buttons().containsKey(button),
                            "template " + name + ": <button:" + button + "> has no click.buttons." + button));
        });
    }

    @Test
    @DisplayName("every hologram preset line parses, and a line preset is tags only")
    void hologramPresets() {
        HologramsConfig config = new HologramsConfig();
        config.presets().forEach((name, preset) -> preset.lines().forEach(line -> assertParses("preset " + name, line)));
        config.linePresets().forEach((name, tags) -> {
            assertEquals("", HologramLine.parse(tags).text(), "line preset " + name + " leaves text: " + tags);
            assertParses("line preset " + name, tags);
        });
    }

    @Test
    @DisplayName("every NPC preset's label parses and its animations know every word")
    void npcPresets() {
        new NpcsConfig().presets().forEach((name, preset) -> {
            preset.label().forEach(line -> assertParses("npc preset " + name, line));
            NpcAnimations animations = preset.body().animations();
            for (String text : List.of(animations.leftClick(), animations.rightClick(), animations.idle(),
                    animations.greet())) {
                List<String> unknown = new ArrayList<>();
                NpcAnimation.parse(text, unknown::add);
                assertTrue(unknown.isEmpty(), "npc preset " + name + ": unknown animation words " + unknown);
            }
        });
    }

    @TempDir
    Path folder;

    @AfterEach
    void release() {
        Configs.releaseAll();
    }

    private HologramTemplate.Templates load(String yaml) throws Exception {
        Files.writeString(folder.resolve("templates.yml"), yaml);
        Plugin plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "DefaultsShowcaseTest";
                    case "getDataFolder" -> folder.toFile();
                    case "getLogger" -> Logger.getLogger("DefaultsShowcaseTest");
                    case "isEnabled" -> false;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
        HologramTemplate.Templates read = Configs.define(plugin, "templates", HologramTemplate.Templates.class)
                .version(2).migration(1, HologramTemplate.Templates.SEED_WHEN_EMPTY).load().get();
        Configs.releaseAll();
        return read;
    }

    @Test
    @DisplayName("an empty templates.yml written before templates shipped is seeded once; one with templates is kept")
    void seed() throws Exception {
        assertEquals(new HologramTemplate.Templates().templates().keySet(), load("templates: {}\n").templates().keySet());
        assertEquals(Set.of("mine"), load("templates:\n  mine:\n    lines:\n    - hi\n").templates().keySet());
        assertTrue(load("config-version: 2\ntemplates: {}\n").templates().isEmpty(), "emptied after seeding stays empty");
    }
}
