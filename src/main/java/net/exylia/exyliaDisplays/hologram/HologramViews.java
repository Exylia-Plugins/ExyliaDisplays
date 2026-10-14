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
    /** The context key of the lines screen: which page it edits, from 0. */
    public static final String PAGE = "holo_page_index";

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
        context.put("holo_click_style", click.buttonStyle() ? states.styleButton() : states.styleNone());
        context.put("holo_buttons", click.buttons().isEmpty() ? states.noButtons()
                : Values.of("count", click.buttons().size()).apply(states.buttonCount()));
        context.put("holo_distance", distanceScale(look.distanceScale()));
        context.put("holo_pages", hologram.pageCount());
        context.put("holo_page_interval", hologram.pageInterval());
        // Live while the screen is open: an edit from a window or a command changes them.
        context.put("holo_undo", (java.util.function.Supplier<Object>) () -> HologramModule.get() == null ? 0 : HologramModule.get().store().undoable(name));
        context.put("holo_templates", (java.util.function.Supplier<Object>) () -> HologramModule.get() == null ? 0 : HologramModule.get().templates().names().size());
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
                .put("facing", facing(hologram))
                .put("lines", hologram.lines().size())
                .put("pages", hologram.pageCount())
                .put("status", status(name, hologram))
                .putText("permission_node", hologram.permission())
                .put("permission", hologram.permission().isBlank() ? states.everyone()
                        : "{info}" + Values.inert(hologram.permission()))
                .put("view_distance", number(hologram.viewDistance()))
                .put("per_player", hologram.perPlayer() ? states.yes() : states.no())
                .put("click", click)
                .put("visibility", visibility(hologram))
                .put("visibility_count", hologram.visibility().players().size())
                .put("attached", attached(name, hologram))
                .put("attach_offset", number(hologram.attach().offset()));
    }

    // ------------------------------------------------------------------
    // Who sees it, and what it rides
    // ------------------------------------------------------------------

    /** The mode, and how many players its list names. */
    static String visibility(StoredHologram hologram) {
        HologramsMessages.Audience words = HologramsMessages.get().audience();
        StoredHologram.Visibility visibility = hologram.visibility();
        return (visibility.manual() ? words.manual() : words.all())
                + " {letters_black}(" + (visibility.manual() ? words.shownToLabel() : words.hiddenFromLabel())
                + " {info}" + visibility.players().size() + "{letters_black})";
    }

    /** The players a list of UUIDs names, by their last known name. */
    static String players(List<String> ids) {
        if (ids.isEmpty()) return HologramsMessages.get().audience().nobody();
        List<String> names = new ArrayList<>();
        for (String id : ids) {
            String known = null;
            try {
                known = org.bukkit.Bukkit.getOfflinePlayer(java.util.UUID.fromString(id)).getName();
            } catch (IllegalArgumentException typo) {
                // A hand edit that is not a UUID is shown as written.
            }
            names.add(Values.inert(known == null ? id : known));
        }
        return String.join(", ", names);
    }

    /** What it rides: nothing, the entity it rides now, or the one it waits for. */
    static String attached(String name, StoredHologram hologram) {
        HologramsMessages.Attach words = HologramsMessages.get().attach();
        String npc = HologramActions.linkedNpc(name);
        if (npc != null) return Values.of().putText("target", HologramActions.NPC + npc).apply(words.riding());
        HologramRenderer renderer = HologramModule.get().renderer();
        java.util.UUID target = renderer.target(name, hologram);
        if (target == null) return words.none();
        // The entity being loaded is enough: mounting it takes a tick, and the screen opens before.
        org.bukkit.entity.Entity riding = org.bukkit.Bukkit.getEntity(target);
        String label = riding != null ? riding.getName() : targetName(target);
        return Values.of().putText("target", label).apply(riding != null ? words.riding() : words.away());
    }

    /** A player's last known name, else the UUID, for an entity that is not loaded. */
    static String targetName(java.util.UUID target) {
        String known = org.bukkit.Bukkit.getOfflinePlayer(target).getName();
        return known != null ? known : target.toString();
    }

    // ------------------------------------------------------------------
    // The lines
    // ------------------------------------------------------------------

    public static void openLines(Player player, String name) {
        openLines(player, name, 0);
    }

    /** One page's lines; a page that no longer exists shows the last one. */
    public static void openLines(Player player, String name, int page) {
        StoredHologram hologram = HologramModule.get().store().get(name).orElse(null);
        if (hologram == null) {
            openList(player);
            return;
        }
        int shown = Math.clamp(page, 0, hologram.pageCount() - 1);
        List<UiEntry> rows = new ArrayList<>();
        List<String> lines = hologram.pageView(shown).lines();
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
        Map<String, Object> context = context(name, hologram);
        context.put(PAGE, shown);
        context.put("holo_page", shown + 1);
        plugin().getMenus().show(player, HologramModule.MENU_LINES, context, rows);
    }

    /** How a line reads in a lore: its text, or what model it is. */
    public static String lineText(HologramLine line) {
        HologramsMessages.States states = HologramsMessages.get().states();
        if (line.item() != null) return Values.of("material", line.item()).apply(states.itemLine());
        if (line.block() != null) return Values.of("material", line.block()).apply(states.blockLine());
        if (line.head() != null) return Values.of().putText("head", shortHead(line.head())).apply(states.headLine());
        return line.text().isBlank() ? states.emptyLine() : line.text();
    }

    /** A head as a lore can show it: a long texture is cut short. */
    private static String shortHead(String head) {
        return head.length() > 24 ? head.substring(0, 21) + "..." : head;
    }

    private static String lineIcon(HologramLine line) {
        if (line.head() != null) return "PLAYER_HEAD";
        String model = line.item() != null ? line.item() : line.block();
        if (model != null && Material.matchMaterial(model) != null) return model;
        if (line.text().isBlank()) return "LIGHT_GRAY_STAINED_GLASS_PANE";
        if (line.typewriter() != null || line.spin() != null || line.bob() != null
                || line.pulse() != null || line.shine() != null || line.flow() != null || line.sway() != null
                || line.rainbow() != null || line.frames() != null || line.marquee() != null || line.fade() != null
                || line.blink() != null || line.scramble() != null) return "AMETHYST_SHARD";
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
            if (line.head() != null) return "PLAYER_HEAD";
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

    /** Whether it grows with distance, and along which curve, as the hub and messages show it. */
    static String distanceScale(HologramConfig.DistanceScale curve) {
        HologramsMessages.States states = HologramsMessages.get().states();
        if (!curve.enabled()) return states.distanceOff();
        return Values.of().put("start", number(curve.start())).put("end", number(curve.end()))
                .put("min", number(curve.min())).put("max", number(curve.max())).apply(states.distanceOn());
    }

    /** Which way it faces: turn, then tilt. */
    static String facing(StoredHologram hologram) {
        return number(hologram.location().yaw()) + "° " + number(hologram.location().pitch()) + "°";
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
