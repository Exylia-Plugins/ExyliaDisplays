package net.exylia.exyliaDisplays.demo;

import net.exylia.exyliaDisplays.npc.StoredNpc;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DemoShowcaseTest {

    @Test
    void everyNpcWearsItsOwnSignedSkin() {
        assertEquals(DemoShowcase.SKIN_COUNT, DemoShowcase.SKINS.size());
        assertEquals(DemoShowcase.SKIN_COUNT, DemoShowcase.SKINS.stream().map(StoredNpc.Skin::texture).distinct().count());
        DemoShowcase.SKINS.forEach(skin -> assertFalse(skin.signature().isBlank()));
    }

    @Test
    void givenNamesComeFirstAndBuiltInSkinsFillTheRest() {
        List<StoredNpc.Skin> given = List.of(StoredNpc.Skin.player("Notch"));
        assertEquals(given.get(0), DemoShowcase.skin(given, 0));
        assertEquals(DemoShowcase.SKINS.get(1), DemoShowcase.skin(given, 1));
    }
}
