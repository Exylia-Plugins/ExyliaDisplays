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
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The style of one line, split by theme so every window fits on screen without scrolling: a small
 * chooser, then {@link Screen#LOOK}, {@link Screen#MOTION} or {@link Screen#REACH}.
 *
 * <p>Every setting is one row of {@link #FIELDS}: the window it sits in, how it is asked, and how
 * the answer goes back through {@link HologramLine.Builder}, which stays the only thing that reads
 * and writes tags. Adding a tag to a window is one more row there, plus its label in
 * {@link Dialogs}; the windows, their order and the saving follow from the table.
 */
final class LineStyle {

    /** The windows, in the order the chooser offers them. */
    enum Screen {
        LOOK(Material.GLOW_INK_SAC, Dialogs::lookTitle, Dialogs::lookButton, Dialogs::lookButtonHint),
        MOTION(Material.FEATHER, Dialogs::motionTitle, Dialogs::motionButton, Dialogs::motionButtonHint),
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

    /**
     * Every setting of every window, in the order they are asked. One row per tag: the helpers
     * below cover a number, a text and a pick from a few options.
     */
    static final List<Field> FIELDS = List.of(
            // Look
            number(Screen.LOOK, "size", Dialogs::sizeLabel, null, HologramLine::size, HologramLine.Builder::size, 1),
            choice(Screen.LOOK, "opacity", Dialogs::opacityLabel, Dialogs::opacityHint, LineStyle::opacities,
                    line -> line.opacity() == null ? INHERIT : String.valueOf(line.opacity()),
                    (line, key) -> line.opacity(INHERIT.equals(key) ? null : Integer.valueOf(key))),
            choice(Screen.LOOK, "shadow", Dialogs::shadowLabel, null, LineStyle::switches,
                    line -> line.shadow() == null ? INHERIT : line.shadow() ? ON : OFF,
                    (line, key) -> line.shadow(INHERIT.equals(key) ? null : ON.equals(key))),
            text(Screen.LOOK, "background", Dialogs::backgroundLabel, Dialogs::backgroundHint,
                    HologramLine::background, HologramLine.Builder::background),
            text(Screen.LOOK, "glow", Dialogs::glowLabel, Dialogs::glowHint,
                    line -> line.glow() == null ? "" : line.glow().isEmpty() ? "team" : line.glow(),
                    (line, glow) -> line.glow(glow.isEmpty() ? null : glow.equalsIgnoreCase("team") ? "" : glow))
                    .only(HologramLine::isModel),
            text(Screen.LOOK, "head", Dialogs::headLabel, Dialogs::headHint, HologramLine::head,
                    (line, head) -> { if (!head.isEmpty()) line.head(head); })
                    .only(line -> line.head() != null),
            // Motion
            number(Screen.MOTION, "spin", Dialogs::spinLabel, Dialogs::motionHint, HologramLine::spin, HologramLine.Builder::spin, 0),
            number(Screen.MOTION, "bob", Dialogs::bobLabel, null, HologramLine::bob, HologramLine.Builder::bob, 0),
            number(Screen.MOTION, "pulse", Dialogs::pulseLabel, null, HologramLine::pulse, HologramLine.Builder::pulse, 0),
            choice(Screen.MOTION, "shine", Dialogs::shineLabel, Dialogs::shineHint, LineStyle::shines,
                    line -> line.shine() == null ? OFF : line.shine().isEmpty() ? ON : line.shine(),
                    (line, key) -> line.shine(OFF.equals(key) ? null : ON.equals(key) ? "" : key))
                    .only(line -> !line.isModel()),
            number(Screen.MOTION, "flow", Dialogs::flowLabel, null, HologramLine::flow, HologramLine.Builder::flow, 0)
                    .only(line -> !line.isModel()),
            number(Screen.MOTION, "typewriter", Dialogs::typewriterLabel, null, HologramLine::typewriter,
                    HologramLine.Builder::typewriter, 0)
                    .only(line -> !line.isModel()),
            // Who sees it
            number(Screen.REACH, "range", Dialogs::rangeLabel, null, HologramLine::range, HologramLine.Builder::range, 0),
            text(Screen.REACH, "permission", Dialogs::linePermissionLabel, Dialogs::linePermissionHint,
                    HologramLine::permission, HologramLine.Builder::permission),
            text(Screen.REACH, "condition", Dialogs::conditionLabel, Dialogs::conditionHint,
                    HologramLine::condition, HologramLine.Builder::condition));

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
                                Arrays.asList(Screen.values()))
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
        EditorForm form = HologramDialogs.form(player,
                Values.of("line", index + 1).apply(screen.title.apply(HologramsMessages.get().dialogs())));
        fields(screen, line).forEach(field -> field.ask().accept(form, line));
        HologramDialogs.answer(player, form.ask(values -> apply(line, screen, values)), back,
                styled -> HologramDialogs.save(player, name, current -> current.onPage(page, view -> view.hasLine(index)
                        ? view.setLine(index, styled.raw()) : view), back));
    }

    /** The line with what one window answered; the tags of the other windows are kept. */
    static HologramLine apply(HologramLine line, Screen screen, FormValues values) {
        HologramLine.Builder edited = line.toBuilder();
        fields(screen, line).forEach(field -> field.write().accept(edited, values));
        return edited.build();
    }

    private static List<Field> fields(Screen screen, HologramLine line) {
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
