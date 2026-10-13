package net.exylia.exyliaDisplays.hologram;

import net.exylia.exyliaDisplays.ExyliaDisplays;
import net.exylia.exyliaDisplays.hologram.HologramsConfig;
import net.exylia.exyliaDisplays.hologram.HologramsMessages;
import net.exylia.exyliaDisplays.hologram.HologramViews;
import net.exylia.exyliaDisplays.common.store.Names;
import net.exylia.exyliaDisplays.common.store.Position;
import net.exylia.exyliaDisplays.hologram.StoredHologram;
import net.exylia.lib.hologram.HologramConfig;
import net.exylia.lib.hologram.HologramLine;
import net.exylia.lib.input.FormField;
import net.exylia.lib.input.FormKey;
import net.exylia.lib.input.FormValues;
import net.exylia.lib.text.Values;
import net.exylia.lib.util.editor.EditorForm;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Every window that changes a hologram.
 *
 * <p>Each one opens with the values it edits already in its boxes, so a change is a correction
 * rather than a retype, and everything about one subject is asked in one window instead of one
 * question per click. The answer goes through {@link net.exylia.exyliaDisplays.hologram.HologramStore},
 * the same door the commands and a hand edit of the file use, and the screen the player came from
 * comes back with the change on it.
 *
 * <p>A client too old for dialogs, or a Bedrock player, is asked the same fields through whatever
 * it can show; ExyliaLib picks.
 */
public final class HologramDialogs {

    private static final FormKey<String> NAME = FormKey.text("name");
    private static final FormKey<String> PRESET = FormKey.text("preset");
    private static final FormKey<String> TEXT = FormKey.text("text");
    private static final FormKey<BigDecimal> SIZE = FormKey.decimal("size");
    private static final FormKey<String> OPACITY = FormKey.text("opacity");
    private static final FormKey<String> SHADOW = FormKey.text("shadow");
    private static final FormKey<String> BACKGROUND = FormKey.text("background");
    private static final FormKey<String> GLOW = FormKey.text("glow");
    private static final FormKey<BigDecimal> SPIN = FormKey.decimal("spin");
    private static final FormKey<BigDecimal> BOB = FormKey.decimal("bob");
    private static final FormKey<BigDecimal> PULSE = FormKey.decimal("pulse");
    private static final FormKey<String> SHINE = FormKey.text("shine");
    private static final FormKey<BigDecimal> FLOW = FormKey.decimal("flow");
    private static final FormKey<BigDecimal> TYPEWRITER = FormKey.decimal("typewriter");
    private static final FormKey<BigDecimal> RANGE = FormKey.decimal("range");
    private static final FormKey<String> PERMISSION = FormKey.text("permission");
    private static final FormKey<String> CONDITION = FormKey.text("condition");
    private static final FormKey<String> BILLBOARD = FormKey.text("billboard");
    private static final FormKey<String> ALIGNMENT = FormKey.text("alignment");
    private static final FormKey<BigDecimal> SCALE_X = FormKey.decimal("scale_x");
    private static final FormKey<BigDecimal> SCALE_Y = FormKey.decimal("scale_y");
    private static final FormKey<BigDecimal> SCALE_Z = FormKey.decimal("scale_z");
    private static final FormKey<BigDecimal> SPACING = FormKey.decimal("spacing");
    private static final FormKey<Long> LINE_WIDTH = FormKey.integer("line_width");
    private static final FormKey<Long> TEXT_OPACITY = FormKey.integer("text_opacity");
    private static final FormKey<Boolean> TEXT_SHADOW = FormKey.flag("text_shadow");
    private static final FormKey<Boolean> SEE_THROUGH = FormKey.flag("see_through");
    private static final FormKey<Boolean> DEFAULT_BACKGROUND = FormKey.flag("default_background");
    private static final FormKey<Long> BRIGHTNESS = FormKey.integer("brightness");
    private static final FormKey<BigDecimal> VIEW_DISTANCE = FormKey.decimal("view_distance");
    private static final FormKey<Boolean> PER_PLAYER = FormKey.flag("per_player");
    private static final FormKey<Long> INTERVAL = FormKey.integer("interval");
    private static final FormKey<Boolean> AUTO_UPDATE = FormKey.flag("auto_update");
    private static final FormKey<String> COMMANDS = FormKey.text("commands");
    private static final FormKey<BigDecimal> WIDTH = FormKey.decimal("width");
    private static final FormKey<Long> COOLDOWN = FormKey.integer("cooldown");
    private static final FormKey<BigDecimal> HOVER = FormKey.decimal("hover");
    private static final FormKey<String> WORLD = FormKey.text("world");
    private static final FormKey<BigDecimal> X = FormKey.decimal("x");
    private static final FormKey<BigDecimal> Y = FormKey.decimal("y");
    private static final FormKey<BigDecimal> Z = FormKey.decimal("z");

    private HologramDialogs() {
        throw new AssertionError("No instances.");
    }

    private static ExyliaDisplays plugin() {
        return ExyliaDisplays.getInstance();
    }

    private static HologramsMessages.Dialogs words() {
        return HologramsMessages.get().dialogs();
    }

    private static EditorForm form(Player player, String title) {
        return EditorForm.of(plugin().getPlugin(), player, title);
    }

    // ------------------------------------------------------------------
    // Creating
    // ------------------------------------------------------------------

    /** Name, and what to start from, in one window; the new hologram stands where the player does. */
    public static void create(Player player, Runnable back) {
        HologramsConfig config = HologramsConfig.get();
        List<FormField.Option> presets = new ArrayList<>();
        config.presets().forEach((id, preset) -> presets.add(new FormField.Option(id, preset.name())));
        EditorForm form = form(player, words().createTitle())
                .field(NAME, FormField.text(NAME, words().nameLabel())
                        .defaultValue(HologramModule.get().store().freeName("hologram"))
                        .validate(name -> Names.valid(name.trim()),
                                plain(HologramsMessages.get().invalidName())))
                .hint(words().nameHint());
        if (!presets.isEmpty()) {
            form.choice(PRESET, words().presetLabel(), presets.get(0).key(), presets);
        }
        answer(player, form.ask(values -> values), back, values -> {
            String name = values.getOr(NAME, "").trim();
            String presetId = values.getOr(PRESET, "");
            HologramsConfig.Preset preset = config.presets().get(presetId);
            List<String> lines = preset == null ? List.of("{primary}&l" + name)
                    : preset.lines().stream().map(line -> line.replace("%hologram%", name)).toList();
            createHere(player, name, lines, back);
        });
    }

    /** Creates a hologram where the player stands and opens its editor, or says why it cannot. */
    public static boolean createHere(Player player, String name, List<String> lines, Runnable back) {
        Values values = Values.of().putText("name", name);
        if (!Names.valid(name)) {
            plugin().getMessages().send(player, HologramsMessages.get().invalidName(), values);
            back.run();
            return false;
        }
        if (HologramModule.get().store().exists(name)) {
            plugin().getMessages().send(player, HologramsMessages.get().alreadyExists(), values);
            back.run();
            return false;
        }
        HologramsConfig config = HologramsConfig.get();
        Position at = Position.of(player.getLocation()).plus(0, config.spawnHeight(), 0);
        HologramModule.get().store().create(name, StoredHologram.at(at, lines, config.defaultViewDistance()));
        plugin().getMessages().send(player, HologramsMessages.get().created(), values);
        HologramViews.openHub(player, name);
        return true;
    }

    // ------------------------------------------------------------------
    // Lines
    // ------------------------------------------------------------------

    /** The text of one line; its tags are kept as they are. */
    public static void lineText(Player player, String name, int index) {
        Optional<StoredHologram> hologram = HologramModule.get().store().get(name).filter(h -> h.hasLine(index));
        if (hologram.isEmpty()) {
            HologramViews.openLines(player, name);
            return;
        }
        HologramLine line = HologramLine.parse(hologram.get().lines().get(index));
        Runnable back = () -> HologramViews.openLines(player, name);
        EditorForm form = form(player, Values.of("line", index + 1).apply(words().lineTitle()))
                .text(TEXT, words().textLabel(), line.text(), 3)
                .hint(words().textHint());
        answer(player, form.ask(values -> values.getOr(TEXT, "")), back, text -> {
            String raw = line.toBuilder().text(text).build().raw();
            save(player, name, current -> current.hasLine(index) ? current.setLine(index, raw) : current, back);
        });
    }

    /** A new text line at the bottom. */
    public static void addLine(Player player, String name) {
        Runnable back = () -> HologramViews.openLines(player, name);
        EditorForm form = form(player, words().newLineTitle())
                .text(TEXT, words().textLabel(), "", 3)
                .hint(words().textHint());
        answer(player, form.ask(values -> values.getOr(TEXT, "")), back,
                text -> save(player, name, current -> current.addLine(text), back));
    }

    /** Every line in one box, one per row: the fastest way to write a whole hologram. */
    public static void allLines(Player player, String name) {
        Optional<StoredHologram> hologram = HologramModule.get().store().get(name);
        if (hologram.isEmpty()) return;
        Runnable back = () -> HologramViews.openLines(player, name);
        EditorForm form = form(player, words().allLinesTitle())
                .text(TEXT, words().allLinesLabel(), String.join("\n", hologram.get().lines()), 12)
                .hint(words().allLinesHint());
        answer(player, form.ask(values -> values.getOr(TEXT, "")), back, text -> {
            List<String> lines = new ArrayList<>(Arrays.asList(text.split("\\R", -1)));
            while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
                lines.remove(lines.size() - 1);
            }
            HologramModule.get().store().edit(name, current -> current.withLines(lines)).ifPresent(saved ->
                    plugin().getMessages().send(player, HologramsMessages.get().linesSaved(),
                            Values.of().putText("name", name).put("count", saved.lines().size())));
            back.run();
        });
    }

    /** A floating item or block added at the bottom, picked from ExyliaLib's icon picker. */
    public static void addModel(Player player, String name, boolean block) {
        Runnable back = () -> HologramViews.openLines(player, name);
        plugin().getInputs().answered(player,
                plugin().getInputs().icon(player, block ? words().blockTitle() : words().itemTitle()).open(),
                picked -> {
                    Material material = Material.matchMaterial(picked);
                    if (material == null || (block ? !material.isBlock() : !material.isItem())) {
                        plugin().getMessages().send(player, HologramsMessages.get().notABlock(),
                                Values.of().putText("material", picked));
                        back.run();
                        return;
                    }
                    HologramLine.Builder line = HologramLine.builder();
                    String raw = (block ? line.block(material.name()) : line.item(material.name())).build().raw();
                    save(player, name, current -> current.addLine(raw), back);
                },
                back);
    }

    /**
     * Everything about one line but its text: look, motion and who sees it, in one window.
     *
     * <p>Empty boxes and zeroes write no tag, so the line keeps following the hologram's own
     * settings for whatever the window left alone.
     */
    public static void lineStyle(Player player, String name, int index) {
        Optional<StoredHologram> hologram = HologramModule.get().store().get(name).filter(h -> h.hasLine(index));
        if (hologram.isEmpty()) {
            HologramViews.openLines(player, name);
            return;
        }
        HologramLine line = HologramLine.parse(hologram.get().lines().get(index));
        HologramsMessages.Dialogs words = words();
        Runnable back = () -> HologramViews.openLines(player, name);
        List<FormField.Option> shadows = List.of(
                new FormField.Option("inherit", words.inherit()),
                new FormField.Option("on", words.on()),
                new FormField.Option("off", words.off()));
        EditorForm form = form(player, Values.of("line", index + 1).apply(words.styleTitle()))
                .decimal(SIZE, words.sizeLabel(), decimal(line.size(), 1))
                .field(OPACITY, FormField.text(OPACITY, words.opacityLabel())
                        .defaultValue(line.opacity() == null ? "" : String.valueOf(line.opacity()))
                        .optional()
                        .validate(value -> value.isBlank() || isInt(value, 0, 255), "0 - 255"))
                .hint(words.opacityHint())
                .choice(SHADOW, words.shadowLabel(),
                        line.shadow() == null ? "inherit" : line.shadow() ? "on" : "off", shadows)
                .text(BACKGROUND, words.backgroundLabel(), line.background())
                .hint(words.backgroundHint());
        if (line.isModel()) {
            form.text(GLOW, words.glowLabel(), line.glow() == null ? "" : line.glow().isEmpty() ? "team" : line.glow())
                    .hint(words.glowHint());
        }
        form.decimal(SPIN, words.spinLabel(), decimal(line.spin(), 0))
                .hint(words.motionHint())
                .decimal(BOB, words.bobLabel(), decimal(line.bob(), 0))
                .decimal(PULSE, words.pulseLabel(), decimal(line.pulse(), 0));
        if (!line.isModel()) {
            form.text(SHINE, words.shineLabel(), line.shine() == null ? "" : line.shine().isEmpty() ? "on" : line.shine())
                    .hint(words.shineHint())
                    .decimal(FLOW, words.flowLabel(), decimal(line.flow(), 0))
                    .decimal(TYPEWRITER, words.typewriterLabel(), decimal(line.typewriter(), 0));
        }
        form.decimal(RANGE, words.rangeLabel(), decimal(line.range(), 0))
                .text(PERMISSION, words.linePermissionLabel(), line.permission())
                .hint(words.linePermissionHint())
                .text(CONDITION, words.conditionLabel(), line.condition(), 2)
                .hint(words.conditionHint());
        answer(player, form.ask(values -> styled(line, values)), back,
                styled -> save(player, name, current -> current.hasLine(index)
                        ? current.setLine(index, styled.raw()) : current, back));
    }

    /** The line with what the style window answered. */
    static HologramLine styled(HologramLine line, FormValues values) {
        HologramLine.Builder edited = line.toBuilder()
                .size(unlessDefault(values.getOr(SIZE, BigDecimal.ONE), 1))
                .opacity(values.getOr(OPACITY, "").isBlank() ? null : Integer.parseInt(values.getOr(OPACITY, "").trim()))
                .shadow(switch (values.getOr(SHADOW, "inherit")) {
                    case "on" -> Boolean.TRUE;
                    case "off" -> Boolean.FALSE;
                    default -> null;
                })
                .background(values.getOr(BACKGROUND, ""))
                .spin(unlessDefault(values.getOr(SPIN, BigDecimal.ZERO), 0))
                .bob(unlessDefault(values.getOr(BOB, BigDecimal.ZERO), 0))
                .pulse(unlessDefault(values.getOr(PULSE, BigDecimal.ZERO), 0))
                .range(unlessDefault(values.getOr(RANGE, BigDecimal.ZERO), 0))
                .permission(values.getOr(PERMISSION, ""))
                .condition(values.getOr(CONDITION, ""));
        if (values.has(GLOW)) {
            String glow = values.getOr(GLOW, "").trim();
            edited.glow(glow.isEmpty() ? null : glow.equalsIgnoreCase("team") ? "" : glow);
        }
        if (values.has(SHINE)) {
            String shine = values.getOr(SHINE, "").trim();
            edited.shine(shine.isEmpty() ? null : shine.equalsIgnoreCase("on") ? "" : shine);
        }
        if (values.has(FLOW)) edited.flow(unlessDefault(values.getOr(FLOW, BigDecimal.ZERO), 0));
        if (values.has(TYPEWRITER)) edited.typewriter(unlessDefault(values.getOr(TYPEWRITER, BigDecimal.ZERO), 0));
        return edited.build();
    }

    // ------------------------------------------------------------------
    // The hologram
    // ------------------------------------------------------------------

    /** How the whole hologram is drawn. */
    public static void appearance(Player player, String name) {
        StoredHologram hologram = HologramModule.get().store().get(name).orElse(null);
        if (hologram == null) return;
        HologramConfig.Properties look = hologram.appearance();
        HologramsMessages.Dialogs words = words();
        Runnable back = () -> HologramViews.openHub(player, name);
        EditorForm form = form(player, words.appearanceTitle())
                .choice(BILLBOARD, words.billboardLabel(), look.billboard().toUpperCase(), options("CENTER", "VERTICAL", "HORIZONTAL", "FIXED"))
                .choice(ALIGNMENT, words.alignmentLabel(), look.alignment().toUpperCase(), options("CENTER", "LEFT", "RIGHT"))
                .decimal(SCALE_X, words.scaleXLabel(), BigDecimal.valueOf(look.scaleX()))
                .decimal(SCALE_Y, words.scaleYLabel(), BigDecimal.valueOf(look.scaleY()))
                .decimal(SCALE_Z, words.scaleZLabel(), BigDecimal.valueOf(look.scaleZ()))
                .decimal(SPACING, words.spacingLabel(), BigDecimal.valueOf(look.lineSpacing()))
                .integer(LINE_WIDTH, words.lineWidthLabel(), look.lineWidth())
                .integer(TEXT_OPACITY, words.textOpacityLabel(), look.textOpacity())
                .flag(TEXT_SHADOW, words.shadowLabel(), look.shadow())
                .flag(SEE_THROUGH, words.seeThroughLabel(), look.seeThrough())
                .flag(DEFAULT_BACKGROUND, words.defaultBackgroundLabel(), look.defaultBackground())
                .text(BACKGROUND, words.backgroundLabel(), look.backgroundColor())
                .hint(words.backgroundHint())
                .integer(BRIGHTNESS, words.brightnessLabel(), look.brightness())
                .hint(words.brightnessHint());
        answer(player, form.ask(values -> new HologramConfig.Properties(
                values.getOr(BILLBOARD, look.billboard()),
                values.getOr(ALIGNMENT, look.alignment()),
                positive(values.getDecimal(SCALE_X), look.scaleX()),
                positive(values.getDecimal(SCALE_Y), look.scaleY()),
                positive(values.getDecimal(SCALE_Z), look.scaleZ()),
                values.getBoolean(TEXT_SHADOW),
                values.getBoolean(SEE_THROUGH),
                (int) Math.max(1, values.getLong(LINE_WIDTH)),
                Math.max(0, values.getDecimal(SPACING).doubleValue()),
                (int) Math.clamp(values.getLong(TEXT_OPACITY), 0, 255),
                values.getBoolean(DEFAULT_BACKGROUND),
                values.getOr(BACKGROUND, "").isBlank() ? "#00000000" : values.getOr(BACKGROUND, "").trim(),
                (int) Math.clamp(values.getLong(BRIGHTNESS), -1, 15),
                look.glowing())), back,
                edited -> save(player, name, current -> current.withAppearance(edited), back));
    }

    /** Who sees it, from how far, and how often it refreshes. */
    public static void visibility(Player player, String name) {
        StoredHologram hologram = HologramModule.get().store().get(name).orElse(null);
        if (hologram == null) return;
        HologramsMessages.Dialogs words = words();
        Runnable back = () -> HologramViews.openHub(player, name);
        EditorForm form = form(player, words.visibilityTitle())
                .decimal(VIEW_DISTANCE, words.viewDistanceLabel(), BigDecimal.valueOf(hologram.viewDistance()))
                .text(PERMISSION, words.permissionLabel(), hologram.permission())
                .hint(words.permissionHint())
                .flag(PER_PLAYER, words.perPlayerLabel(), hologram.perPlayer())
                .integer(INTERVAL, words.intervalLabel(), hologram.refresh().updateInterval())
                .flag(AUTO_UPDATE, words.autoUpdateLabel(), hologram.refresh().autoUpdate());
        answer(player, form.ask(values -> values), back, values -> save(player, name, current -> current
                .withViewDistance(positive(values.getDecimal(VIEW_DISTANCE), current.viewDistance()))
                .withPermission(values.getOr(PERMISSION, "").trim())
                .withPerPlayer(values.getBoolean(PER_PLAYER))
                .withRefresh(new HologramConfig.Refresh(Math.max(1, values.getLong(INTERVAL)),
                        values.getBoolean(AUTO_UPDATE))), back));
    }

    /** What clicking it does. */
    public static void click(Player player, String name) {
        StoredHologram hologram = HologramModule.get().store().get(name).orElse(null);
        if (hologram == null) return;
        HologramConfig.Click click = hologram.click();
        HologramsMessages.Dialogs words = words();
        Runnable back = () -> HologramViews.openHub(player, name);
        EditorForm form = form(player, words.clickTitle())
                .text(COMMANDS, words.commandsLabel(), String.join("\n", click.commands()), 6)
                .hint(words.commandsHint())
                .decimal(WIDTH, words.widthLabel(), BigDecimal.valueOf(click.width()))
                .integer(COOLDOWN, words.cooldownLabel(), click.cooldown())
                .decimal(HOVER, words.hoverLabel(), BigDecimal.valueOf(click.hoverScale()))
                .hint(words.hoverHint());
        answer(player, form.ask(values -> new HologramConfig.Click(
                Arrays.stream(values.getOr(COMMANDS, "").split("\\R")).map(String::trim)
                        .filter(command -> !command.isEmpty()).toList(),
                positive(values.getDecimal(WIDTH), click.width()),
                Math.max(0, values.getLong(COOLDOWN)),
                positive(values.getDecimal(HOVER), 1))), back,
                edited -> save(player, name, current -> current.withClick(edited), back));
    }

    /** The exact position, for a nudge a step at a time. */
    public static void location(Player player, String name) {
        StoredHologram hologram = HologramModule.get().store().get(name).orElse(null);
        if (hologram == null) return;
        Position at = hologram.location();
        HologramsMessages.Dialogs words = words();
        Runnable back = () -> HologramViews.openHub(player, name);
        List<FormField.Option> worlds = new ArrayList<>();
        worlds.add(new FormField.Option(at.world(), at.world()));
        for (World world : Bukkit.getWorlds()) {
            if (!world.getName().equals(at.world())) {
                worlds.add(new FormField.Option(world.getName(), world.getName()));
            }
        }
        EditorForm form = form(player, words.locationTitle())
                .choice(WORLD, words.worldLabel(), at.world(), worlds)
                .decimal(X, "X", BigDecimal.valueOf(at.x()))
                .decimal(Y, "Y", BigDecimal.valueOf(at.y()))
                .decimal(Z, "Z", BigDecimal.valueOf(at.z()));
        answer(player, form.ask(values -> new Position(values.getOr(WORLD, at.world()),
                        values.getDecimal(X).doubleValue(), values.getDecimal(Y).doubleValue(),
                        values.getDecimal(Z).doubleValue())), back,
                position -> save(player, name, current -> current.withLocation(position), back));
    }

    /** A new name, keeping everything else. */
    public static void rename(Player player, String name) {
        Runnable back = () -> HologramViews.openHub(player, name);
        plugin().getInputs().answered(player,
                plugin().getInputs().text(player, words().renameTitle()).defaultValue(name).maxLength(32).open(),
                typed -> {
                    String renamed = typed.trim();
                    if (rename(player, name, renamed)) {
                        HologramViews.openHub(player, renamed);
                    } else {
                        back.run();
                    }
                },
                back);
    }

    /** Renames, telling the sender how it went. */
    public static boolean rename(org.bukkit.command.CommandSender sender, String name, String renamed) {
        Values values = Values.of().putText("name", name).putText("new", renamed);
        HologramsMessages messages = HologramsMessages.get();
        if (!HologramModule.get().store().exists(name)) {
            plugin().getMessages().send(sender, messages.notFound(), values);
            return false;
        }
        if (!Names.valid(renamed)) {
            plugin().getMessages().send(sender, messages.invalidName(), values);
            return false;
        }
        if (!HologramModule.get().store().rename(name, renamed)) {
            plugin().getMessages().send(sender, messages.alreadyExists(), Values.of().putText("name", renamed));
            return false;
        }
        plugin().getMessages().send(sender, messages.renamed(), values);
        return true;
    }

    /** A copy under a new name, standing in the same place. */
    public static void cloneHologram(Player player, String name) {
        Runnable back = () -> HologramViews.openHub(player, name);
        plugin().getInputs().answered(player,
                plugin().getInputs().text(player, words().cloneTitle())
                        .defaultValue(HologramModule.get().store().freeName(name)).maxLength(32).open(),
                typed -> {
                    String copy = typed.trim();
                    if (copy(player, name, copy)) {
                        HologramViews.openHub(player, copy);
                    } else {
                        back.run();
                    }
                },
                back);
    }

    /** Copies, telling the sender how it went. */
    public static boolean copy(org.bukkit.command.CommandSender sender, String name, String copy) {
        Values values = Values.of().putText("name", name).putText("new", copy);
        HologramsMessages messages = HologramsMessages.get();
        Optional<StoredHologram> original = HologramModule.get().store().get(name);
        if (original.isEmpty()) {
            plugin().getMessages().send(sender, messages.notFound(), values);
            return false;
        }
        if (!Names.valid(copy)) {
            plugin().getMessages().send(sender, messages.invalidName(), values);
            return false;
        }
        if (!HologramModule.get().store().create(copy, original.get())) {
            plugin().getMessages().send(sender, messages.alreadyExists(), Values.of().putText("name", copy));
            return false;
        }
        plugin().getMessages().send(sender, messages.cloned(), values);
        return true;
    }

    /** Asks before deleting, then shows the list. */
    public static void delete(Player player, String name, Runnable back) {
        plugin().getInputs().answered(player,
                plugin().getInputs().confirm(player,
                        Values.of().putText("name", name).apply(words().confirmDelete())).dangerous().open(),
                confirmed -> {
                    if (confirmed && HologramModule.get().store().delete(name)) {
                        plugin().getMessages().send(player, HologramsMessages.get().deleted(),
                                Values.of().putText("name", name));
                        HologramViews.openList(player);
                        return;
                    }
                    back.run();
                },
                back);
    }

    /** Asks before removing a line. */
    public static void deleteLine(Player player, String name, int index) {
        Runnable back = () -> HologramViews.openLines(player, name);
        plugin().getInputs().answered(player,
                plugin().getInputs().confirm(player,
                        Values.of("line", index + 1).apply(words().confirmDeleteLine())).dangerous().open(),
                confirmed -> {
                    if (confirmed) {
                        HologramModule.get().store().edit(name, current -> current.hasLine(index) ? current.removeLine(index) : current);
                        plugin().getMessages().send(player, HologramsMessages.get().lineRemoved(),
                                Values.of().putText("name", name).put("line", index + 1));
                    }
                    back.run();
                },
                back);
    }

    // ------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------

    /** Saves a change and brings the screen back with it. */
    private static void save(Player player, String name, UnaryOperator<StoredHologram> change, Runnable back) {
        HologramModule.get().store().edit(name, change).ifPresentOrElse(
                saved -> plugin().getMessages().send(player, HologramsMessages.get().saved(), Values.of().putText("name", name)),
                () -> plugin().getMessages().send(player, HologramsMessages.get().notFound(), Values.of().putText("name", name)));
        back.run();
    }

    /**
     * Waits for a window's answer on the player's own thread: the answer, or the screen they came
     * from when they closed it.
     */
    private static <T> void answer(Player player, CompletionStage<Optional<T>> asked, Runnable back,
                                   Consumer<T> then) {
        player.closeInventory();
        asked.whenComplete((answer, failure) -> plugin().getTasks().runAtEntity(player, () -> {
            if (failure != null) {
                plugin().getDebug().error("A hologram window failed", failure);
                back.run();
            } else if (answer.isPresent()) {
                then.accept(answer.get());
            } else {
                back.run();
            }
        }));
    }

    private static List<FormField.Option> options(String... keys) {
        return Arrays.stream(keys).map(key -> new FormField.Option(key, key)).toList();
    }

    private static BigDecimal decimal(Double value, double fallback) {
        return BigDecimal.valueOf(value == null ? fallback : value).stripTrailingZeros();
    }

    /** {@code null} when the value is the one that writes no tag. */
    private static Double unlessDefault(BigDecimal value, double none) {
        double number = value.doubleValue();
        return number == none ? null : number;
    }

    private static double positive(BigDecimal value, double fallback) {
        double number = value.doubleValue();
        return number > 0 ? number : fallback;
    }

    private static boolean isInt(String value, int min, int max) {
        try {
            int number = Integer.parseInt(value.trim());
            return number >= min && number <= max;
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }

    /** A message as plain text, for a validation error under a box. */
    private static String plain(String message) {
        return plugin().getMessages().plain(message.replace("%prefix%", ""), Values.of());
    }
}
