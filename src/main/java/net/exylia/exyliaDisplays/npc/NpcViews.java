package net.exylia.exyliaDisplays.npc;

import net.exylia.exyliaDisplays.ExyliaDisplays;
import net.exylia.exyliaDisplays.hologram.HologramModule;
import net.exylia.lib.hologram.HologramLine;
import net.exylia.lib.text.Values;
import net.exylia.lib.ui.UiEntry;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * What the NPC screens show, and opening them.
 *
 * <p>Every state word comes from {@code messages/npcs.yml}, so one palette or wording change
 * restyles every screen and message at once.
 */
public final class NpcViews {

    /** The context key every screen about one NPC carries. */
    public static final String NAME = "npc_name";

    private NpcViews() {
        throw new AssertionError("No instances.");
    }

    private static ExyliaDisplays plugin() {
        return ExyliaDisplays.getInstance();
    }

    // ------------------------------------------------------------------
    // Screens
    // ------------------------------------------------------------------

    public static void openList(Player player) {
        List<UiEntry> rows = new ArrayList<>();
        NpcModule.get().store().all().forEach((name, npc) -> rows.add(UiEntry.of(name)
                .with("npc_name", name)
                .with("npc_icon", npc.skin().head())
                .with("npc_world", npc.location().world())
                .with("npc_xyz", xyz(npc))
                .withFormatted("npc_skin", skin(npc))
                .withFormatted("npc_preview", preview(npc))
                .withFormatted("npc_actions", actions(npc))
                .withFormatted("npc_status", status(name, npc))
                .build()));
        plugin().getMenus().show(player, NpcModule.MENU_LIST, Map.of("npc_count", rows.size()), rows);
    }

    public static void openHub(Player player, String name) {
        StoredNpc npc = NpcModule.get().store().get(name).orElse(null);
        if (npc == null) {
            openList(player);
            return;
        }
        plugin().getMenus().show(player, NpcModule.MENU_HUB, context(name, npc), null);
    }

    public static void openEquipment(Player player, String name) {
        StoredNpc npc = NpcModule.get().store().get(name).orElse(null);
        if (npc == null) {
            openList(player);
            return;
        }
        NpcsMessages.States states = NpcsMessages.get().states();
        Map<String, Object> context = context(name, npc);
        for (String slot : StoredNpc.SLOTS) {
            String icon = worn(npc, slot);
            context.put("npc_eq_" + slot, icon.isEmpty() ? "LIGHT_GRAY_STAINED_GLASS_PANE" : icon);
            context.put("npc_eq_" + slot + "_state", icon.isEmpty() ? states.emptySlot() : states.wornSlot());
        }
        plugin().getMenus().show(player, NpcModule.MENU_EQUIPMENT, context, null);
    }

    // ------------------------------------------------------------------
    // Values
    // ------------------------------------------------------------------

    /** Everything a screen about one NPC may show. */
    public static Map<String, Object> context(String name, StoredNpc npc) {
        Map<String, Object> context = new HashMap<>(values(name, npc).map());
        context.put(NAME, name);
        context.put("npc_icon", npc.skin().head());
        context.put("npc_preview", preview(npc));
        context.put("npc_height", number(npc.label().heightOffset()));
        // Changes while the screen is open, so it is read every time the screen redraws.
        context.put("npc_viewers", (Supplier<Object>) () -> NpcModule.get() == null ? 0
                : NpcModule.get().renderer().viewers(name));
        // Live while the screen is open: an edit from a window or a command changes it.
        context.put("npc_undo", (Supplier<Object>) () -> NpcModule.get() == null ? 0 : NpcModule.get().store().undoable(name));
        return context;
    }

    /** The values a message about one NPC may name. */
    public static Values values(String name, StoredNpc npc) {
        NpcsMessages.States states = NpcsMessages.get().states();
        StoredNpc.Interactions click = npc.interactions();
        return Values.of()
                .putText("name", name)
                .put("world", npc.location().world())
                .put("x", number(npc.location().x()))
                .put("y", number(npc.location().y()))
                .put("z", number(npc.location().z()))
                .put("yaw", number(npc.location().yaw()))
                .put("pitch", number(npc.location().pitch()))
                .put("xyz", xyz(npc))
                .put("status", status(name, npc))
                .put("skin", skin(npc))
                .put("pose", npc.pose().name())
                .put("scale", number(npc.scale()))
                .put("glow", glow(npc))
                .put("look", npc.look().mode().name())
                .put("look_range", number(npc.look().range()))
                .put("view_distance", number(npc.viewDistance()))
                .put("permission", permission(npc.permission()))
                .put("label", npc.label().lines().isEmpty() ? states.nothing()
                        : Values.of("count", npc.label().lines().size()).apply(states.labelLines()))
                .put("link", link(npc))
                .put("actions", actions(npc))
                .put("use_permission", permission(click.permission()))
                .put("cooldown", number(click.cooldown() / 1000.0))
                .put("face_clicker", click.faceClicker() ? states.yes() : states.no())
                .put("equipment", npc.equipment().isEmpty() ? states.nothing()
                        : Values.of("count", npc.equipment().size()).apply(states.equipmentCount()));
    }

    /** What is worn in a slot, as its icon string, or empty. */
    static String worn(StoredNpc npc, String slot) {
        return npc.equipment().entrySet().stream()
                .filter(entry -> StoredNpc.slot(entry.getKey()) == StoredNpc.slot(slot))
                .map(Map.Entry::getValue).findFirst().orElse("");
    }

    static String skin(StoredNpc npc) {
        NpcsMessages.States states = NpcsMessages.get().states();
        StoredNpc.Skin skin = npc.skin();
        return switch (skin.type()) {
            case PLAYER -> skin.player().isBlank() ? states.skinNone()
                    : Values.of().putText("player", skin.player()).apply(states.skinPlayer());
            case TEXTURE -> skin.texture().isBlank() ? states.skinNone() : states.skinTexture();
            case MIRROR -> states.skinMirror();
            case NONE -> states.skinNone();
        };
    }

    /** The first label line with text, as the label writes it, for a list row. */
    static String preview(StoredNpc npc) {
        for (String raw : npc.label().lines()) {
            HologramLine line = HologramLine.parse(raw);
            if (!line.isModel() && !line.text().isBlank()) return line.text();
        }
        return NpcsMessages.get().states().nothing();
    }

    static String actions(StoredNpc npc) {
        int count = npc.interactions().actions().size();
        NpcsMessages.States states = NpcsMessages.get().states();
        return count == 0 ? states.nothing() : Values.of("count", count).apply(states.actionCount());
    }

    static String status(String name, StoredNpc npc) {
        NpcsMessages.States states = NpcsMessages.get().states();
        if (!npc.enabled()) return states.disabled();
        return NpcModule.get().renderer().isWaiting(name) ? states.waiting() : states.enabled();
    }

    private static String link(StoredNpc npc) {
        NpcsMessages.States states = NpcsMessages.get().states();
        String hologram = npc.linkedHologram();
        if (hologram.isBlank()) return states.nothing();
        HologramModule holograms = HologramModule.get();
        if (holograms != null && !holograms.store().exists(hologram)) {
            return Values.of().putText("hologram", hologram).apply(states.linkMissing());
        }
        return "{info}" + Values.inert(hologram);
    }

    /** The outline colour, written in itself. */
    private static String glow(StoredNpc npc) {
        int rgb = npc.glowRgb();
        if (rgb < 0) return NpcsMessages.get().states().noGlow();
        String hex = String.format("#%06x", rgb);
        return "<" + hex + ">" + hex;
    }

    private static String permission(String node) {
        return node.isBlank() ? NpcsMessages.get().states().everyone() : "{info}" + Values.inert(node);
    }

    static String xyz(StoredNpc npc) {
        return number(npc.location().x()) + " " + number(npc.location().y()) + " " + number(npc.location().z());
    }

    /** A number as people write it: {@code 2}, {@code 0.25}, never {@code 2.0} or {@code 1E+1}. */
    public static String number(double value) {
        return net.exylia.lib.format.Numbers.trimmed(value, 2);
    }
}
