package net.exylia.exyliaDisplays.hologram;

import net.exylia.exyliaDisplays.common.store.Position;
import net.exylia.lib.hologram.HologramConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** What a fine adjustment writes, and what it leaves to whoever else edited the hologram. */
class HologramAdjustTest {

    @Test
    @DisplayName("saving keeps the place, facing, scale and spacing it adjusted, and everything else as saved now")
    void mergeTakesOnlyWhatItAdjusts() {
        StoredHologram original = new StoredHologram().withLines(List.of("a"));
        StoredHologram work = HologramAdjust.scaled(original, 2)
                .withFacing(90, 0)
                .withLocation(new Position("world", 1, 2, 3, 90, 0));
        StoredHologram editedMeanwhile = original.withLines(List.of("a", "b")).withPermission("vip");

        StoredHologram saved = HologramAdjust.merge(editedMeanwhile, work);

        assertEquals(List.of("a", "b"), saved.lines());
        assertEquals("vip", saved.permission());
        assertEquals(work.location(), saved.location());
        assertEquals("FIXED", saved.appearance().billboard());
        assertEquals(2, saved.appearance().scaleY());
    }

    @Test
    @DisplayName("scaling keeps the proportions of a hologram scaled per axis")
    void scaleKeepsProportions() {
        HologramConfig.Properties p = new StoredHologram().appearance();
        StoredHologram stretched = new StoredHologram().withAppearance(new HologramConfig.Properties(p.billboard(),
                p.alignment(), 3, 1.5, 1.5, p.shadow(), p.seeThrough(), p.lineWidth(), p.lineSpacing(),
                p.textOpacity(), p.defaultBackground(), p.backgroundColor(), p.brightness(), p.glowing()));

        HologramConfig.Properties twice = HologramAdjust.scaled(stretched, 3).appearance();

        assertEquals(6, twice.scaleX(), 1e-9);
        assertEquals(3, twice.scaleY(), 1e-9);
        assertEquals(3, twice.scaleZ(), 1e-9);
    }
}
