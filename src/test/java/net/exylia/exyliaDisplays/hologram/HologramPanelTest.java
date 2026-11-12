package net.exylia.exyliaDisplays.hologram;

import net.exylia.lib.text.Centering;
import net.exylia.lib.text.Text;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HologramPanelTest {

    private static final HologramPanel PANEL = new HologramPanel(true, 160);

    private static int width(String line) {
        return Centering.pixelWidth(Text.component(net.exylia.lib.hologram.HologramLine.parse(line).text()));
    }

    @Test
    void sharesSplitTheSpacesFirstFillsFirst() {
        assertArrayEquals(new int[]{3, 2}, HologramPanel.shares(5, 2));
        assertArrayEquals(new int[]{0}, HologramPanel.shares(0, 1));
    }

    @Test
    void staticLinesReachTheWidthWithinOneSpace() {
        for (String line : List.of("Hi", "&lTOP TEMPORADA", "<fill>right", "a<fill>b<fill>c")) {
            int drawn = width(PANEL.lay(line, "CENTER"));
            assertTrue(drawn <= 160 && drawn > 156, line + " -> " + drawn);
        }
    }

    @Test
    void sizeScalesTheTarget() {
        int drawn = width(PANEL.lay("<size:2>Hi", "CENTER"));
        assertTrue(drawn <= 80 && drawn > 76, "size 2 -> " + drawn);
    }

    @Test
    void placeholdersBecomeFillPlaceholdersAndTagsStayInFront() {
        String laid = PANEL.lay("<if:\"%a_b% == x\"><background:#cc000000>1. %a_name%<fill>%a_value%", "CENTER");
        assertTrue(laid.startsWith("<width:168><if:\"%a_b% == x\"><background:#cc000000>1. %a_name%%exyliadisplays_fill_"), laid);
        assertTrue(laid.endsWith("%%a_value%"), laid);
    }

    @Test
    void modelsAndAPanelThatIsOffAreUntouched() {
        assertEquals("<item:DIAMOND>", PANEL.lay("<item:DIAMOND>", "CENTER"));
        assertEquals(List.of("x"), new HologramPanel().lay(List.of("x"), "CENTER"));
    }
}
