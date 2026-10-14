package net.exylia.exyliaDisplays.npc.migration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.exylia.exyliaDisplays.common.store.Place;
import net.exylia.exyliaDisplays.npc.StoredNpc;
import net.exylia.lib.npc.NpcLook;
import net.exylia.lib.npc.NpcPose;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * NPCs brought over from Citizens and FancyNpcs, read from their save files.
 *
 * <p>Their files are read and never changed, so trying an import costs nothing. What comes across
 * is what those plugins share with this one: place and facing, whether it is shown, skin, the name
 * and the lines over it, pose, size, glow, held and worn items, turning to players, who sees it,
 * and click actions with their cooldown. What has no counterpart here is left out with a comment
 * where it is read; a click action that only some players may run (a permission, a price, a use
 * limit) is left out rather than run for everyone. A non-player NPC is skipped rather than
 * imported as a person.
 *
 * <p>Both formats are read as those plugins write them, from their source, with the older
 * spellings they still read.
 */
public final class NpcImport {

    /** Where to read from. */
    public enum Source {
        CITIZENS("Citizens"),
        FANCYNPCS("FancyNpcs");

        private final String folder;

        Source(String folder) {
            this.folder = folder;
        }

        public String folder() {
            return folder;
        }
    }

    /**
     * What an import found.
     *
     * @param npcs    the NPCs read, by their name there
     * @param skipped how many could not be read, or were not players
     */
    public record Result(Map<String, StoredNpc> npcs, int skipped) {
    }

    private NpcImport() {
        throw new AssertionError("No instances.");
    }

    /** The other plugin's folder, next to this one's. */
    public static File folder(Plugin plugin, Source source) {
        return new File(plugin.getDataFolder().getParentFile(), source.folder());
    }

    public static Result run(Plugin plugin, Source source) {
        File folder = folder(plugin, source);
        return switch (source) {
            case CITIZENS -> citizens(new File(folder, "saves.yml"));
            case FANCYNPCS -> fancy(new File(folder, "npcs.yml"));
        };
    }

    // ------------------------------------------------------------------
    // Citizens: saves.yml, npc.<id>.traits.*
    // ------------------------------------------------------------------

    static Result citizens(File file) {
        Map<String, StoredNpc> found = new LinkedHashMap<>();
        int skipped = 0;
        if (!file.isFile()) return new Result(found, 0);
        ConfigurationSection all = YamlConfiguration.loadConfiguration(file).getConfigurationSection("npc");
        if (all == null) return new Result(found, 0);
        for (String id : all.getKeys(false)) {
            ConfigurationSection npc = all.getConfigurationSection(id);
            ConfigurationSection traits = npc == null ? null : npc.getConfigurationSection("traits");
            Place at = traits == null ? null : place(traits.getConfigurationSection("location"));
            String type = traits == null ? "PLAYER" : traits.getString("type", "PLAYER");
            if (at == null || !"PLAYER".equalsIgnoreCase(type)) {
                skipped++;
                continue;
            }
            String name = npc.getString("name", "npc-" + id);
            StoredNpc imported = new StoredNpc().withLocation(at)
                    .withEnabled(traits.getBoolean("spawned", true))
                    .withSkin(traits.getBoolean("mirrortrait.enabled", false) ? StoredNpc.Skin.mirror()
                            : citizensSkin(traits.getConfigurationSection("skintrait"), name))
                    .withLabelLines(citizensLabel(npc, traits, name))
                    .withPose(citizensPose(traits))
                    .withScale(citizensScale(traits.getConfigurationSection("attributetrait.attributes")));
            if (npc.getBoolean("metadata.glowing", false)) {
                // The outline takes the colour of its scoreboard team there.
                NamedTextColor colour = NamedTextColor.NAMES.value(
                        traits.getString("scoreboardtrait.color", "white").toLowerCase(Locale.ROOT));
                imported = imported.withGlow((colour == null ? NamedTextColor.WHITE : colour).asHexString());
            }
            ConfigurationSection look = traits.getConfigurationSection("lookclose");
            if (look != null && look.getBoolean("enabled", false)) {
                imported = imported.withLook(new StoredNpc.Look(look.getBoolean("perplayer", false)
                        ? NpcLook.EACH_VIEWER : NpcLook.NEAREST, number(look.get("range"), 8)));
            } else {
                imported = imported.withLook(new StoredNpc.Look(NpcLook.FIXED, 8));
            }
            imported = equipment(imported, traits.getConfigurationSection("equipment"));
            imported = citizensCommands(imported, traits.getConfigurationSection("commandtrait"));
            found.put(plain(name).isBlank() ? "npc-" + id : plain(name), imported);
        }
        return new Result(found, skipped);
    }

    /** A written texture first, then a named account, then Citizens' own default: the NPC's name. */
    private static StoredNpc.Skin citizensSkin(ConfigurationSection skin, String name) {
        if (skin != null) {
            String texture = skin.getString("textureRaw", "");
            if (!texture.isBlank()) return StoredNpc.Skin.texture(texture, skin.getString("signature", ""));
            String account = skin.getString("skinName", "");
            if (!account.isBlank()) return StoredNpc.Skin.player(account);
            if (!skin.getBoolean("fetchDefaultSkin", true)) return new StoredNpc.Skin();
        }
        StoredNpc.Skin fallback = StoredNpc.Skin.parse(plain(name));
        return fallback == null ? new StoredNpc.Skin() : fallback;
    }

    /**
     * The hologram trait's lines, which stack upwards from just over the name there (so the last
     * is the top one), then the name, unless its nameplate is hidden. A line's own margins are
     * left out: the label keeps one gap.
     */
    private static List<String> citizensLabel(ConfigurationSection npc, ConfigurationSection traits, String name) {
        List<String> lines = new ArrayList<>();
        ConfigurationSection hologram = traits.getConfigurationSection("hologramtrait.lines");
        if (hologram != null) {
            for (String key : hologram.getKeys(false)) {
                String text = hologram.isConfigurationSection(key) ? hologram.getString(key + ".text", "") : hologram.getString(key, "");
                lines.addFirst(text);
            }
        }
        if (!"false".equalsIgnoreCase(String.valueOf(npc.get("metadata.nameplate-visible", true)))) lines.add(name);
        return lines;
    }

    /** entitypose, sneak and sleeptrait; sitting (sittrait, the SITTING pose) has no counterpart here. */
    private static NpcPose citizensPose(ConfigurationSection traits) {
        if (traits.contains("sleeptrait.at")) return NpcPose.LYING;
        if (traits.getBoolean("sneak.sneaking", false)) return NpcPose.SNEAKING;
        return switch (traits.getString("entitypose.pose", "").toUpperCase(Locale.ROOT)) {
            case "SLEEPING" -> NpcPose.LYING;
            case "CROUCHING" -> NpcPose.SNEAKING;
            case "SWIMMING" -> NpcPose.CRAWLING;
            case "SPIN_ATTACK" -> NpcPose.SPINNING;
            default -> NpcPose.STANDING;
        };
    }

    /** The scale attribute, written {@code minecraft:scale}, {@code minecraft:generic,scale} or {@code GENERIC_SCALE}. */
    private static double citizensScale(ConfigurationSection attributes) {
        if (attributes == null) return 1;
        for (String key : attributes.getKeys(false)) {
            String plain = key.toLowerCase(Locale.ROOT).replace(',', '.').replaceFirst("^[a-z_]+:", "")
                    .replaceFirst("^generic[._]", "");
            if (plain.equals("scale")) return number(attributes.get(key), 1);
        }
        return 1;
    }

    /**
     * {@code commandtrait.commands.<n>}: {@code command}, {@code hand} (LEFT, RIGHT, BOTH...),
     * {@code player} (as the player rather than the console), {@code cooldown} in seconds and
     * {@code delay} in ticks.
     *
     * <p>A command that needs a permission, a price, items or has a use limit is left out, and so is
     * every command when the trait charges for all of them or runs only one of them per click
     * (RANDOM, SEQUENTIAL, CYCLE): run for everyone, every time, it would give away what was paid for.
     * {@code globalcooldown}, {@code op} (here a player command runs with the player's own
     * permissions) and the trait's error messages and temporary permissions have no counterpart.
     */
    private static StoredNpc citizensCommands(StoredNpc npc, ConfigurationSection trait) {
        ConfigurationSection commands = trait == null ? null : trait.getConfigurationSection("commands");
        if (commands == null) return npc;
        boolean charged = trait.getDouble("cost", -1) > 0 || trait.getInt("experienceCost", -1) > 0
                || !trait.getList("itemRequirements", List.of()).isEmpty()
                || !"LINEAR".equalsIgnoreCase(trait.getString("executionMode", "LINEAR"));
        record Delayed(int delay, String line) {
        }
        List<Delayed> kept = new ArrayList<>();
        double cooldown = 0;
        for (String key : charged ? List.<String>of() : commands.getKeys(false)) {
            ConfigurationSection command = commands.getConfigurationSection(key);
            if (command == null || command.getString("command", "").isBlank()) continue;
            if (command.isConfigurationSection("permissions") || command.isConfigurationSection("itemCost")
                    || command.getInt("n", 0) > 0 || command.getInt("gn", 0) > 0
                    || command.getDouble("cost", -1) > 0 || command.getInt("experienceCost", -1) > 0) continue;
            String filter = switch (command.getString("hand", "BOTH").toUpperCase(Locale.ROOT)) {
                case "LEFT" -> "left: ";
                case "RIGHT" -> "right: ";
                case "SHIFT_LEFT" -> "shift_left: ";
                case "SHIFT_RIGHT" -> "shift_right: ";
                default -> "";
            };
            kept.add(new Delayed(command.getInt("delay", 0),
                    filter + citizensCommand(command.getString("command"), command.getBoolean("player", false))));
            cooldown = Math.max(cooldown, number(command.get("cooldown"), 0));
        }
        // Each command waits its own delay from the click there; sorted, the waits add up to it here.
        kept.sort(Comparator.comparingInt(Delayed::delay));
        List<String> lines = new ArrayList<>();
        int waited = 0;
        for (Delayed command : kept) {
            if (command.delay() > waited) lines.add("[DELAY] " + (command.delay() - waited) + "t");
            waited = Math.max(waited, command.delay());
            lines.add(command.line());
        }
        StoredNpc.Interactions rules = npc.interactions();
        return npc.withInteractions(new StoredNpc.Interactions(lines, rules.permission(), Math.round(cooldown * 1000),
                rules.denyMessage(), rules.cooldownMessage(), rules.faceClicker()));
    }

    /** One command, with what Citizens itself reads into it: 'say' speaks, 'server x' connects. */
    static String citizensCommand(String written, boolean asPlayer) {
        String text = written.trim().replaceFirst("^/", "")
                .replace("<player_uuid>", "%player_uuid%")
                .replace("<player>", "%player_name%").replace("<p>", "%player_name%").replace("%player%", "%player_name%")
                .replace("<npc>", "%npc%").replace("<world>", "%player_world%");
        if (text.startsWith("say")) return "[SAY] " + text.substring(3).trim();
        String[] words = text.split("\\s+", 2);
        if (asPlayer && words.length == 2 && words[0].equalsIgnoreCase("server")) return "[SERVER] " + words[1];
        return (asPlayer ? "" : "console: ") + text;
    }

    // ------------------------------------------------------------------
    // FancyNpcs: npcs.yml, npcs.<uuid>.*
    // ------------------------------------------------------------------

    static Result fancy(File file) {
        Map<String, StoredNpc> found = new LinkedHashMap<>();
        int skipped = 0;
        if (!file.isFile()) return new Result(found, 0);
        ConfigurationSection all = YamlConfiguration.loadConfiguration(file).getConfigurationSection("npcs");
        if (all == null) return new Result(found, 0);
        File skins = new File(file.getParentFile(), ".data/skins");
        for (String id : all.getKeys(false)) {
            ConfigurationSection npc = all.getConfigurationSection(id);
            Place at = npc == null ? null : place(npc.getConfigurationSection("location"));
            if (at == null || !"PLAYER".equalsIgnoreCase(npc.getString("type", "PLAYER"))) {
                skipped++;
                continue;
            }
            String name = npc.getString("name", id);
            String display = npc.getString("displayName", "<empty>");
            StoredNpc imported = new StoredNpc().withLocation(at)
                    .withSkin(fancySkin(npc, skins))
                    .withLabelLines(display.isBlank() || display.equalsIgnoreCase("<empty>") ? List.of() : List.of(display))
                    .withPose(switch (npc.getString("attributes.pose", "standing").toLowerCase(Locale.ROOT)) {
                        case "crouching" -> NpcPose.SNEAKING;
                        case "sleeping" -> NpcPose.LYING;
                        case "swimming" -> NpcPose.CRAWLING;
                        // standing, and sitting, which has no counterpart here.
                        default -> NpcPose.STANDING;
                    })
                    .withScale(number(npc.get("scale"), 1));
            // Its other attributes (on_fire, invisible, silent, shaking, on_ground, use_item) and
            // showInTab, collidable and spawnEntity have no counterpart here.
            double distance = number(npc.get("visibility_distance"), -1);
            if (distance > 0) imported = imported.withViewDistance(distance);
            // MANUAL visibility is decided by another plugin through its API, so it is shown to all here.
            if ("PERMISSION_REQUIRED".equalsIgnoreCase(npc.getString("visibility", "ALL"))) {
                imported = imported.withPermission("fancynpcs.npc." + name + ".see");
            }
            if (npc.getBoolean("glowing", false)) {
                NamedTextColor colour = NamedTextColor.NAMES.value(npc.getString("glowingColor", "white").toLowerCase(Locale.ROOT));
                imported = imported.withGlow((colour == null ? NamedTextColor.WHITE : colour).asHexString());
            }
            double range = number(npc.get("turnToPlayerDistance"), -1);
            imported = imported.withLook(npc.getBoolean("turnToPlayer", false)
                    ? new StoredNpc.Look(NpcLook.EACH_VIEWER, range > 0 ? range : 8)
                    : new StoredNpc.Look(NpcLook.FIXED, 8));
            imported = equipment(imported, npc.getConfigurationSection("equipment"));
            imported = fancyActions(imported, npc, name);
            found.put(name, imported);
        }
        return new Result(found, skipped);
    }

    /**
     * {@code skin.mirrorSkin}, the old {@code skin.value}/{@code skin.signature}, then
     * {@code skin.identifier} (or the older {@code skin.uuid}): the texture FancyNpcs cached for it
     * in {@code .data/skins}, else the account it names (a UUID through the name the server knows
     * it by), and a placeholder is the viewer's own skin. A URL or file it has not cached cannot be
     * read without fetching it, and is left as the default skin.
     */
    private static StoredNpc.Skin fancySkin(ConfigurationSection npc, File cache) {
        if (npc.getBoolean("skin.mirrorSkin", npc.getBoolean("mirrorSkin", false))) return StoredNpc.Skin.mirror();
        String texture = npc.getString("skin.value", "");
        if (!texture.isBlank()) return StoredNpc.Skin.texture(texture, npc.getString("skin.signature", ""));
        String identifier = npc.getString("skin.identifier", npc.getString("skin.uuid", ""));
        if (identifier.isBlank()) return new StoredNpc.Skin();
        File cached = new File(cache, Base64.getEncoder().encodeToString(identifier.getBytes(StandardCharsets.UTF_8)) + ".json");
        try {
            JsonObject skin = JsonParser.parseString(Files.readString(cached.toPath())).getAsJsonObject().getAsJsonObject("skinData");
            if (skin != null && skin.has("textureValue")) {
                return StoredNpc.Skin.texture(skin.get("textureValue").getAsString(),
                        skin.has("textureSignature") ? skin.get("textureSignature").getAsString() : "");
            }
        } catch (Exception notCached) {
            // Not cached: read the identifier itself below.
        }
        // A placeholder such as %player_name% is the viewer's own skin, which is what mirror draws.
        if (identifier.startsWith("%") && identifier.endsWith("%")) return StoredNpc.Skin.mirror();
        String account = identifier;
        try {
            // A UUID FancyNpcs no longer caches: the name it last played under, when the server knows it.
            String known = Bukkit.getOfflinePlayer(UUID.fromString(identifier)).getName();
            if (known != null) account = known;
        } catch (IllegalArgumentException notUuid) {
            // A name, kept as it is.
        }
        StoredNpc.Skin skin = StoredNpc.Skin.parse(account);
        return skin == null ? new StoredNpc.Skin() : skin;
    }

    /**
     * {@code actions.<TRIGGER>.<order>} with {@code action} and {@code value}, plus the older
     * {@code message}/{@code playerCommand}/{@code serverCommand} keys and their list forms.
     *
     * <p>A {@code need_permission} first in its trigger becomes who may use it; anywhere else, and
     * {@code need_item} and {@code execute_random_action}, stop the trigger there: nothing here can
     * check them midway, and running the rest for everyone would give it away. CUSTOM is only run by
     * other plugins through its API, and an action type with no counterpart is left out.
     */
    private static StoredNpc fancyActions(StoredNpc npc, ConfigurationSection section, String name) {
        List<String> lines = new ArrayList<>();
        String permission = "";
        ConfigurationSection triggers = section.getConfigurationSection("actions");
        for (String trigger : triggers == null ? List.<String>of() : triggers.getKeys(false)) {
            String filter = switch (trigger.toUpperCase(Locale.ROOT)) {
                case "LEFT_CLICK" -> "left: ";
                case "RIGHT_CLICK" -> "right: ";
                case "ANY_CLICK" -> "";
                default -> null;
            };
            ConfigurationSection actions = triggers.getConfigurationSection(trigger);
            if (filter == null || actions == null) continue;
            List<String> orders = new ArrayList<>(actions.getKeys(false));
            orders.sort(Comparator.comparingDouble(order -> number(order, 0)));
            for (int index = 0; index < orders.size(); index++) {
                String action = actions.getString(orders.get(index) + ".action", "").toLowerCase(Locale.ROOT);
                String value = actions.getString(orders.get(index) + ".value", "");
                if (action.equals("need_permission")) {
                    if (index == 0 && !value.startsWith("!") && (permission.isEmpty() || permission.equals(value))) {
                        permission = value;
                        continue;
                    }
                    break;
                }
                if (action.equals("need_item") || action.equals("execute_random_action")) break;
                String line = fancyAction(action, value);
                if (line != null) lines.add(filter + line);
            }
        }
        section.getStringList("messages").forEach(message -> lines.add("[MESSAGE] " + message));
        section.getStringList("playerCommands").forEach(command -> lines.add(fancyAction("player_command", command)));
        section.getStringList("serverCommands").forEach(command -> lines.add(fancyAction("console_command", command)));
        if (!section.getString("message", "").isBlank()) lines.add("[MESSAGE] " + section.getString("message"));
        if (!section.getString("playerCommand", "").isBlank()) lines.add(fancyAction("player_command", section.getString("playerCommand")));
        if (!section.getString("serverCommand", "").isBlank()) lines.add(fancyAction("console_command", section.getString("serverCommand")));
        double cooldown = number(section.get("interactionCooldown"), 0);
        StoredNpc.Interactions rules = npc.interactions();
        return npc.withInteractions(new StoredNpc.Interactions(lines, permission, Math.round(cooldown * 1000),
                rules.denyMessage(), rules.cooldownMessage(), rules.faceClicker()));
    }

    static String fancyAction(String action, String value) {
        String text = value.replace("{player}", "%player_name%").trim();
        return switch (action.toLowerCase(Locale.ROOT)) {
            case "message" -> "[MESSAGE] " + text;
            // FancyNpcs reads 'server <name>' as a player command into a connect.
            case "player_command" -> text.toLowerCase(Locale.ROOT).startsWith("server ")
                    ? "[SERVER] " + text.substring(7).trim() : text.replaceFirst("^/", "");
            case "console_command" -> "console: " + text.replaceFirst("^/", "");
            case "send_to_server" -> "[SERVER] " + text;
            case "play_sound" -> "[SOUND] " + text;
            case "wait" -> "[DELAY] " + (text.isEmpty() ? "1" : text) + "s";
            // player_command_as_op was removed there and runs nothing; block_until_done only
            // stops a second click while the actions run.
            default -> null;
        };
    }

    // ------------------------------------------------------------------
    // Shared
    // ------------------------------------------------------------------

    /**
     * Worn items, under either plugin's slot names. An item written as a plain material is read
     * as one; a serialised item is read whole, which needs a running server, and is skipped
     * without one. Citizens' cosmetic slots and its serialised item meta (a head's texture
     * included) are left out: only the material comes across.
     */
    private static StoredNpc equipment(StoredNpc npc, ConfigurationSection section) {
        if (section == null) return npc;
        StoredNpc equipped = npc;
        for (String key : section.getKeys(false)) {
            EquipmentSlot slot = StoredNpc.slot(key);
            String icon = icon(section, key);
            if (slot != null && icon != null) equipped = equipped.equip(slot, icon);
        }
        return equipped;
    }

    private static String icon(ConfigurationSection section, String key) {
        Object value = section.get(key);
        if (value instanceof String material) return material(material);
        ConfigurationSection item = section.getConfigurationSection(key);
        if (item != null) {
            for (String field : List.of("type_key", "type", "id", "material")) {
                if (item.isString(field)) return material(item.getString(field));
            }
        }
        try {
            ItemStack stack = section.getItemStack(key);
            return stack == null || stack.getType().isAir() ? null : net.exylia.lib.item.Source.of(stack).raw();
        } catch (RuntimeException | LinkageError noServer) {
            return null;
        }
    }

    /** A place section; Citizens writes its numbers as strings, so both are read. */
    static Place place(ConfigurationSection section) {
        if (section == null || !section.contains("world")) return null;
        return new Place(section.getString("world"), number(section.get("x"), 0), number(section.get("y"), 0),
                number(section.get("z"), 0), number(section.get("yaw"), 0), number(section.get("pitch"), 0));
    }

    private static double number(Object written, double fallback) {
        if (written instanceof Number number) return number.doubleValue();
        try {
            return written == null ? fallback : Double.parseDouble(String.valueOf(written).trim());
        } catch (NumberFormatException unreadable) {
            return fallback;
        }
    }

    /** A name without its colour codes, for a key or an account. */
    static String plain(String name) {
        return name.replaceAll("(?i)[&§][0-9a-fk-or]", "").replaceAll("<[^>]+>", "").trim();
    }

    private static String material(String written) {
        String name = written.trim().split("[\\s{]", 2)[0];
        if (name.contains(":")) name = name.substring(name.indexOf(':') + 1);
        return name.toUpperCase(Locale.ROOT);
    }
}
