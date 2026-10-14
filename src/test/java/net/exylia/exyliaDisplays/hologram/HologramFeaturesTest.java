package net.exylia.exyliaDisplays.hologram;

import net.exylia.lib.hologram.HologramConfig;
import net.exylia.lib.hologram.HologramLine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Size by distance, the button look, presets and the new line tags, as edits keep and write them. */
class HologramFeaturesTest {

    private static final HologramConfig.DistanceScale CURVE = new HologramConfig.DistanceScale(true, 4, 20, 1.5, 3);

    private static StoredHologram dressed() {
        StoredHologram hologram = new StoredHologram().withLines(List.of("a"));
        return hologram
                .withAppearance(hologram.appearance().withDistanceScale(CURVE))
                .withClick(new HologramConfig.Click(List.of("spawn"), 2, 500, 1.1, HologramConfig.Click.STYLE_NONE,
                        "{accent}", "{info}", "UI_BUTTON_CLICK;1;1", Map.of("join", List.of("arena join"))));
    }

    @Test
    @DisplayName("editing the appearance keeps size by distance")
    void appearanceKeepsDistanceScale() {
        StoredHologram hologram = dressed();
        assertEquals(CURVE, HologramAdjust.scaled(hologram, 2).appearance().distanceScale());
        assertEquals(CURVE, HologramAdjust.merge(hologram, HologramAdjust.scaled(hologram, 3)).appearance().distanceScale());
        assertEquals(CURVE, hologram.withFacing(90, 0).appearance().distanceScale());
        assertEquals("FIXED", hologram.withFacing(90, 0).appearance().billboard());
    }

    @Test
    @DisplayName("editing the click actions keeps the button look, the sound and the buttons")
    void clickKeepsStyleAndButtons() {
        HologramConfig.Click before = dressed().click();
        HologramConfig.Click after = StoredHologram.clickWith(before, List.of("warp"), 3, 0, 1);
        assertEquals(List.of("warp"), after.commands());
        assertEquals(3, after.width());
        assertEquals(before.style(), after.style());
        assertEquals(before.color(), after.color());
        assertEquals(before.hoverColor(), after.hoverColor());
        assertEquals(before.sound(), after.sound());
        assertEquals(before.buttons(), after.buttons());

        StoredHologram paged = dressed().addPage().removePage(0);
        assertEquals(before.buttons(), paged.click().buttons(), "removing the first page keeps the buttons");
        assertFalse(paged.click().buttonStyle());
    }

    @Test
    @DisplayName("a preset replaces the line's own tags of the same kind and keeps its text and other tags")
    void presetMerges() {
        String raw = "<size:2><shine:accent,speed=20><range:8>{primary}&lTHE ARENA";
        HologramLine merged = HologramLine.parse(LineStyle.withPreset(raw, "<size:1.6><gap:0.15><shine>"));
        assertEquals("{primary}&lTHE ARENA", merged.text());
        assertEquals(1.6, merged.size());
        assertEquals(0.15, merged.gap());
        assertEquals("", merged.shine());
        assertNull(merged.shineSpeed(), "the preset's bare shine drops the old speed");
        assertEquals(8.0, merged.range());
        assertEquals("<gradient:#fff:#000>Hi", HologramLine.parse(LineStyle.withPreset("<gradient:#fff:#000>Hi",
                "<fade:4>trailing text is ignored")).text());
    }

    @Test
    @DisplayName("every preset config.yml ships with is tags only, and they all read")
    void defaultPresetsRead() {
        new HologramsConfig().linePresets().forEach((name, tags) -> {
            HologramLine line = HologramLine.parse(tags);
            assertEquals("", line.text(), name + " leaves text behind: a tag does not read");
        });
    }

    @Test
    @DisplayName("/holo <name> distancescale reads on, off and four numbers")
    void distanceScaleCommand() {
        HologramConfig.DistanceScale off = new HologramConfig.DistanceScale();
        HologramConfig.DistanceScale on = HologramCommand.distanceScale(off, "on");
        assertTrue(on.enabled());
        assertEquals(off.start(), on.start());
        assertFalse(HologramCommand.distanceScale(CURVE, "off").enabled());
        assertEquals(CURVE.max(), HologramCommand.distanceScale(CURVE, "off").max());
        assertEquals(new HologramConfig.DistanceScale(true, 6, 30, 1, 2), HologramCommand.distanceScale(off, " 6  30 1 2 "));
        assertNull(HologramCommand.distanceScale(off, "6 30 1"));
        assertNull(HologramCommand.distanceScale(off, "30 6 1 2"), "end before start");
        assertNull(HologramCommand.distanceScale(off, "6 30 0 2"), "a size under 0.1");
        assertNull(HologramCommand.distanceScale(off, "6 30 big 2"));
        assertNull(HologramCommand.distanceScale(off, "maybe"));
    }

    @Test
    @DisplayName("/holo <name> line <n> <tag> writes a tag with its options, and takes it off with them")
    void lineTagCommand() {
        String swaying = HologramCommand.written("<size:2>Hi", "sway", "20,period=6");
        HologramLine line = HologramLine.parse(swaying);
        assertEquals(20.0, line.sway());
        assertEquals(6.0, line.swayPeriod());
        assertEquals(2.0, line.size());
        assertEquals("Hi", line.text());
        assertEquals(HologramLine.DEFAULT_RAINBOW, HologramLine.parse(HologramCommand.written("Hi", "rainbow", "on")).rainbow());
        assertEquals("left", HologramLine.parse(HologramCommand.written("Hi", "align", "LEFT")).align());
        assertNull(HologramCommand.written("Hi", "align", "diagonal"));
        assertNull(HologramCommand.written("Hi", "marquee", "wide"));
        HologramLine cleared = HologramLine.parse(HologramCommand.cleared(swaying, "sway"));
        assertNull(cleared.sway());
        assertNull(cleared.swayPeriod());
        assertEquals(2.0, cleared.size());
    }

    @Test
    @DisplayName("frames typed one per row are joined as the line takes turns between them")
    void framesJoin() {
        assertEquals("{letters}Open now||{letters}Rewards doubled", LineStyle.joinFrames("{letters}Open now\n{letters}Rewards doubled\n\n"));
        assertEquals("Hi", LineStyle.joinFrames("Hi"));
    }
}
