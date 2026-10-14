package net.exylia.exyliaDisplays.hologram;

import net.exylia.exyliaDisplays.ExyliaDisplays;
import net.exylia.exyliaDisplays.hologram.HologramsMessages.Dialogs;
import net.exylia.lib.hologram.HologramLine;
import net.exylia.lib.input.FormField;
import net.exylia.lib.input.FormKey;
import net.exylia.lib.input.FormValues;
import net.exylia.lib.text.Values;
import net.exylia.lib.util.editor.EditorForm;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The style of one line, split by theme so every window fits on screen without scrolling: a small
 * chooser offering a preset or one of the {@link Screen} windows that has something for the line.
 *
 * <p>Every setting is one row of {@link #FIELDS}: the window it sits in, how it is asked, and how
 * the answer goes back through {@link HologramLine.Builder}, which stays the only thing that reads
 * and writes tags. Adding a tag to a window is one more row there, plus its label in
 * {@link Dialogs}; the windows, their order and the saving follow from the table.
 */
final class LineStyle {

    /** The windows, in the order the chooser offers them. A preset is no window but a pick. */
    enum Screen {
        PRESET(Material.NAME_TAG, Dialogs::presetTitle, Dialogs::presetButton, Dialogs::presetButtonHint),
        LOOK(Material.GLOW_INK_SAC, Dialogs::lookTitle, Dialogs::lookButton, Dialogs::lookButtonHint),
        LAYOUT(Material.ITEM_FRAME, Dialogs::layoutTitle, Dialogs::layoutButton, Dialogs::layoutButtonHint),
        MOTION(Material.FEATHER, Dialogs::motionTitle, Dialogs::motionButton, Dialogs::motionButtonHint),
        COLOURS(Material.PRISMARINE_CRYSTALS, Dialogs::colourTitle, Dialogs::colourButton, Dialogs::colourButtonHint),
        TEXT(Material.OAK_SIGN, Dialogs::textEffectsTitle, Dialogs::textEffectsButton, Dialogs::textEffectsButtonHint),
        FRAMES(Material.CLOCK, Dialogs::framesTitle, Dialogs::framesButton, Dialogs::framesButtonHint),
        REACH(Material.ENDER_EYE, Dialogs::reachTitle, Dialogs::reachButton, Dialogs::reachButtonHint);

        final Material icon;
        final Function<Dialogs, String> title;
        final Function<Dialogs, String> button;
        final Function<Dialogs, String> hint;

        Screen(Material icon, Function<Dialogs, String> title, Function<Dialogs, String> button,
               Function<Dialogs, String> hint) {
            this.icon = icon;
            this.title = title;
            this.button = button;
            this.hint = hint;
        }
    }

    /**
     * One setting of a line.
     *
     * @param screen the window it sits in
     * @param shown  which lines have it: a glow only means something on an item or a block
     * @param ask    adds its box to the window, filled with what the line has
     * @param write  puts the answer back on the line
     */
    record Field(Screen screen, Predicate<HologramLine> shown, BiConsumer<EditorForm, HologramLine> ask,
                 BiConsumer<HologramLine.Builder, FormValues> write) {

        /** The same field, only on the lines that pass. */
        Field only(Predicate<HologramLine> lines) {
            return new Field(screen, shown.and(lines), ask, write);
        }
    }

    private static final String INHERIT = "inherit";
    private static final String ON = "on";
    private static final String OFF = "off";

    /** The palette colours a shine offers, by name, as the text parser reads them. */
    private static final List<String> SHINE_COLOURS = List.of("highlight", "primary", "secondary",
            "letters", "accent", "info", "success", "warning", "error");

    private static final Predicate<HologramLine> TEXT_LINE = line -> !line.isModel();

    /**
     * Every setting of every window, in the order they are asked. One row per tag: the helpers
     * below cover a number, a text and a pick from a few options. An option of a tag (a period, a
     * speed) only shows once its tag is on, which keeps every window short enough to need no scroll.
     */
    static final List<Field> FIELDS = List.of(
            // Look
            number(Screen.LOOK, "size", Dialogs::sizeLabel, null, HologramLine::size, HologramLine.Builder::size, 1),
            choice(Screen.LOOK, "opacity", Dialogs::opacityLabel, Dialogs::opacityHint, LineStyle::opacities,
                    line -> line.opacity() == null ? INHERIT : String.valueOf(line.opacity()),
                    (line, key) -> line.opacity(INHERIT.equals(key) ? null : Integer.valueOf(key))),
            choice(Screen.LOOK, "shadow", Dialogs::shadowLabel, null, LineStyle::switches,
                    line -> toggle(line.shadow()), (line, key) -> line.shadow(toggle(key))),
            text(Screen.LOOK, "background", Dialogs::backgroundLabel, Dialogs::backgroundHint,
                    HologramLine::background, HologramLine.Builder::background),
            text(Screen.LOOK, "glow", Dialogs::glowLabel, Dialogs::glowHint,
                    line -> line.glow() == null ? "" : line.glow().isEmpty() ? "team" : line.glow(),
                    (line, glow) -> line.glow(glow.isEmpty() ? null : glow.equalsIgnoreCase("team") ? "" : glow))
                    .only(HologramLine::isModel),
            text(Screen.LOOK, "head", Dialogs::headLabel, Dialogs::headHint, HologramLine::head,
                    (line, head) -> { if (!head.isEmpty()) line.head(head); })
                    .only(line -> line.head() != null),
            number(Screen.LOOK, "gap", Dialogs::gapLabel, Dialogs::gapHint, HologramLine::gap, HologramLine.Builder::gap, 0),
            // Layout
            choice(Screen.LAYOUT, "align", Dialogs::alignLabel, null,
                    (words, line) -> named(words, line.align(), "left", "center", "right"),
                    line -> line.align() == null ? INHERIT : line.align(),
                    (line, key) -> line.align(INHERIT.equals(key) ? null : key))
                    .only(TEXT_LINE),
            number(Screen.LAYOUT, "width", Dialogs::lineWidthTagLabel, null,
                    line -> line.width() == null ? null : line.width().doubleValue(),
                    (line, width) -> line.width(width == null ? null : (int) Math.max(1, Math.round(width))), 0)
                    .only(TEXT_LINE),
            choice(Screen.LAYOUT, "billboard", Dialogs::lineBillboardLabel, null,
                    (words, line) -> named(words, line.billboard(), "center", "vertical", "horizontal", "fixed"),
                    line -> line.billboard() == null ? INHERIT : line.billboard(),
                    (line, key) -> line.billboard(INHERIT.equals(key) ? null : key)),
            choice(Screen.LAYOUT, "seethrough", Dialogs::lineSeeThroughLabel, null, LineStyle::switches,
                    line -> toggle(line.seeThrough()), (line, key) -> line.seeThrough(toggle(key))),
            choice(Screen.LAYOUT, "brightness", Dialogs::lineBrightnessLabel, Dialogs::brightnessHint, LineStyle::lights,
                    line -> line.brightness() == null ? INHERIT : String.valueOf(line.brightness()),
                    (line, key) -> line.brightness(INHERIT.equals(key) ? null : Integer.valueOf(key))),
            text(Screen.LAYOUT, "autoscale", Dialogs::autoscaleLabel, Dialogs::autoscaleHint,
                    line -> line.autoscale() == null ? "" : line.autoscale().isEmpty() ? ON : line.autoscale(),
                    LineStyle::autoscale),
            // Movement
            number(Screen.MOTION, "spin", Dialogs::spinLabel, Dialogs::motionHint, HologramLine::spin, HologramLine.Builder::spin, 0),
            number(Screen.MOTION, "sway", Dialogs::swayLabel, null, HologramLine::sway, HologramLine.Builder::sway, 0),
            number(Screen.MOTION, "sway_period", Dialogs::swayPeriodLabel, null, HologramLine::swayPeriod,
                    HologramLine.Builder::swayPeriod, 0)
                    .only(line -> line.sway() != null),
            number(Screen.MOTION, "bob", Dialogs::bobLabel, null, HologramLine::bob, HologramLine.Builder::bob, 0),
            number(Screen.MOTION, "bob_period", Dialogs::bobPeriodLabel, null, HologramLine::bobPeriod,
                    HologramLine.Builder::bobPeriod, 0)
                    .only(line -> line.bob() != null),
            number(Screen.MOTION, "pulse", Dialogs::pulseLabel, null, HologramLine::pulse, HologramLine.Builder::pulse, 0),
            number(Screen.MOTION, "pulse_period", Dialogs::pulsePeriodLabel, null, HologramLine::pulsePeriod,
                    HologramLine.Builder::pulsePeriod, 0)
                    .only(line -> line.pulse() != null),
            // Colour effects
            choice(Screen.COLOURS, "shine", Dialogs::shineLabel, Dialogs::shineHint, LineStyle::shines,
                    line -> line.shine() == null ? OFF : line.shine().isEmpty() ? ON : line.shine(),
                    (line, key) -> line.shine(OFF.equals(key) ? null : ON.equals(key) ? "" : key))
                    .only(TEXT_LINE),
            number(Screen.COLOURS, "shine_speed", Dialogs::shineSpeedLabel, null, HologramLine::shineSpeed,
                    HologramLine.Builder::shineSpeed, 0)
                    .only(TEXT_LINE.and(line -> line.shine() != null)),
            number(Screen.COLOURS, "shine_pause", Dialogs::shinePauseLabel, null, HologramLine::shinePause,
                    HologramLine.Builder::shinePause, 0)
                    .only(TEXT_LINE.and(line -> line.shine() != null)),
            number(Screen.COLOURS, "shine_width", Dialogs::shineWidthLabel, null, HologramLine::shineWidth,
                    HologramLine.Builder::shineWidth, 0)
                    .only(TEXT_LINE.and(line -> line.shine() != null)),
            number(Screen.COLOURS, "flow", Dialogs::flowLabel, Dialogs::effectsHint, HologramLine::flow, HologramLine.Builder::flow, 0)
                    .only(TEXT_LINE),
            number(Screen.COLOURS, "rainbow", Dialogs::rainbowLabel, null, HologramLine::rainbow, HologramLine.Builder::rainbow, 0)
                    .only(TEXT_LINE),
            // Text effects
            number(Screen.TEXT, "typewriter", Dialogs::typewriterLabel, Dialogs::effectsHint, HologramLine::typewriter,
                    HologramLine.Builder::typewriter, 0)
                    .only(TEXT_LINE),
            number(Screen.TEXT, "scramble", Dialogs::scrambleLabel, null, HologramLine::scramble,
                    HologramLine.Builder::scramble, 0)
                    .only(TEXT_LINE),
            number(Screen.TEXT, "marquee", Dialogs::marqueeLabel, null,
                    line -> line.marquee() == null ? null : line.marquee().doubleValue(),
                    (line, shown) -> line.marquee(shown == null ? null : (int) Math.max(1, Math.round(shown))), 0)
                    .only(TEXT_LINE),
            number(Screen.TEXT, "marquee_speed", Dialogs::marqueeSpeedLabel, null, HologramLine::marqueeSpeed,
                    HologramLine.Builder::marqueeSpeed, 0)
                    .only(TEXT_LINE.and(line -> line.marquee() != null)),
            number(Screen.TEXT, "fade", Dialogs::fadeLabel, null, HologramLine::fade, HologramLine.Builder::fade, 0)
                    .only(TEXT_LINE),
            number(Screen.TEXT, "blink", Dialogs::blinkLabel, null, HologramLine::blink, HologramLine.Builder::blink, 0)
                    .only(TEXT_LINE),
            // Frames
            number(Screen.FRAMES, "frames", Dialogs::framesLabel, null, HologramLine::frames, HologramLine.Builder::frames, 0)
                    .only(TEXT_LINE),
            frames().only(TEXT_LINE),
            // Who sees it, and what clicking it runs
            number(Screen.REACH, "range", Dialogs::rangeLabel, null, HologramLine::range, HologramLine.Builder::range, 0),
            text(Screen.REACH, "permission", Dialogs::linePermissionLabel, Dialogs::linePermissionHint,
                    HologramLine::permission, HologramLine.Builder::permission),
            text(Screen.REACH, "condition", Dialogs::conditionLabel, Dialogs::conditionHint,
                    HologramLine::condition, HologramLine.Builder::condition),
            text(Screen.REACH, "button", Dialogs::buttonLabel, Dialogs::buttonHint,
                    HologramLine::button, (line, button) -> line.button(button.isEmpty() ? null : button)));

    private LineStyle() {
        throw new AssertionError("No instances.");
    }

    /** The chooser: one button per window, each saying what it holds. */
    static void open(Player player, String name, int page, int index) {
        HologramLine line = line(name, page, index).orElse(null);
        Runnable back = () -> HologramViews.openLines(player, name, page);
        if (line == null) {
            back.run();
            return;
        }
        Dialogs words = HologramsMessages.get().dialogs();
        ExyliaDisplays plugin = ExyliaDisplays.getInstance();
        player.closeInventory();
        plugin.getInputs().answered(player,
                plugin.getInputs().choice(player, Values.of("line", index + 1).apply(words.styleTitle()),
                                Arrays.stream(Screen.values()).filter(screen -> screen == Screen.PRESET
                                        ? !HologramsConfig.get().linePresets().isEmpty()
                                        : !fields(screen, line).isEmpty()).toList())
                        .label(screen -> screen.button.apply(words))
                        .description(screen -> screen.hint.apply(words))
                        .icon(screen -> screen.icon)
                        .key(screen -> screen.name().toLowerCase())
                        .open(),
                screen -> open(player, name, page, index, screen),
                back);
    }

    /** One window; saving or closing it comes back to the chooser, for the next one. */
    static void open(Player player, String name, int page, int index, Screen screen) {
        HologramLine line = line(name, page, index).orElse(null);
        if (line == null) {
            HologramViews.openLines(player, name, page);
            return;
        }
        Runnable back = () -> open(player, name, page, index);
        if (screen == Screen.PRESET) {
            preset(player, name, page, index, back);
            return;
        }
        EditorForm form = HologramDialogs.form(player,
                Values.of("line", index + 1).apply(screen.title.apply(HologramsMessages.get().dialogs())));
        fields(screen, line).forEach(field -> field.ask().accept(form, line));
        HologramDialogs.answer(player, form.ask(values -> apply(line, screen, values)), back,
                styled -> HologramDialogs.save(player, name, current -> current.onPage(page, view -> view.hasLine(index)
                        ? view.setLine(index, styled.raw()) : view), back));
    }

    /** Picks a preset of {@code line-presets} and puts its tags on the line. */
    private static void preset(Player player, String name, int page, int index, Runnable back) {
        Map<String, String> presets = HologramsConfig.get().linePresets();
        Dialogs words = HologramsMessages.get().dialogs();
        ExyliaDisplays plugin = ExyliaDisplays.getInstance();
        plugin.getInputs().answered(player,
                plugin.getInputs().choice(player, Values.of("line", index + 1).apply(words.presetTitle()),
                                List.copyOf(presets.keySet()))
                        .label(preset -> "{highlight}" + preset)
                        .description(preset -> Values.inert(presets.get(preset)))
                        .icon(preset -> Material.NAME_TAG)
                        .open(),
                preset -> HologramDialogs.save(player, name, current -> current.onPage(page, view -> view.hasLine(index)
                        ? view.setLine(index, withPreset(view.lines().get(index), presets.get(preset))) : view), back),
                back);
    }

    /**
     * A line with a preset's tags on it. The preset's tags replace the line's own of the same kind
     * (a preset's {@code <shine>} drops the line's shine speed too); the line's text and its other
     * tags stay. Text after the preset's tags is ignored, and a broken tag ends the preset there.
     *
     * @param raw    the line as written
     * @param preset the preset's tags, such as {@code <size:1.6><shine>}
     * @return the line as written, with the preset
     */
    static String withPreset(String raw, String preset) {
        HologramLine line = HologramLine.parse(raw);
        String own = line.toBuilder().text("").build().raw();
        String tags = HologramLine.parse(preset).toBuilder().text("").build().raw();
        // Read left to right, a later tag wins: the preset's come after the line's own.
        return HologramLine.parse(own + tags + line.text()).raw();
    }

    /** The line with what one window answered; the tags of the other windows are kept. */
    static HologramLine apply(HologramLine line, Screen screen, FormValues values) {
        HologramLine.Builder edited = line.toBuilder();
        fields(screen, line).forEach(field -> field.write().accept(edited, values));
        return edited.build();
    }

    static List<Field> fields(Screen screen, HologramLine line) {
        return FIELDS.stream().filter(field -> field.screen() == screen && field.shown().test(line)).toList();
    }

    private static Optional<HologramLine> line(String name, int page, int index) {
        return HologramModule.get().store().get(name).map(hologram -> hologram.pageView(page))
                .filter(view -> view.hasLine(index)).map(view -> HologramLine.parse(view.lines().get(index)));
    }

    // ------------------------------------------------------------------
    // The kinds of row
    // ------------------------------------------------------------------

    /** A number box; {@code none} is the value that writes no tag, and an empty box means it. */
    private static Field number(Screen screen, String id, Function<Dialogs, String> label, Function<Dialogs, String> hint,
                                Function<HologramLine, Double> read, BiConsumer<HologramLine.Builder, Double> write,
                                double none) {
        FormKey<BigDecimal> key = FormKey.decimal(id);
        return new Field(screen, line -> true,
                (form, line) -> hint(form.field(key, FormField.decimal(key, label.apply(words()))
                        .defaultValue(BigDecimal.valueOf(read.apply(line) == null ? none : read.apply(line)).stripTrailingZeros())
                        .optional()), hint),
                (line, values) -> {
                    double value = values.getOr(key, BigDecimal.valueOf(none)).doubleValue();
                    write.accept(line, value == none ? null : value);
                });
    }

    /** A text box; an empty one writes no tag. */
    private static Field text(Screen screen, String id, Function<Dialogs, String> label, Function<Dialogs, String> hint,
                              Function<HologramLine, String> read, BiConsumer<HologramLine.Builder, String> write) {
        FormKey<String> key = FormKey.text(id);
        return new Field(screen, line -> true,
                (form, line) -> hint(form.field(key, FormField.text(key, label.apply(words()))
                        .defaultValue(read.apply(line) == null ? "" : read.apply(line)).optional()), hint),
                (line, values) -> write.accept(line, values.getOr(key, "").trim()));
    }

    /** A button that cycles through a few options, starting on the line's. */
    private static Field choice(Screen screen, String id, Function<Dialogs, String> label, Function<Dialogs, String> hint,
                                BiFunction<Dialogs, HologramLine, List<FormField.Option>> options,
                                Function<HologramLine, String> read, BiConsumer<HologramLine.Builder, String> write) {
        FormKey<String> key = FormKey.text(id);
        return new Field(screen, line -> true,
                (form, line) -> hint(form.choice(key, label.apply(words()), read.apply(line), options.apply(words(), line)), hint),
                (line, values) -> write.accept(line, values.getOr(key, read.apply(line.build()))));
    }

    /**
     * The frames of a {@code <frames>} line, one per row, written back joined by
     * {@link HologramLine#FRAME_SEPARATOR}: the line's whole text, split where it takes turns.
     */
    private static Field frames() {
        FormKey<String> key = FormKey.text("frames_text");
        return new Field(Screen.FRAMES, line -> true,
                (form, line) -> {
                    form.field(key, FormField.text(key, words().framesTextLabel())
                            .defaultValue(String.join("\n", line.text().split(Pattern.quote(HologramLine.FRAME_SEPARATOR), -1)))
                            .lines(6).optional());
                    form.hint(words().framesHint());
                },
                (line, values) -> line.text(joinFrames(values.getOr(key, ""))));
    }

    /** Rows typed in a box as frames: one per row, blank rows at the end dropped. */
    static String joinFrames(String rows) {
        List<String> frames = new ArrayList<>(Arrays.asList(rows.split("\\R", -1)));
        while (frames.size() > 1 && frames.get(frames.size() - 1).isBlank()) frames.remove(frames.size() - 1);
        return String.join(HologramLine.FRAME_SEPARATOR, frames);
    }

    /** Empty for off, {@code on} for the hologram's curve, or its own {@code start,end,max}; a typo keeps the line's. */
    private static void autoscale(HologramLine.Builder line, String typed) {
        if (typed.isEmpty() || typed.equalsIgnoreCase(OFF)) {
            line.autoscale(null);
        } else if (typed.equalsIgnoreCase(ON)) {
            line.autoscale("");
        } else {
            String curve = HologramLine.parse("<autoscale:" + typed + ">").autoscale();
            if (curve != null) line.autoscale(curve);
        }
    }

    private static String toggle(Boolean value) {
        return value == null ? INHERIT : value ? ON : OFF;
    }

    private static Boolean toggle(String key) {
        return INHERIT.equals(key) ? null : ON.equals(key);
    }

    private static void hint(EditorForm form, Function<Dialogs, String> hint) {
        if (hint != null) form.hint(hint.apply(words()));
    }

    private static Dialogs words() {
        return HologramsMessages.get().dialogs();
    }

    // ------------------------------------------------------------------
    // The options of each pick; a value typed by command that is not offered is kept on the list
    // ------------------------------------------------------------------

    private static List<FormField.Option> switches(Dialogs words, HologramLine line) {
        return List.of(new FormField.Option(INHERIT, words.inherit()), new FormField.Option(ON, words.on()),
                new FormField.Option(OFF, words.off()));
    }

    private static List<FormField.Option> opacities(Dialogs words, HologramLine line) {
        List<FormField.Option> options = new ArrayList<>(List.of(new FormField.Option(INHERIT, words.inherit())));
        for (int percent : new int[]{100, 75, 50, 25}) {
            options.add(new FormField.Option(String.valueOf(Math.round(percent * 2.55)), percent + "%"));
        }
        keep(options, line.opacity() == null ? null : String.valueOf(line.opacity()));
        return options;
    }

    /** The hologram's, then each name as the tag writes it. */
    private static List<FormField.Option> named(Dialogs words, String current, String... names) {
        List<FormField.Option> options = new ArrayList<>(List.of(new FormField.Option(INHERIT, words.inherit())));
        for (String each : names) options.add(new FormField.Option(each, each));
        keep(options, current);
        return options;
    }

    private static List<FormField.Option> lights(Dialogs words, HologramLine line) {
        List<FormField.Option> options = new ArrayList<>(List.of(new FormField.Option(INHERIT, words.inherit()),
                new FormField.Option("-1", words.lightHere())));
        for (int level : new int[]{0, 5, 10, 15}) options.add(new FormField.Option(String.valueOf(level), String.valueOf(level)));
        keep(options, line.brightness() == null ? null : String.valueOf(line.brightness()));
        return options;
    }

    private static List<FormField.Option> shines(Dialogs words, HologramLine line) {
        List<FormField.Option> options = new ArrayList<>(List.of(new FormField.Option(OFF, words.off()),
                new FormField.Option(ON, words.on())));
        SHINE_COLOURS.forEach(colour -> options.add(new FormField.Option(colour, "{" + colour + "}" + colour)));
        keep(options, line.shine() == null || line.shine().isEmpty() ? null : line.shine());
        return options;
    }

    private static void keep(List<FormField.Option> options, String current) {
        if (current != null && options.stream().noneMatch(option -> option.key().equals(current))) {
            options.add(new FormField.Option(current, current));
        }
    }
}
