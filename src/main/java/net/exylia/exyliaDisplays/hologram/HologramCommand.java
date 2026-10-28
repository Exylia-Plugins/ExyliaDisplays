package net.exylia.exyliaDisplays.hologram;

import net.exylia.exyliaDisplays.ExyliaDisplays;
import net.exylia.exyliaDisplays.Permissions;
import net.exylia.exyliaDisplays.common.Completion;
import net.exylia.exyliaDisplays.common.store.Position;
import net.exylia.lib.hologram.HologramConfig;
import net.exylia.lib.hologram.HologramLine;
import net.exylia.lib.hologram.Holograms;
import net.exylia.lib.text.Values;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.CommandPlaceholder;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.annotation.Single;
import revxrsal.commands.annotation.Subcommand;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * {@code /holo <name> <property> ...}: every setting of one hologram, as a command, for the
 * console, for scripts and for whoever types faster than they click. What is not about one
 * hologram (the list, create, import, reload) is {@link HologramAdminCommand}.
 *
 * <p>Every edit goes through the same store the screens use, so a command and a click can never
 * disagree. Line numbers are counted from 1, top first, as a player reads them.
 */
@Command({"holo", "hologram"})
@CommandPermission(Permissions.HOLOGRAMS)
public final class HologramCommand {

    /** The tags {@code /holo <name> line <n> <tag>} sets, as a line writes them. */
    static final List<String> TAGS = List.of("text", "preset", "size", "head", "opacity", "background", "shadow",
            "glow", "gap", "align", "width", "billboard", "seethrough", "brightness", "autoscale", "spin", "sway", "bob",
            "pulse", "shine", "flow", "rainbow", "typewriter", "scramble", "marquee", "fade", "blink", "frames", "range",
            "permission", "condition", "button");
    /**
     * The tags set by reading them as a line writes them, options included, so
     * {@code line 2 sway 20,period=6} is {@code <sway:20,period=6>} and {@code on} is the bare tag.
     */
    private static final java.util.Set<String> WRITTEN = java.util.Set.of("gap", "align", "width", "billboard",
            "seethrough", "brightness", "autoscale", "sway", "bob", "pulse", "shine", "rainbow", "scramble", "marquee",
            "fade", "blink", "frames", "button");
    private static final Pattern PALETTE = Pattern.compile("\\{[a-z_]+}");
    private static final Pattern COLOUR = Pattern.compile("#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})");

    private static HologramsMessages messages() {
        return HologramsMessages.get();
    }

    private static void send(CommandSender to, String line, Values values) {
        ExyliaDisplays.getInstance().getMessages().send(to, line, values);
    }

    @CommandPlaceholder
    public void root(CommandSender sender) {
        help(sender);
    }

    @Subcommand("help")
    public void help(CommandSender sender) {
        ExyliaDisplays.getInstance().getMessages().send(sender, messages().help(), Values.of());
    }

    /** Its editor for a player, its details for the console. */
    @Subcommand("<name>")
    public void open(CommandSender sender, @Single @SuggestWith(Names.class) String name) {
        if (sender instanceof Player player) {
            if (exists(player, name)) HologramViews.openHub(player, name);
        } else {
            info(sender, name);
        }
    }

    @Subcommand("<name> lines")
    public void lines(Player player, @SuggestWith(Names.class) String name) {
        if (exists(player, name)) HologramViews.openLines(player, name);
    }

    @Subcommand("<name> info")
    public void info(CommandSender sender, @SuggestWith(Names.class) String name) {
        StoredHologram hologram = HologramModule.get().store().get(name).orElse(null);
        if (hologram == null) {
            send(sender, messages().notFound(), Values.of().putText("name", name));
            return;
        }
        ExyliaDisplays.getInstance().getMessages().send(sender, messages().info(), HologramViews.values(name, hologram));
        List<List<String>> pages = hologram.allPages();
        for (int page = 0; page < pages.size(); page++) {
            List<String> lines = pages.get(page);
            for (int i = 0; i < lines.size(); i++) {
                HologramLine line = HologramLine.parse(lines.get(i));
                // With pages, a line is numbered page.line: 2.1 is the first line of page 2.
                String number = pages.size() > 1 ? (page + 1) + "." + (i + 1) : String.valueOf(i + 1);
                send(sender, messages().infoLine(), Values.of().put("line", number).put("text", HologramViews.lineText(line)));
            }
        }
        List<String> click = hologram.click().commands();
        for (int i = 0; i < click.size(); i++) {
            send(sender, messages().infoClick(), Values.of().put("action", i + 1).putText("text", click.get(i)));
        }
    }

    // ------------------------------------------------------------------
    // The hologram itself
    // ------------------------------------------------------------------

    /** Typed on purpose, so it deletes at once: no confirmation and no screen, unlike the delete button. */
    @Subcommand("<name> delete")
    public void delete(CommandSender sender, @SuggestWith(Names.class) String name) {
        Values values = Values.of().putText("name", name);
        send(sender, HologramModule.get().store().delete(name) ? messages().deleted() : messages().notFound(), values);
    }

    @Subcommand("<name> rename")
    public void rename(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(HologramSuggestions.class) String renamed) {
        HologramDialogs.rename(sender, name, renamed.toLowerCase(Locale.ROOT));
    }

    @Subcommand("<name> clone")
    public void cloneHologram(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(HologramSuggestions.class) String copy) {
        HologramDialogs.copy(sender, name, copy.toLowerCase(Locale.ROOT));
    }

    /** Puts it back as it was before its last change, from a command, a screen or the file. */
    @Subcommand("<name> undo")
    public void undo(CommandSender sender, @SuggestWith(Names.class) String name) {
        HologramDialogs.undo(sender, name);
    }

    /** Keeps its look under a name, replacing a template of that name. */
    @Subcommand("<name> savetemplate")
    public void saveTemplate(CommandSender sender, @SuggestWith(Names.class) String name,
                             @SuggestWith(Templates.class) String template) {
        HologramDialogs.saveTemplate(sender, name, template.toLowerCase(Locale.ROOT));
    }

    /** Dresses it as a template: lines, pages, appearance, refresh and click. */
    @Subcommand("<name> applytemplate")
    public void applyTemplate(CommandSender sender, @SuggestWith(Names.class) String name,
                              @SuggestWith(Templates.class) String template) {
        HologramDialogs.applyTemplate(sender, name, template.toLowerCase(Locale.ROOT));
    }

    @Subcommand("<name> enable")
    public void enable(CommandSender sender, @SuggestWith(Names.class) String name) {
        state(sender, name, true);
    }

    @Subcommand("<name> disable")
    public void disable(CommandSender sender, @SuggestWith(Names.class) String name) {
        state(sender, name, false);
    }

    // ------------------------------------------------------------------
    // Where it stands
    // ------------------------------------------------------------------

    @Subcommand("<name> tp")
    public void tp(Player player, @SuggestWith(Names.class) String name) {
        HologramActions.teleport(player, name);
    }

    @Subcommand("<name> movehere")
    public void moveHere(Player player, @SuggestWith(Names.class) String name) {
        Position here = Position.facing(player.getLocation());
        edited(player, name, current -> current.withLocation(here), messages().moved(), Values.of());
    }

    @Subcommand("<name> move")
    public void move(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(HologramSuggestions.class) double x,
                     @SuggestWith(HologramSuggestions.class) double y, @SuggestWith(HologramSuggestions.class) double z,
                     @Optional @SuggestWith(HologramSuggestions.class) String world) {
        edited(sender, name, current -> current.withLocation(new Position(
                world == null ? current.location().world() : world, x, y, z,
                current.location().yaw(), current.location().pitch())), messages().moved(), Values.of());
    }

    @Subcommand("<name> center")
    public void centre(CommandSender sender, @SuggestWith(Names.class) String name) {
        edited(sender, name, current -> current.withLocation(current.location().centred()), messages().centred(), Values.of());
    }

    /** Turns it: yaw 0 south, 90 west, 180 north, -90 east; pitch tilts it, -90 to 90. */
    @Subcommand("<name> rotate")
    public void rotate(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(HologramSuggestions.class) float yaw,
                       @Optional @SuggestWith(HologramSuggestions.class) Float pitch) {
        float tilt = pitch == null ? 0f : pitch;
        if (tilt < -90 || tilt > 90) {
            invalid(sender, "pitch", HologramViews.number(tilt), "-90 - 90");
            return;
        }
        StoredHologram saved = edited(sender, name, current -> current.withFacing(yaw, tilt), null, Values.of());
        if (saved != null) facingSet(sender, name, saved);
    }

    /** Turns it to face the player, without moving it. */
    @Subcommand("<name> face")
    public void face(Player player, @SuggestWith(Names.class) String name) {
        float yaw = Position.facing(player.getLocation()).yaw();
        StoredHologram saved = edited(player, name, current -> current.withFacing(yaw, 0f), null, Values.of());
        if (saved != null) facingSet(player, name, saved);
    }

    /** Fine adjustment with the mouse wheel; again saves it. See {@link HologramAdjust}. */
    @Subcommand("<name> adjust")
    public void adjust(Player player, @SuggestWith(Names.class) String name) {
        HologramModule.get().adjust().toggle(player, name);
    }

    @Subcommand("<name> adjust save")
    public void adjustSave(Player player, @SuggestWith(Names.class) String name) {
        HologramModule.get().adjust().finish(player, name, true);
    }

    @Subcommand("<name> adjust cancel")
    public void adjustCancel(Player player, @SuggestWith(Names.class) String name) {
        HologramModule.get().adjust().finish(player, name, false);
    }

    private static void facingSet(CommandSender sender, String name, StoredHologram saved) {
        send(sender, messages().propertySet(), Values.of().putText("name", name).putText("property", "facing")
                .put("value", HologramViews.facing(saved) + " " + saved.appearance().billboard()));
    }

    // ------------------------------------------------------------------
    // Lines
    // ------------------------------------------------------------------

    @Subcommand("<name> addline")
    public void addLine(CommandSender sender, @SuggestWith(Names.class) String name, String text) {
        addLine(sender, name, 1, text);
    }

    @Subcommand("<name> setline")
    public void setLine(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(HologramSuggestions.class) int line,
                        @SuggestWith(HologramSuggestions.class) String text) {
        setLine(sender, name, 1, line, text);
    }

    @Subcommand("<name> insertline")
    public void insertLine(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(HologramSuggestions.class) int line,
                        @SuggestWith(HologramSuggestions.class) String text) {
        insertLine(sender, name, 1, line, text);
    }

    @Subcommand("<name> removeline")
    public void removeLine(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(HologramSuggestions.class) int line) {
        removeLine(sender, name, 1, line);
    }

    @Subcommand("<name> moveline")
    public void moveLine(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(HologramSuggestions.class) int from,
                         @SuggestWith(HologramSuggestions.class) int to) {
        moveLine(sender, name, 1, from, to);
    }

    /**
     * One tag of one line, keeping its text and its other tags. Without a value, or with
     * {@code reset}, the tag is taken off and the line follows the hologram again.
     */
    @Subcommand("<name> line")
    public void lineTag(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(HologramSuggestions.class) int line,
                        @SuggestWith(Tags.class) String tag,
                        @Optional @SuggestWith(HologramSuggestions.class) String value) {
        lineTag(sender, name, 1, line, tag, value);
    }

    // ------------------------------------------------------------------
    // Pages: the line commands above, on another page
    // ------------------------------------------------------------------

    @Subcommand("<name> page add")
    public void pageAdd(CommandSender sender, @SuggestWith(Names.class) String name) {
        StoredHologram saved = edited(sender, name, StoredHologram::addPage, null, Values.of());
        if (saved != null) pagesSet(sender, name, saved);
    }

    @Subcommand("<name> page remove")
    public void pageRemove(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(HologramSuggestions.class) int page) {
        if (!hasPage(sender, name, page)) return;
        StoredHologram saved = edited(sender, name, current -> current.removePage(page - 1), null, Values.of());
        if (saved != null) pagesSet(sender, name, saved);
    }

    /** Turns the hologram to that page for the player who asked; {@code lines} opens the editor. */
    @Subcommand("<name> page")
    public void page(Player player, @SuggestWith(Names.class) String name, @SuggestWith(HologramSuggestions.class) int page) {
        if (!hasPage(player, name, page)) return;
        Holograms.get(ExyliaDisplays.getInstance().getPlugin(), name).ifPresent(live -> live.page(player, page - 1));
        send(player, messages().propertySet(), Values.of().putText("name", name).putText("property", "page").put("value", page));
    }

    @Subcommand("<name> pageinterval")
    public void pageInterval(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(HologramSuggestions.class) long ticks) {
        if (ticks < 0) {
            invalid(sender, "page-interval", String.valueOf(ticks), "0+");
            return;
        }
        property(sender, name, "page-interval", String.valueOf(ticks), current -> current.withPageInterval(ticks));
    }

    @Subcommand("<name> page <page> addline")
    public void addLine(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(HologramSuggestions.class) int page, String text) {
        if (!hasPage(sender, name, page)) return;
        StoredHologram saved = edited(sender, name, current -> current.onPage(page - 1, view -> view.addLine(text)), null, Values.of());
        if (saved != null) {
            send(sender, messages().lineAdded(), Values.of().putText("name", name)
                    .put("line", saved.pageView(page - 1).lines().size()));
        }
    }

    @Subcommand("<name> page <page> setline")
    public void setLine(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(HologramSuggestions.class) int page,
                        @SuggestWith(HologramSuggestions.class) int line, @SuggestWith(HologramSuggestions.class) String text) {
        if (hasLine(sender, name, page, line)) {
            edited(sender, name, current -> current.onPage(page - 1, view -> view.setLine(line - 1, text)),
                    messages().lineSet(), Values.of("line", line));
        }
    }

    @Subcommand("<name> page <page> insertline")
    public void insertLine(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(HologramSuggestions.class) int page,
                        @SuggestWith(HologramSuggestions.class) int line, @SuggestWith(HologramSuggestions.class) String text) {
        if (!hasPage(sender, name, page)) return;
        int count = HologramModule.get().store().get(name).orElseThrow().pageView(page - 1).lines().size();
        int at = Math.clamp(line - 1, 0, count);
        edited(sender, name, current -> current.onPage(page - 1, view -> view.insertLine(at, text)),
                messages().lineAdded(), Values.of("line", at + 1));
    }

    @Subcommand("<name> page <page> removeline")
    public void removeLine(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(HologramSuggestions.class) int page,
                           @SuggestWith(HologramSuggestions.class) int line) {
        if (hasLine(sender, name, page, line)) {
            edited(sender, name, current -> current.onPage(page - 1, view -> view.removeLine(line - 1)),
                    messages().lineRemoved(), Values.of("line", line));
        }
    }

    @Subcommand("<name> page <page> moveline")
    public void moveLine(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(HologramSuggestions.class) int page,
                         @SuggestWith(HologramSuggestions.class) int from, @SuggestWith(HologramSuggestions.class) int to) {
        if (hasLine(sender, name, page, from) && hasLine(sender, name, page, to)) {
            edited(sender, name, current -> current.onPage(page - 1, view -> view.moveLine(from - 1, to - 1)),
                    messages().lineMoved(), Values.of().put("line", from).put("to", to));
        }
    }

    @Subcommand("<name> page <page> line")
    public void lineTag(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(HologramSuggestions.class) int page,
                        @SuggestWith(HologramSuggestions.class) int line, @SuggestWith(Tags.class) String tag,
                        @Optional @SuggestWith(HologramSuggestions.class) String value) {
        if (!hasLine(sender, name, page, line)) return;
        String raw = HologramModule.get().store().get(name).orElseThrow().pageView(page - 1).lines().get(line - 1);
        String set = value == null || value.isBlank() || value.equalsIgnoreCase("reset") ? null : value.trim();
        String key = tag.toLowerCase(Locale.ROOT);
        if (key.equals("preset") || WRITTEN.contains(key)) {
            String styled;
            if (key.equals("preset")) {
                java.util.Map<String, String> presets = HologramsConfig.get().linePresets();
                String preset = set == null ? null : presets.get(set.toLowerCase(Locale.ROOT));
                if (preset == null) {
                    send(sender, messages().presetNotFound(), Values.of().putText("preset", set == null ? "" : set)
                            .putText("presets", String.join(", ", presets.keySet())));
                    return;
                }
                styled = LineStyle.withPreset(raw, preset);
            } else {
                styled = set == null ? cleared(raw, key) : written(raw, key, set);
            }
            if (styled == null) {
                invalid(sender, tag, value, "<" + key + ":...>, on, reset");
                return;
            }
            edited(sender, name, current -> current.onPage(page - 1,
                            view -> view.hasLine(line - 1) ? view.setLine(line - 1, styled) : view),
                    messages().lineSet(), Values.of("line", line));
            return;
        }
        HologramLine.Builder edited = HologramLine.parse(raw).toBuilder();
        try {
            switch (key) {
                case "text" -> edited.text(set == null ? "" : set);
                case "size" -> edited.size(decimal(set, 1));
                case "head" -> {
                    if (set == null) throw new IllegalArgumentException("a head needs a value");
                    edited.head(set);
                }
                case "opacity" -> edited.opacity(set == null ? null : within(Integer.parseInt(set), 0, 255));
                case "background" -> edited.background(colour(set));
                case "shadow" -> edited.shadow(set == null ? null : bool(set));
                case "glow" -> edited.glow(set == null ? null : set.equalsIgnoreCase("team") ? "" : set);
                case "spin" -> edited.spin(decimal(set, HologramLine.DEFAULT_SPIN));
                case "typewriter" -> edited.typewriter(decimal(set, HologramLine.DEFAULT_TYPEWRITER));
                case "flow" -> edited.flow(decimal(set, HologramLine.DEFAULT_FLOW));
                case "range" -> edited.range(decimal(set, 0));
                case "permission" -> edited.permission(set);
                case "condition" -> edited.condition(set);
                default -> {
                    invalid(sender, "tag", tag, String.join(", ", TAGS));
                    return;
                }
            }
        } catch (IllegalArgumentException wrong) {
            invalid(sender, tag, value, "reset");
            return;
        }
        String styled = edited.build().raw();
        edited(sender, name, current -> current.onPage(page - 1,
                        view -> view.hasLine(line - 1) ? view.setLine(line - 1, styled) : view),
                messages().lineSet(), Values.of("line", line));
    }

    /**
     * A line with one tag set as written, options included; {@code on} is the bare tag.
     *
     * @return the line, or {@code null} when the value does not read as that tag
     */
    static String written(String raw, String key, String value) {
        String tag = value.equalsIgnoreCase("on") ? "<" + key + ">"
                : "<" + key + ":" + (value.contains(">") ? "\"" + value + "\"" : value) + ">";
        if (!HologramLine.parse(tag).text().isEmpty()) return null;
        return LineStyle.withPreset(raw, tag);
    }

    /** A line without one of the {@link #WRITTEN} tags, and without its options. */
    static String cleared(String raw, String key) {
        HologramLine.Builder line = HologramLine.parse(raw).toBuilder();
        switch (key) {
            case "gap" -> line.gap(null);
            case "align" -> line.align(null);
            case "width" -> line.width(null);
            case "billboard" -> line.billboard(null);
            case "seethrough" -> line.seeThrough(null);
            case "brightness" -> line.brightness(null);
            case "autoscale" -> line.autoscale(null);
            case "sway" -> line.sway(null);
            case "bob" -> line.bob(null);
            case "pulse" -> line.pulse(null);
            case "shine" -> line.shine(null);
            case "rainbow" -> line.rainbow(null);
            case "scramble" -> line.scramble(null);
            case "marquee" -> line.marquee(null);
            case "fade" -> line.fade(null);
            case "blink" -> line.blink(null);
            case "frames" -> line.frames(null);
            case "button" -> line.button(null);
            default -> throw new IllegalArgumentException(key);
        }
        return line.build().raw();
    }

    private static void pagesSet(CommandSender sender, String name, StoredHologram saved) {
        send(sender, messages().propertySet(), Values.of().putText("name", name).putText("property", "pages")
                .put("value", saved.pageCount()));
    }

    // ------------------------------------------------------------------
    // How it is drawn
    // ------------------------------------------------------------------

    @Subcommand("<name> billboard")
    public void billboard(CommandSender sender, @SuggestWith(Names.class) String name,
                          @SuggestWith(HologramSuggestions.class) String mode) {
        String value = mode.toUpperCase(Locale.ROOT);
        if (!List.of("CENTER", "VERTICAL", "HORIZONTAL", "FIXED").contains(value)) {
            invalid(sender, "billboard", mode, "center, vertical, horizontal, fixed");
            return;
        }
        look(sender, name, "billboard", value, look -> look.billboard = value);
    }

    @Subcommand("<name> alignment")
    public void alignment(CommandSender sender, @SuggestWith(Names.class) String name,
                          @SuggestWith(HologramSuggestions.class) String mode) {
        String value = mode.toUpperCase(Locale.ROOT);
        if (!List.of("CENTER", "LEFT", "RIGHT").contains(value)) {
            invalid(sender, "alignment", mode, "center, left, right");
            return;
        }
        look(sender, name, "alignment", value, look -> look.alignment = value);
    }

    /** One number scales it evenly; three set each axis. */
    @Subcommand("<name> scale")
    public void scale(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(HologramSuggestions.class) double x,
                      @Optional @SuggestWith(HologramSuggestions.class) Double y, @Optional @SuggestWith(HologramSuggestions.class) Double z) {
        double sy = y == null ? x : y;
        double sz = z == null ? x : z;
        if (x <= 0 || sy <= 0 || sz <= 0) {
            invalid(sender, "scale", HologramViews.number(Math.min(x, Math.min(sy, sz))), "> 0");
            return;
        }
        look(sender, name, "scale", HologramViews.number(x) + " " + HologramViews.number(sy) + " " + HologramViews.number(sz),
                look -> {
                    look.scaleX = x;
                    look.scaleY = sy;
                    look.scaleZ = sz;
                });
    }

    @Subcommand("<name> shadow")
    public void shadow(CommandSender sender, @SuggestWith(Names.class) String name, boolean value) {
        look(sender, name, "shadow", String.valueOf(value), look -> look.shadow = value);
    }

    @Subcommand("<name> seethrough")
    public void seeThrough(CommandSender sender, @SuggestWith(Names.class) String name, boolean value) {
        look(sender, name, "see-through", String.valueOf(value), look -> look.seeThrough = value);
    }

    @Subcommand("<name> linewidth")
    public void lineWidth(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(HologramSuggestions.class) int pixels) {
        if (pixels < 1) {
            invalid(sender, "line-width", String.valueOf(pixels), "1+");
            return;
        }
        look(sender, name, "line-width", String.valueOf(pixels), look -> look.lineWidth = pixels);
    }

    @Subcommand("<name> spacing")
    public void spacing(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(HologramSuggestions.class) double blocks) {
        if (blocks < 0) {
            invalid(sender, "spacing", HologramViews.number(blocks), "0+");
            return;
        }
        look(sender, name, "spacing", HologramViews.number(blocks), look -> look.lineSpacing = blocks);
    }

    @Subcommand("<name> opacity")
    public void opacity(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(HologramSuggestions.class) int value) {
        if (value < 0 || value > 255) {
            invalid(sender, "opacity", String.valueOf(value), "0 - 255");
            return;
        }
        look(sender, name, "opacity", String.valueOf(value), look -> look.textOpacity = value);
    }

    /** {@code #AARRGGBB}, {@code none} for transparent, or {@code default} for the client's own. */
    @Subcommand("<name> background")
    public void background(CommandSender sender, @SuggestWith(Names.class) String name,
                           @SuggestWith(HologramSuggestions.class) String value) {
        boolean client = value.equalsIgnoreCase("default");
        String colour = value.equalsIgnoreCase("none") ? "#00000000" : value;
        if (!client && !COLOUR.matcher(colour).matches()) {
            invalid(sender, "background", value, "#AARRGGBB, none, default");
            return;
        }
        look(sender, name, "background", value, look -> {
            look.defaultBackground = client;
            if (!client) look.backgroundColor = colour;
        });
    }

    /** A fixed light level, or {@code -1} for the light where it stands. */
    @Subcommand("<name> brightness")
    public void brightness(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(HologramSuggestions.class) int level) {
        if (level < -1 || level > 15) {
            invalid(sender, "brightness", String.valueOf(level), "-1 - 15");
            return;
        }
        look(sender, name, "brightness", String.valueOf(level), look -> look.brightness = level);
    }

    /** Grows it with the viewer's distance: {@code on}, {@code off}, or {@code <start> <end> <min> <max>}, which turns it on. */
    @Subcommand("<name> distancescale")
    public void distanceScale(CommandSender sender, @SuggestWith(Names.class) String name,
                              @SuggestWith(HologramSuggestions.class) String value) {
        StoredHologram hologram = HologramModule.get().store().get(name).orElse(null);
        if (hologram == null) {
            send(sender, messages().notFound(), Values.of().putText("name", name));
            return;
        }
        HologramConfig.DistanceScale curve = distanceScale(hologram.appearance().distanceScale(), value);
        if (curve == null) {
            invalid(sender, "distance-scale", value, "on, off, <start> <end> <min 0.1-10> <max 0.1-10>");
            return;
        }
        property(sender, name, "distance-scale", curve.enabled()
                        ? HologramViews.number(curve.min()) + "x-" + HologramViews.number(curve.max()) + "x, "
                        + HologramViews.number(curve.start()) + "-" + HologramViews.number(curve.end()) + " blocks" : "off",
                current -> current.withAppearance(current.appearance().withDistanceScale(curve)));
    }

    /**
     * What {@code /holo <name> distancescale} reads: {@code on} or {@code off} keep the curve, four
     * numbers replace it and turn it on.
     *
     * @return the curve, or {@code null} when the value does not read
     */
    static HologramConfig.DistanceScale distanceScale(HologramConfig.DistanceScale current, String value) {
        String[] parts = value == null ? new String[0] : value.trim().split("\\s+");
        if (parts.length == 1) {
            return switch (parts[0].toLowerCase(Locale.ROOT)) {
                case "on", "true", "yes" -> new HologramConfig.DistanceScale(true, current.start(), current.end(), current.min(), current.max());
                case "off", "false", "no" -> new HologramConfig.DistanceScale(false, current.start(), current.end(), current.min(), current.max());
                default -> null;
            };
        }
        if (parts.length != 4) return null;
        try {
            double start = Double.parseDouble(parts[0]);
            double end = Double.parseDouble(parts[1]);
            double min = Double.parseDouble(parts[2]);
            double max = Double.parseDouble(parts[3]);
            if (start < 0 || end <= start || min < 0.1 || min > 10 || max < 0.1 || max > 10) return null;
            return new HologramConfig.DistanceScale(true, start, end, min, max);
        } catch (NumberFormatException typo) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Who sees it
    // ------------------------------------------------------------------

    @Subcommand("<name> viewdistance")
    public void viewDistance(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(HologramSuggestions.class) double blocks) {
        if (blocks <= 0) {
            invalid(sender, "view-distance", HologramViews.number(blocks), "> 0");
            return;
        }
        property(sender, name, "view-distance", HologramViews.number(blocks), current -> current.withViewDistance(blocks));
    }

    /** Without a permission, everyone sees it. */
    @Subcommand("<name> permission")
    public void permission(CommandSender sender, @SuggestWith(Names.class) String name, @Optional @SuggestWith(HologramSuggestions.class) String permission) {
        String value = permission == null ? "" : permission.trim();
        property(sender, name, "permission", value.isEmpty() ? "-" : value, current -> current.withPermission(value));
    }

    @Subcommand("<name> perplayer")
    public void perPlayer(CommandSender sender, @SuggestWith(Names.class) String name, boolean value) {
        property(sender, name, "per-player", String.valueOf(value), current -> current.withPerPlayer(value));
    }

    /** Ticks between refreshes of its placeholders. */
    @Subcommand("<name> refresh")
    public void refresh(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(HologramSuggestions.class) int ticks) {
        if (ticks < 1) {
            invalid(sender, "refresh", String.valueOf(ticks), "1+");
            return;
        }
        property(sender, name, "refresh", String.valueOf(ticks), current -> current.withRefresh(
                new HologramConfig.Refresh(ticks, current.refresh().autoUpdate())));
    }

    @Subcommand("<name> autoupdate")
    public void autoUpdate(CommandSender sender, @SuggestWith(Names.class) String name, boolean value) {
        property(sender, name, "auto-update", String.valueOf(value), current -> current.withRefresh(
                new HologramConfig.Refresh(current.refresh().updateInterval(), value)));
    }

    // ------------------------------------------------------------------
    // What clicking it does
    // ------------------------------------------------------------------

    @Subcommand("<name> click add")
    public void clickAdd(CommandSender sender, @SuggestWith(Names.class) String name,
                         @SuggestWith(HologramSuggestions.class) String action) {
        click(sender, name, "click", action, commands -> commands.add(action));
    }

    @Subcommand("<name> click remove")
    public void clickRemove(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(HologramSuggestions.class) int action) {
        StoredHologram hologram = HologramModule.get().store().get(name).orElse(null);
        if (hologram != null && (action < 1 || action > hologram.click().commands().size())) {
            invalid(sender, "click", String.valueOf(action), "1 - " + hologram.click().commands().size());
            return;
        }
        click(sender, name, "click", "-" + action, commands -> commands.remove(action - 1));
    }

    @Subcommand("<name> click clear")
    public void clickClear(CommandSender sender, @SuggestWith(Names.class) String name) {
        click(sender, name, "click", "-", commands -> commands.clear());
    }

    @Subcommand("<name> click width")
    public void clickWidth(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(HologramSuggestions.class) double blocks) {
        if (blocks <= 0) {
            invalid(sender, "click width", HologramViews.number(blocks), "> 0");
            return;
        }
        property(sender, name, "click width", HologramViews.number(blocks), current -> current.withClick(
                StoredHologram.clickWith(current.click(), current.click().commands(), blocks, current.click().cooldown(), current.click().hoverScale())));
    }

    /** Milliseconds before the same player's next click counts. */
    @Subcommand("<name> click cooldown")
    public void clickCooldown(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(HologramSuggestions.class) long millis) {
        property(sender, name, "click cooldown", Math.max(0, millis) + "ms", current -> current.withClick(
                StoredHologram.clickWith(current.click(), current.click().commands(), current.click().width(), millis, current.click().hoverScale())));
    }

    /** How much it grows while looked at; 1 turns it off. */
    @Subcommand("<name> click hover")
    public void clickHover(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(HologramSuggestions.class) double scale) {
        if (scale <= 0) {
            invalid(sender, "click hover", HologramViews.number(scale), "> 0");
            return;
        }
        property(sender, name, "click hover", HologramViews.number(scale), current -> current.withClick(
                StoredHologram.clickWith(current.click(), current.click().commands(), current.click().width(), current.click().cooldown(), scale)));
    }

    /** {@code button} draws a panel behind what can be clicked; {@code none} only grows it. */
    @Subcommand("<name> click style")
    public void clickStyle(CommandSender sender, @SuggestWith(Names.class) String name,
                           @SuggestWith(HologramSuggestions.class) String style) {
        String value = style.toUpperCase(Locale.ROOT);
        if (!value.equals(HologramConfig.Click.STYLE_BUTTON) && !value.equals(HologramConfig.Click.STYLE_NONE)) {
            invalid(sender, "click style", style, "button, none");
            return;
        }
        clickLook(sender, name, "click style", value, click -> restyled(click, value, click.color(), click.hoverColor(), click.sound()));
    }

    /** The panel's colour: a palette name such as {primary}, or #RRGGBB / #AARRGGBB. */
    @Subcommand("<name> click color")
    public void clickColor(CommandSender sender, @SuggestWith(Names.class) String name,
                           @SuggestWith(HologramSuggestions.class) String colour) {
        if (!panelColour(colour)) {
            invalid(sender, "click color", colour, "{primary}, #RRGGBB, #AARRGGBB");
            return;
        }
        clickLook(sender, name, "click color", colour, click -> restyled(click, click.style(), colour, click.hoverColor(), click.sound()));
    }

    /** The panel's colour while a player looks at it. */
    @Subcommand("<name> click hovercolor")
    public void clickHoverColor(CommandSender sender, @SuggestWith(Names.class) String name,
                                @SuggestWith(HologramSuggestions.class) String colour) {
        if (!panelColour(colour)) {
            invalid(sender, "click hover-color", colour, "{secondary}, #RRGGBB, #AARRGGBB");
            return;
        }
        clickLook(sender, name, "click hover-color", colour, click -> restyled(click, click.style(), click.color(), colour, click.sound()));
    }

    /** A sound on every click, as [SOUND] takes it; none clears it. */
    @Subcommand("<name> click sound")
    public void clickSound(CommandSender sender, @SuggestWith(Names.class) String name,
                           @Optional @SuggestWith(HologramSuggestions.class) String sound) {
        String value = sound == null || sound.isBlank() || sound.equalsIgnoreCase("none") ? "" : sound.trim();
        clickLook(sender, name, "click sound", value.isEmpty() ? "-" : value,
                click -> restyled(click, click.style(), click.color(), click.hoverColor(), value));
    }

    /**
     * Adds an action to a button, which a line tagged {@code <button:name>} runs when clicked;
     * {@code remove} removes the button.
     */
    @Subcommand("<name> button")
    public void button(CommandSender sender, @SuggestWith(Names.class) String name,
                       @SuggestWith(HologramSuggestions.class) String button,
                       @SuggestWith(HologramSuggestions.class) String action) {
        String id = button.toLowerCase(Locale.ROOT);
        if (!net.exylia.exyliaDisplays.common.store.Names.valid(id)) {
            invalid(sender, "button", button, "a-z, 0-9, - and _");
            return;
        }
        boolean remove = action.trim().equalsIgnoreCase("remove");
        clickLook(sender, name, "button " + id, remove ? "-" : action.trim(), click -> {
            java.util.Map<String, List<String>> buttons = new java.util.LinkedHashMap<>(click.buttons());
            if (remove) {
                buttons.remove(id);
            } else {
                List<String> lines = new ArrayList<>(buttons.getOrDefault(id, List.of()));
                lines.add(action.trim());
                buttons.put(id, lines);
            }
            return click.withButtons(buttons);
        });
    }

    private static boolean panelColour(String colour) {
        return PALETTE.matcher(colour).matches() || COLOUR.matcher(colour).matches();
    }

    private static HologramConfig.Click restyled(HologramConfig.Click click, String style, String color, String hoverColor,
                                                 String sound) {
        return new HologramConfig.Click(click.commands(), click.width(), click.cooldown(), click.hoverScale(),
                style, color, hoverColor, sound, click.buttons(), click.range());
    }

    private static void clickLook(CommandSender sender, String name, String property, String value,
                                  UnaryOperator<HologramConfig.Click> change) {
        property(sender, name, property, value, current -> current.withClick(change.apply(current.click())));
    }

    // ------------------------------------------------------------------
    // Which players see it
    // ------------------------------------------------------------------

    /** {@code all}: everyone but the players it is hidden from. {@code manual}: only the players it is shown to. */
    @Subcommand("<name> visibility")
    public void visibilityMode(CommandSender sender, @SuggestWith(Names.class) String name,
                               @SuggestWith(HologramSuggestions.class) String mode) {
        String value = mode.toLowerCase(Locale.ROOT);
        if (!value.equals(StoredHologram.Visibility.ALL) && !value.equals(StoredHologram.Visibility.MANUAL)) {
            invalid(sender, "visibility", mode, "all, manual");
            return;
        }
        StoredHologram saved = edited(sender, name, current -> current.withVisibility(current.visibility().withMode(value)),
                null, Values.of());
        if (saved != null) {
            send(sender, messages().audience().modeSet(), Values.of().putText("name", name)
                    .put("mode", HologramViews.visibility(saved)));
        }
    }

    @Subcommand("<name> show")
    public void show(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(Players.class) String player) {
        audience(sender, name, player, true);
    }

    @Subcommand("<name> hide")
    public void hide(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(Players.class) String player) {
        audience(sender, name, player, false);
    }

    /** The players the mode's list names: who sees it in {@code manual}, who does not in {@code all}. */
    @Subcommand("<name> viewers")
    public void viewers(CommandSender sender, @SuggestWith(Names.class) String name) {
        StoredHologram hologram = HologramModule.get().store().get(name).orElse(null);
        if (hologram == null) {
            send(sender, messages().notFound(), Values.of().putText("name", name));
            return;
        }
        StoredHologram.Visibility visibility = hologram.visibility();
        HologramsMessages.Audience words = messages().audience();
        send(sender, words.list(), Values.of().putText("name", name)
                .put("mode", visibility.manual() ? words.manual() : words.all())
                .put("list", visibility.manual() ? words.shownToLabel() : words.hiddenFromLabel())
                .put("count", visibility.players().size())
                .put("players", HologramViews.players(visibility.players())));
    }

    @Subcommand("<name> viewers clear")
    public void viewersClear(CommandSender sender, @SuggestWith(Names.class) String name) {
        edited(sender, name, current -> current.withVisibility(current.visibility().cleared()),
                messages().audience().cleared(), Values.of());
    }

    private static void audience(CommandSender sender, String name, String player, boolean show) {
        if (!exists(sender, name)) return;
        List<UUID> players = new ArrayList<>();
        if (player.equalsIgnoreCase("@a")) {
            Bukkit.getOnlinePlayers().forEach(online -> players.add(online.getUniqueId()));
        } else {
            OfflinePlayer found = HologramActions.player(player);
            if (found == null) {
                send(sender, messages().audience().playerNotFound(), Values.of().putText("player", player));
                return;
            }
            players.add(found.getUniqueId());
        }
        edited(sender, name, current -> current.withVisibility(show
                        ? current.visibility().show(players) : current.visibility().hide(players)),
                show ? messages().audience().shown() : messages().audience().hidden(),
                Values.of().put("players", HologramViews.players(players.stream().map(UUID::toString).toList())));
    }

    // ------------------------------------------------------------------
    // What it rides
    // ------------------------------------------------------------------

    /** Rides a player (by name or UUID), an entity (by UUID), the entity nearest to the player, or an NPC ({@code npc:<name>}). */
    @Subcommand("<name> attach")
    public void attach(CommandSender sender, @SuggestWith(Names.class) String name,
                       @SuggestWith(Targets.class) String target) {
        HologramActions.attach(sender, name, target);
    }

    @Subcommand("<name> detach")
    public void detach(CommandSender sender, @SuggestWith(Names.class) String name) {
        HologramActions.detach(sender, name);
    }

    /** How high above the entity it rides. */
    @Subcommand("<name> attachoffset")
    public void attachOffset(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(HologramSuggestions.class) double blocks) {
        if (blocks < -16 || blocks > 16) {
            invalid(sender, "attach-offset", HologramViews.number(blocks), "-16 - 16");
            return;
        }
        property(sender, name, "attach-offset", HologramViews.number(blocks),
                current -> current.withAttach(current.attach().withOffset(blocks)));
    }

    // ------------------------------------------------------------------

    /** The appearance as fields, so one command changes one of them and keeps the rest. */
    private static final class Look {
        String billboard, alignment, backgroundColor;
        double scaleX, scaleY, scaleZ, lineSpacing;
        boolean shadow, seeThrough, defaultBackground, glowing;
        int lineWidth, textOpacity, brightness;
        HologramConfig.DistanceScale distanceScale;

        Look(HologramConfig.Properties p) {
            billboard = p.billboard();
            alignment = p.alignment();
            scaleX = p.scaleX();
            scaleY = p.scaleY();
            scaleZ = p.scaleZ();
            shadow = p.shadow();
            seeThrough = p.seeThrough();
            lineWidth = p.lineWidth();
            lineSpacing = p.lineSpacing();
            textOpacity = p.textOpacity();
            defaultBackground = p.defaultBackground();
            backgroundColor = p.backgroundColor();
            brightness = p.brightness();
            glowing = p.glowing();
            distanceScale = p.distanceScale();
        }

        HologramConfig.Properties build() {
            return new HologramConfig.Properties(billboard, alignment, scaleX, scaleY, scaleZ, shadow, seeThrough,
                    lineWidth, lineSpacing, textOpacity, defaultBackground, backgroundColor, brightness, glowing, distanceScale);
        }
    }

    private static void look(CommandSender sender, String name, String property, String value,
                             java.util.function.Consumer<Look> change) {
        property(sender, name, property, value, current -> {
            Look look = new Look(current.appearance());
            change.accept(look);
            return current.withAppearance(look.build());
        });
    }

    private static void click(CommandSender sender, String name, String property, String value,
                              java.util.function.Consumer<List<String>> change) {
        property(sender, name, property, value, current -> {
            HologramConfig.Click click = current.click();
            List<String> commands = new ArrayList<>(click.commands());
            change.accept(commands);
            return current.withClick(StoredHologram.clickWith(click, commands, click.width(), click.cooldown(), click.hoverScale()));
        });
    }

    private static void property(CommandSender sender, String name, String property, String value,
                                 UnaryOperator<StoredHologram> change) {
        edited(sender, name, change, messages().propertySet(), Values.of().putText("property", property).putText("value", value));
    }

    private static void invalid(CommandSender sender, String property, String value, String expected) {
        send(sender, messages().invalidValue(), Values.of().putText("property", property)
                .putText("value", value == null ? "" : value).putText("expected", expected));
    }

    /** {@code null} takes the tag off; {@code on} is the tag's own default. */
    private static Double decimal(String value, double bare) {
        if (value == null) return null;
        return value.equalsIgnoreCase("on") ? bare : Double.parseDouble(value);
    }

    private static int within(int value, int min, int max) {
        if (value < min || value > max) throw new IllegalArgumentException(value + " out of range");
        return value;
    }

    private static String colour(String value) {
        if (value != null && !COLOUR.matcher(value).matches()) throw new IllegalArgumentException(value);
        return value;
    }

    private static boolean bool(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "on", "yes" -> true;
            case "false", "off", "no" -> false;
            default -> throw new IllegalArgumentException(value);
        };
    }

    private static void state(CommandSender sender, String name, boolean enabled) {
        StoredHologram saved = edited(sender, name, current -> current.withEnabled(enabled), null, Values.of());
        if (saved != null) send(sender, messages().stateChanged(), HologramViews.values(name, saved));
    }

    /**
     * Applies a change, then tells the sender {@code done} with the hologram's name added.
     *
     * @return the hologram after the change, or {@code null} when there is none by that name
     */
    private static StoredHologram edited(CommandSender sender, String name, UnaryOperator<StoredHologram> change,
                                         String done, Values values) {
        StoredHologram saved = HologramModule.get().store().edit(name, change).orElse(null);
        if (saved == null) {
            send(sender, messages().notFound(), Values.of().putText("name", name));
            return null;
        }
        if (done != null) send(sender, done, values.putText("name", name));
        return saved;
    }

    private static boolean exists(CommandSender sender, String name) {
        if (HologramModule.get().store().exists(name)) return true;
        send(sender, messages().notFound(), Values.of().putText("name", name));
        return false;
    }

    private static boolean hasPage(CommandSender sender, String name, @SuggestWith(HologramSuggestions.class) int page) {
        StoredHologram hologram = HologramModule.get().store().get(name).orElse(null);
        if (hologram == null) {
            send(sender, messages().notFound(), Values.of().putText("name", name));
            return false;
        }
        if (!hologram.hasPage(page - 1)) {
            invalid(sender, "page", String.valueOf(page), "1 - " + hologram.pageCount());
            return false;
        }
        return true;
    }

    private static boolean hasLine(CommandSender sender, String name, @SuggestWith(HologramSuggestions.class) int page,
                           @SuggestWith(HologramSuggestions.class) int line) {
        if (!hasPage(sender, name, page)) return false;
        StoredHologram view = HologramModule.get().store().get(name).orElseThrow().pageView(page - 1);
        if (!view.hasLine(line - 1)) {
            send(sender, messages().lineNotFound(), Values.of().put("line", line).put("count", view.lines().size()));
            return false;
        }
        return true;
    }

    /** Every hologram's name, for tab completion. */
    public static final class Names implements Completion {

        @Override
        public Collection<String> suggest(List<String> words, Player player) {
            return HologramModule.get() == null ? List.of() : HologramModule.get().store().names();
        }
    }

    /** The tags {@code line <n> <tag>} sets. */
    public static final class Tags implements Completion {

        @Override
        public Collection<String> suggest(List<String> words, Player player) {
            return TAGS;
        }
    }

    /** Every template's name; saving also offers the hologram's own name. */
    public static final class Templates implements Completion {

        @Override
        public Collection<String> suggest(List<String> words, Player player) {
            if (HologramModule.get() == null) return List.of();
            List<String> names = new ArrayList<>(HologramModule.get().templates().names());
            if (Completion.word(words, 2).equalsIgnoreCase("savetemplate")) names.add(Completion.word(words, 1));
            return names;
        }
    }

    /** What attach takes: online players, {@code nearest} and {@code npc:<name>}. */
    public static final class Targets implements Completion {

        @Override
        public Collection<String> suggest(List<String> words, Player player) {
            List<String> names = new ArrayList<>(List.of("nearest"));
            Bukkit.getOnlinePlayers().forEach(online -> names.add(online.getName()));
            names.addAll(HologramActions.npcTargets());
            return names;
        }
    }

    /** Online players, {@code @a} and {@code nearest}, for show and hide. */
    public static final class Players implements Completion {

        @Override
        public Collection<String> suggest(List<String> words, Player player) {
            List<String> names = new ArrayList<>(List.of("@a", "nearest"));
            Bukkit.getOnlinePlayers().forEach(online -> names.add(online.getName()));
            return names;
        }
    }
}
