package net.exylia.exyliaDisplays.hologram;

import net.exylia.exyliaDisplays.ExyliaDisplays;
import net.exylia.exyliaDisplays.hologram.HologramsMessages;
import net.exylia.exyliaDisplays.hologram.StoredHologram;
import net.exylia.lib.hologram.HologramConfig;
import net.exylia.lib.hologram.HologramLine;
import net.exylia.lib.text.Values;
import net.exylia.lib.ui.UiEntry;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the three screens show, and opening them.
 *
 * <p>Every state word comes from {@code messages.yml}, so one palette or wording change restyles
 * every screen and message at once.
 */
public final class HologramViews {

    /** The context key every screen about one hologram carries. */
    public static final String NAME = "holo_name";

    private HologramViews() {
        throw new AssertionError("No instances.");
    }

    private static ExyliaDisplays plugin() {
        return ExyliaDisplays.getInstance();
    }

    // ------------------------------------------------------------------
    // The list
    // ------------------------------------------------------------------

    public static void openList(Player player) {
        List<UiEntry> rows = new ArrayList<>();
        HologramModule.get().store().all().forEach((name, hologram) -> rows.add(UiEntry.of(name)
                .with("holo_name", name)
                .with("holo_world", hologram.location().world())
                .with("holo_xyz", xyz(hologram))
                .with("holo_lines", hologram.lines().size())
                .with("holo_icon", icon(hologram))
                .withFormatted("holo_status", status(name, hologram))
                .withFormatted("holo_preview", preview(hologram))
                .build()));
        plugin().getMenus().show(player, HologramModule.MENU_LIST,
                Map.of("holo_count", rows.size()), rows);
    }

    // ------------------------------------------------------------------
    // The hub
    // ------------------------------------------------------------------

    public static void openHub(Player player, String name) {
        StoredHologram hologram = HologramModule.get().store().get(name).orElse(null);
        if (hologram == null) {
            openList(player);
            return;
        }
        plugin().getMenus().show(player, HologramModule.MENU_HUB, context(name, hologram), null);
    }

    /** Everything a screen about one hologram may show. */
    public static Map<String, Object> context(String name, StoredHologram hologram) {
        HologramsMessages.States states = HologramsMessages.get().states();
        HologramConfig.Properties look = hologram.appearance();
        HologramConfig.Click click = hologram.click();
        Map<String, Object> context = new HashMap<>(values(name, hologram).map());
        context.put(NAME, name);
        context.put("holo_toggle_icon", hologram.enabled() ? "LIME_DYE" : "GRAY_DYE");
        context.put("holo_icon", icon(hologram));
        context.put("holo_preview", preview(hologram));
        context.put("holo_billboard", look.billboard());
        context.put("holo_scale", number(look.scaleY()));
        context.put("holo_spacing", number(look.lineSpacing()));
        context.put("holo_background", look.backgroundColor());
        context.put("holo_shadow", look.shadow() ? states.yes() : states.no());
        context.put("holo_interval", hologram.refresh().updateInterval());
        context.put("holo_click_width", number(click.width()));
        context.put("holo_hover", number(click.hoverScale()));
        return context;
    }

    /** The values a message about one hologram may name. */
    public static Values values(String name, StoredHologram hologram) {
        HologramsMessages.States states = HologramsMessages.get().states();
        String click = hologram.click().commands().isEmpty()
                ? states.noClick()
                : Values.of("count", hologram.click().commands().size()).apply(states.clickCount());
        return Values.of()
                .putText("name", name)
                .put("world", hologram.location().world())
                .put("x", number(hologram.location().x()))
                .put("y", number(hologram.location().y()))
                .put("z", number(hologram.location().z()))
                .put("xyz", xyz(hologram))
                .put("lines", hologram.lines().size())
                .put("status", status(name, hologram))
                .putText("permission_node", hologram.permission())
                .put("permission", hologram.permission().isBlank() ? states.everyone()
                        : "{info}" + Values.inert(hologram.permission()))
                .put("view_distance", number(hologram.viewDistance()))
                .put("per_player", hologram.perPlayer() ? states.yes() : states.no())
                .put("click", click);
    }

    // ------------------------------------------------------------------
    // The lines
    // ------------------------------------------------------------------

    public static void openLines(Player player, String name) {
        StoredHologram hologram = HologramModule.get().store().get(name).orElse(null);
        if (hologram == null) {
            openList(player);
            return;
        }
        List<UiEntry> rows = new ArrayList<>();
        List<String> lines = hologram.lines();
        for (int i = 0; i < lines.size(); i++) {
            HologramLine line = HologramLine.parse(lines.get(i));
            String tags = line.raw().substring(0, line.raw().length() - line.text().length());
            rows.add(UiEntry.of(i)
                    .with("line_number", i + 1)
                    .with("line_icon", lineIcon(line))
                    .withFormatted("line_text", lineText(line))
                    .with("line_tags", tags.isEmpty() ? "" : tags)
                    .withFormatted("line_style", tags.isEmpty() ? HologramsMessages.get().states().noStyle() : "")
                    .build());
        }
        plugin().getMenus().show(player, HologramModule.MENU_LINES, context(name, hologram), rows);
    }

    /** How a line reads in a lore: its text, or what model it is. */
    public static String lineText(HologramLine line) {
        HologramsMessages.States states = HologramsMessages.get().states();
        if (line.item() != null) return Values.of("material", line.item()).apply(states.itemLine());
        if (line.block() != null) return Values.of("material", line.block()).apply(states.blockLine());
        return line.text().isBlank() ? states.emptyLine() : line.text();
    }

    private static String lineIcon(HologramLine line) {
        String model = line.item() != null ? line.item() : line.block();
        if (model != null && Material.matchMaterial(model) != null) return model;
        if (line.text().isBlank()) return "LIGHT_GRAY_STAINED_GLASS_PANE";
        if (line.typewriter() != null || line.spin() != null || line.bob() != null
                || line.pulse() != null || line.shine() != null || line.flow() != null) return "AMETHYST_SHARD";
        if (line.range() != null || line.permission() != null || line.condition() != null) return "ENDER_EYE";
        return "PAPER";
    }

    // ------------------------------------------------------------------
    // Shared
    // ------------------------------------------------------------------

    /** The first line the hologram draws as a model, else a sign. */
    static String icon(StoredHologram hologram) {
        for (String raw : hologram.lines()) {
            HologramLine line = HologramLine.parse(raw);
            String model = line.item() != null ? line.item() : line.block();
            if (model != null && Material.matchMaterial(model) != null) return model;
        }
        return "OAK_HANGING_SIGN";
    }

    /** The first line with text, as the hologram writes it, for a list row. */
    static String preview(StoredHologram hologram) {
        for (String raw : hologram.lines()) {
            HologramLine line = HologramLine.parse(raw);
            if (!line.isModel() && !line.text().isBlank()) return line.text();
        }
        return HologramsMessages.get().states().emptyLine();
    }

    static String status(String name, StoredHologram hologram) {
        HologramsMessages.States states = HologramsMessages.get().states();
        if (!hologram.enabled()) return states.disabled();
        return HologramModule.get().renderer().isWaiting(name) ? states.waiting() : states.enabled();
    }

    static String xyz(StoredHologram hologram) {
        return number(hologram.location().x()) + " " + number(hologram.location().y()) + " "
                + number(hologram.location().z());
    }

    /** A number as people write it: {@code 2}, {@code 0.25}, never {@code 2.0} or {@code 1E+1}. */
    public static String number(double value) {
        return net.exylia.lib.format.Numbers.trimmed(value, 2);
    }
}
