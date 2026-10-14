package net.exylia.exyliaDisplays.npc;

import net.exylia.exyliaDisplays.ExyliaDisplays;
import net.exylia.exyliaDisplays.Permissions;
import net.exylia.exyliaDisplays.common.Completion;
import net.exylia.exyliaDisplays.common.store.Place;
import net.exylia.exyliaDisplays.hologram.HologramModule;
import net.exylia.lib.npc.NpcAnimation;
import net.exylia.lib.npc.NpcAnimations;
import net.exylia.lib.npc.NpcLook;
import net.exylia.lib.npc.NpcPerformance;
import net.exylia.lib.npc.NpcPose;
import net.exylia.lib.text.Values;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
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
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * {@code /npc <name> <property> ...}: every setting of one NPC, as a command, for the console,
 * for scripts and for whoever types faster than they click. What is not about one NPC (the list,
 * create, import, reload) is {@link NpcAdminCommand}.
 *
 * <p>Every edit goes through the same store the screens use, so a command and a click can never
 * disagree. Label lines and action numbers are counted from 1, as a player reads them.
 */
@Command({"npc", "npcs", "exylianpc"})
@CommandPermission(Permissions.NPCS)
public final class NpcCommand {

    private static NpcsMessages messages() {
        return NpcsMessages.get();
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
            if (exists(player, name)) NpcViews.openHub(player, name);
        } else {
            info(sender, name);
        }
    }

    @Subcommand("<name> info")
    public void info(CommandSender sender, @SuggestWith(Names.class) String name) {
        StoredNpc npc = NpcModule.get().store().get(name).orElse(null);
        if (npc == null) {
            send(sender, messages().notFound(), Values.of().putText("name", name));
            return;
        }
        ExyliaDisplays.getInstance().getMessages().send(sender, messages().info(), NpcViews.values(name, npc));
    }

    @Subcommand("<name> equipment")
    public void equipment(Player player, @SuggestWith(Names.class) String name) {
        if (exists(player, name)) NpcViews.openEquipment(player, name);
    }

    // ------------------------------------------------------------------
    // The NPC itself
    // ------------------------------------------------------------------

    /** Typed on purpose, so it deletes at once: no confirmation and no screen, unlike the delete button. */
    @Subcommand("<name> delete")
    public void delete(CommandSender sender, @SuggestWith(Names.class) String name) {
        send(sender, NpcModule.get().store().delete(name) ? messages().deleted() : messages().notFound(),
                Values.of().putText("name", name));
    }

    @Subcommand("<name> rename")
    public void rename(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(NpcSuggestions.class) String renamed) {
        NpcDialogs.rename(sender, name, renamed.toLowerCase(Locale.ROOT));
    }

    @Subcommand("<name> clone")
    public void cloneNpc(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(NpcSuggestions.class) String copy) {
        NpcDialogs.copy(sender, name, copy.toLowerCase(Locale.ROOT));
    }

    /** Puts it back as it was before its last change, from a command, a screen or the file. */
    @Subcommand("<name> undo")
    public void undo(CommandSender sender, @SuggestWith(Names.class) String name) {
        NpcDialogs.undo(sender, name);
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
        NpcActions.teleport(player, name);
    }

    @Subcommand("<name> movehere")
    public void moveHere(Player player, @SuggestWith(Names.class) String name) {
        Place here = Place.of(player.getLocation());
        edited(player, name, current -> current.withLocation(here), messages().moved(), Values.of());
    }

    /** Moves it without turning it; the world stays the same unless one is given. */
    @Subcommand("<name> move")
    public void move(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(NpcSuggestions.class) double x,
                     @SuggestWith(NpcSuggestions.class) double y, @SuggestWith(NpcSuggestions.class) double z,
                     @Optional @SuggestWith(NpcSuggestions.class) String world) {
        property(sender, name, "location", NpcViews.number(x) + " " + NpcViews.number(y) + " " + NpcViews.number(z),
                current -> current.withLocation(new Place(world == null ? current.location().world() : world,
                        x, y, z, current.location().yaw(), current.location().pitch())));
    }

    @Subcommand("<name> center")
    public void centre(CommandSender sender, @SuggestWith(Names.class) String name) {
        StoredNpc saved = edited(sender, name, current -> current.withLocation(current.location().centred()), null, Values.of());
        if (saved != null) set(sender, name, "location", NpcViews.xyz(saved));
    }

    /** Turns it: yaw 0 south, 90 west, 180 north, -90 east; pitch looks down (positive) or up. */
    @Subcommand("<name> rotate")
    public void rotate(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(NpcSuggestions.class) double yaw,
                       @Optional @SuggestWith(NpcSuggestions.class) Double pitch) {
        double tilt = pitch == null ? 0 : pitch;
        if (tilt < -90 || tilt > 90) {
            invalid(sender, "pitch", NpcViews.number(tilt), "-90 - 90");
            return;
        }
        StoredNpc saved = edited(sender, name, current -> {
            Place at = current.location();
            return current.withLocation(new Place(at.world(), at.x(), at.y(), at.z(), yaw, tilt));
        }, null, Values.of());
        if (saved != null) {
            set(sender, name, "facing", NpcViews.number(saved.location().yaw()) + "° " + NpcViews.number(saved.location().pitch()) + "°");
        }
    }

    /** Turns it, where it stands, to face the player. */
    @Subcommand("<name> face")
    public void face(Player player, @SuggestWith(Names.class) String name) {
        NpcDialogs.faceMe(player, player, name);
    }

    /** Fine adjustment with the mouse wheel; again saves it. See {@link NpcAdjust}. */
    @Subcommand("<name> adjust")
    public void adjust(Player player, @SuggestWith(Names.class) String name) {
        NpcModule.get().adjust().toggle(player, name);
    }

    @Subcommand("<name> adjust save")
    public void adjustSave(Player player, @SuggestWith(Names.class) String name) {
        NpcModule.get().adjust().finish(player, name, true);
    }

    @Subcommand("<name> adjust cancel")
    public void adjustCancel(Player player, @SuggestWith(Names.class) String name) {
        NpcModule.get().adjust().finish(player, name, false);
    }

    // ------------------------------------------------------------------
    // How it looks
    // ------------------------------------------------------------------

    /** An account name, {@code @mirror} for each viewer's own, or {@code @none} for the default. */
    @Subcommand("<name> skin")
    public void skin(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(NpcSuggestions.class) String skin) {
        StoredNpc.Skin parsed = StoredNpc.Skin.parse(skin);
        if (parsed == null) {
            send(sender, messages().invalidSkin(), Values.of().putText("skin", skin));
            return;
        }
        property(sender, name, "skin", skin, current -> current.withSkin(parsed));
    }

    /** An uploaded skin's texture value and signature, as mineskin.org gives them. */
    @Subcommand("<name> texture")
    public void texture(CommandSender sender, @SuggestWith(Names.class) String name, String value,
                        @Optional String signature) {
        String sign = signature == null ? "" : signature.trim();
        property(sender, name, "skin", "texture", current -> current.withSkin(StoredNpc.Skin.texture(value.trim(), sign)));
    }

    @Subcommand("<name> pose")
    public void pose(CommandSender sender, @SuggestWith(Names.class) String name, NpcPose pose) {
        property(sender, name, "pose", pose.name().toLowerCase(Locale.ROOT), current -> current.withPose(pose));
    }

    @Subcommand("<name> scale")
    public void scale(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(NpcSuggestions.class) double scale) {
        if (scale < 0.0625 || scale > 16) {
            invalid(sender, "scale", NpcViews.number(scale), "0.0625 - 16");
            return;
        }
        property(sender, name, "scale", NpcViews.number(scale), current -> current.withScale(scale));
    }

    /** An outline colour as {@code #RRGGBB}, or {@code none}. */
    @Subcommand("<name> glow")
    public void glow(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(NpcSuggestions.class) String colour) {
        String glow = colour.equalsIgnoreCase("none") || colour.equalsIgnoreCase("off") ? "" : colour.trim();
        if (!glow.isEmpty() && !StoredNpc.isColour(glow)) {
            send(sender, messages().invalidColour(), Values.of().putText("value", colour));
            return;
        }
        String written = glow.isEmpty() || glow.startsWith("#") ? glow : "#" + glow;
        property(sender, name, "glow", written.isEmpty() ? "none" : written, current -> current.withGlow(written));
    }

    /**
     * Puts an item in a slot: the one written (a material, {@code playerhead-<name>},
     * {@code basehead-<texture>}; {@code air} empties it), or else the one in the player's hand.
     * {@code clear} empties every slot.
     */
    @Subcommand("<name> equip")
    public void equip(CommandSender sender, @SuggestWith(Names.class) String name,
                      @SuggestWith(NpcSuggestions.class) String slot,
                      @Optional @SuggestWith(NpcSuggestions.class) String item) {
        if (slot.equalsIgnoreCase("clear")) {
            edited(sender, name, current -> current.withEquipment(Map.of()), messages().equipmentCleared(), Values.of());
            return;
        }
        EquipmentSlot parsed = StoredNpc.slot(slot);
        if (parsed == null) {
            send(sender, messages().invalidSlot(), Values.of().putText("slot", slot)
                    .put("slots", String.join(", ", StoredNpc.SLOTS)));
            return;
        }
        if (item != null) {
            String icon = item.trim();
            boolean empty = icon.isEmpty() || icon.equalsIgnoreCase("air");
            edited(sender, name, current -> current.equip(parsed, icon), empty ? messages().unequipped() : messages().equipped(),
                    Values.of().put("slot", StoredNpc.slotName(parsed)));
        } else if (sender instanceof Player player) {
            if (exists(player, name)) NpcDialogs.equipFromHand(player, player, name, parsed);
        } else {
            invalid(sender, "item", "", "DIAMOND_SWORD, playerhead-<name>, basehead-<texture>, air");
        }
    }

    // ------------------------------------------------------------------
    // Where it looks, and who sees it
    // ------------------------------------------------------------------

    @Subcommand("<name> look")
    public void look(CommandSender sender, @SuggestWith(Names.class) String name,
                     @SuggestWith(NpcSuggestions.class) String mode,
                     @Optional @SuggestWith(NpcSuggestions.class) Double range) {
        // Written the way npcs.yml writes it (each-viewer), or as the constant (EACH_VIEWER).
        NpcLook look;
        try {
            look = NpcLook.valueOf(mode.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException unknown) {
            send(sender, messages().invalidLook(), Values.of().putText("value", mode));
            return;
        }
        if (range != null && range < 0) {
            invalid(sender, "look range", NpcViews.number(range), "0+");
            return;
        }
        String value = look.name().toLowerCase(Locale.ROOT).replace('_', '-') + (range == null ? "" : " " + NpcViews.number(range));
        property(sender, name, "look", value, current -> current.withLook(new StoredNpc.Look(look,
                range == null ? current.look().range() : range)));
    }

    @Subcommand("<name> viewdistance")
    public void viewDistance(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(NpcSuggestions.class) double blocks) {
        if (blocks <= 0) {
            invalid(sender, "view-distance", NpcViews.number(blocks), "> 0");
            return;
        }
        property(sender, name, "view-distance", NpcViews.number(blocks), current -> current.withViewDistance(blocks));
    }

    /** Without a permission, everyone sees it. */
    @Subcommand("<name> permission")
    public void permission(CommandSender sender, @SuggestWith(Names.class) String name, @Optional @SuggestWith(NpcSuggestions.class) String permission) {
        String value = permission == null ? "" : permission.trim();
        property(sender, name, "permission", value.isEmpty() ? "-" : value, current -> current.withPermission(value));
    }

    // ------------------------------------------------------------------
    // Label
    // ------------------------------------------------------------------

    @Subcommand("<name> label set")
    public void labelSet(CommandSender sender, @SuggestWith(Names.class) String name,
                         @SuggestWith(NpcSuggestions.class) String text) {
        label(sender, name, current -> List.of(text));
    }

    @Subcommand("<name> label add")
    public void labelAdd(CommandSender sender, @SuggestWith(Names.class) String name, String text) {
        label(sender, name, current -> {
            List<String> lines = new ArrayList<>(current);
            lines.add(text);
            return lines;
        });
    }

    @Subcommand("<name> label remove")
    public void labelRemove(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(NpcSuggestions.class) int line) {
        StoredNpc npc = NpcModule.get().store().get(name).orElse(null);
        if (npc != null && (line < 1 || line > npc.label().lines().size())) {
            invalid(sender, "label line", String.valueOf(line), "1 - " + npc.label().lines().size());
            return;
        }
        label(sender, name, current -> {
            List<String> lines = new ArrayList<>(current);
            if (line <= lines.size()) lines.remove(line - 1);
            return lines;
        });
    }

    @Subcommand("<name> label clear")
    public void labelClear(CommandSender sender, @SuggestWith(Names.class) String name) {
        edited(sender, name, current -> current.withLabelLines(List.of()), messages().labelCleared(), Values.of());
    }

    /** The gap between the top of its head and the label's bottom line. */
    @Subcommand("<name> label height")
    public void labelHeight(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(NpcSuggestions.class) double blocks) {
        property(sender, name, "label height", NpcViews.number(blocks), current -> current.withLabel(
                new StoredNpc.Label(current.label().lines(), blocks)));
    }

    private static void label(CommandSender sender, String name, UnaryOperator<List<String>> change) {
        StoredNpc saved = edited(sender, name, current -> current.withLabelLines(change.apply(current.label().lines())),
                null, Values.of());
        if (saved != null) {
            send(sender, messages().labelSaved(), Values.of().putText("name", name).put("count", saved.label().lines().size()));
        }
    }

    // ------------------------------------------------------------------
    // Linked hologram
    // ------------------------------------------------------------------

    @Subcommand("<name> link")
    public void link(CommandSender sender, @SuggestWith(Names.class) String name,
                     @SuggestWith(Holograms.class) String hologram) {
        HologramModule holograms = HologramModule.get();
        if (holograms == null) {
            send(sender, messages().hologramsOff(), Values.of());
            return;
        }
        if (!holograms.store().exists(hologram)) {
            send(sender, messages().hologramNotFound(), Values.of().putText("hologram", hologram));
            return;
        }
        edited(sender, name, current -> current.withLinkedHologram(hologram), messages().linked(),
                Values.of().putText("hologram", hologram));
    }

    @Subcommand("<name> unlink")
    public void unlink(CommandSender sender, @SuggestWith(Names.class) String name) {
        edited(sender, name, current -> current.withLinkedHologram(""), messages().unlinked(), Values.of());
    }

    // ------------------------------------------------------------------
    // What clicking it does
    // ------------------------------------------------------------------

    @Subcommand("<name> action add")
    public void actionAdd(CommandSender sender, @SuggestWith(Names.class) String name, String line) {
        StoredNpc saved = edited(sender, name, current -> current.addAction(line), null, Values.of());
        if (saved != null) {
            send(sender, messages().actionAdded(), Values.of().putText("name", name)
                    .put("index", saved.interactions().actions().size()));
        }
    }

    @Subcommand("<name> action remove")
    public void actionRemove(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(NpcSuggestions.class) int index) {
        StoredNpc npc = NpcModule.get().store().get(name).orElse(null);
        if (npc == null) {
            send(sender, messages().notFound(), Values.of().putText("name", name));
            return;
        }
        if (!npc.hasAction(index - 1)) {
            send(sender, messages().actionNotFound(), Values.of().put("index", index)
                    .put("count", npc.interactions().actions().size()));
            return;
        }
        edited(sender, name, current -> current.hasAction(index - 1) ? current.removeAction(index - 1) : current,
                messages().actionRemoved(), Values.of("index", index));
    }

    @Subcommand("<name> action clear")
    public void actionClear(CommandSender sender, @SuggestWith(Names.class) String name) {
        edited(sender, name, current -> current.withInteractions(current.interactions().withActions(List.of())),
                messages().actionsCleared(), Values.of());
    }

    @Subcommand("<name> action list")
    public void actionList(CommandSender sender, @SuggestWith(Names.class) String name) {
        StoredNpc npc = NpcModule.get().store().get(name).orElse(null);
        if (npc == null) {
            send(sender, messages().notFound(), Values.of().putText("name", name));
            return;
        }
        List<String> actions = npc.interactions().actions();
        if (actions.isEmpty()) {
            send(sender, messages().actionsEmpty(), Values.of().putText("name", name));
            return;
        }
        send(sender, messages().actionsHeader(), Values.of().putText("name", name).put("count", actions.size()));
        for (int i = 0; i < actions.size(); i++) {
            send(sender, messages().actionLine(), Values.of().put("index", i + 1).putText("action", actions.get(i)));
        }
    }

    /** Who may use it; without a permission, everyone. */
    @Subcommand("<name> action permission")
    public void actionPermission(CommandSender sender, @SuggestWith(Names.class) String name, @Optional @SuggestWith(NpcSuggestions.class) String permission) {
        String value = permission == null ? "" : permission.trim();
        interactions(sender, name, "use permission", value.isEmpty() ? "-" : value,
                click -> new StoredNpc.Interactions(click.actions(), value, click.cooldown(), click.denyMessage(),
                        click.cooldownMessage(), click.faceClicker()));
    }

    /** Between two uses by the same player: {@code 500ms}, {@code 2s}, {@code 1m}, or 0. */
    @Subcommand("<name> action cooldown")
    public void actionCooldown(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(NpcSuggestions.class) String time) {
        long millis = NpcDialogs.cooldown(time);
        if (millis < 0) {
            invalid(sender, "cooldown", time, "500ms, 2s, 1m, 0");
            return;
        }
        interactions(sender, name, "cooldown", NpcViews.number(millis / 1000.0) + "s",
                click -> new StoredNpc.Interactions(click.actions(), click.permission(), millis, click.denyMessage(),
                        click.cooldownMessage(), click.faceClicker()));
    }

    /** Told to a player without the permission; without text, the one in messages/npcs.yml. */
    @Subcommand("<name> action denymessage")
    public void actionDenyMessage(CommandSender sender, @SuggestWith(Names.class) String name, @Optional @SuggestWith(NpcSuggestions.class) String text) {
        String value = text == null ? "" : text.trim();
        interactions(sender, name, "deny message", value.isEmpty() ? "-" : value,
                click -> new StoredNpc.Interactions(click.actions(), click.permission(), click.cooldown(), value,
                        click.cooldownMessage(), click.faceClicker()));
    }

    /** Told to a player still waiting; without text, the one in messages/npcs.yml. */
    @Subcommand("<name> action cooldownmessage")
    public void actionCooldownMessage(CommandSender sender, @SuggestWith(Names.class) String name, @Optional @SuggestWith(NpcSuggestions.class) String text) {
        String value = text == null ? "" : text.trim();
        interactions(sender, name, "cooldown message", value.isEmpty() ? "-" : value,
                click -> new StoredNpc.Interactions(click.actions(), click.permission(), click.cooldown(),
                        click.denyMessage(), value, click.faceClicker()));
    }

    @Subcommand("<name> action faceclicker")
    public void actionFaceClicker(CommandSender sender, @SuggestWith(Names.class) String name, boolean value) {
        interactions(sender, name, "face clicker", String.valueOf(value),
                click -> new StoredNpc.Interactions(click.actions(), click.permission(), click.cooldown(),
                        click.denyMessage(), click.cooldownMessage(), value));
    }

    // ------------------------------------------------------------------
    // Animations
    // ------------------------------------------------------------------

    /**
     * What a trigger plays: {@code left-click}, {@code right-click}, {@code idle} or {@code greet}
     * take an animation (no text or {@code none} clears it); {@code idle-every} and
     * {@code greet-cooldown} a length of time; {@code greet-range} and {@code perform-range} blocks.
     * An animation is vanilla moves or one performance ({@code anim:}, {@code emote:}, {@code keys:}).
     */
    @Subcommand("<name> animation")
    public void animation(CommandSender sender, @SuggestWith(Names.class) String name,
                          @SuggestWith(NpcSuggestions.class) String trigger,
                          @Optional @SuggestWith(NpcSuggestions.class) String value) {
        String text = value == null || value.isBlank() || value.trim().equalsIgnoreCase("none")
                || value.trim().equalsIgnoreCase("reset") ? "" : value.trim();
        String key = trigger.trim().toLowerCase(Locale.ROOT);
        switch (key) {
            case "idle-every", "greet-cooldown" -> {
                long millis = NpcDialogs.cooldown(text);
                if (text.isEmpty() || millis < 0) {
                    invalid(sender, "animation " + key, text, "500ms, 20s, 1m, 0");
                    return;
                }
                java.time.Duration time = java.time.Duration.ofMillis(millis);
                property(sender, name, "animation " + key, NpcViews.seconds(time), current -> {
                    NpcAnimations a = current.animations();
                    return current.withAnimations(key.equals("idle-every")
                            ? new NpcAnimations(a.leftClick(), a.rightClick(), a.idle(), time, a.greet(), a.greetRange(), a.greetCooldown(), a.performRange())
                            : new NpcAnimations(a.leftClick(), a.rightClick(), a.idle(), a.idleEvery(), a.greet(), a.greetRange(), time, a.performRange()));
                });
            }
            case "greet-range", "perform-range" -> {
                double blocks;
                try {
                    blocks = Double.parseDouble(text);
                } catch (NumberFormatException typo) {
                    blocks = -1;
                }
                if (blocks < 0) {
                    invalid(sender, "animation " + key, text, "0+");
                    return;
                }
                double range = blocks;
                property(sender, name, "animation " + key, NpcViews.number(range), current -> {
                    NpcAnimations a = current.animations();
                    return current.withAnimations(key.equals("greet-range")
                            ? new NpcAnimations(a.leftClick(), a.rightClick(), a.idle(), a.idleEvery(), a.greet(),
                                    range, a.greetCooldown(), a.performRange())
                            : new NpcAnimations(a.leftClick(), a.rightClick(), a.idle(), a.idleEvery(), a.greet(),
                                    a.greetRange(), a.greetCooldown(), range));
                });
            }
            default -> {
                if (new StoredNpc().withAnimation(key, text) == null) {
                    invalid(sender, "animation", trigger, String.join(", ", StoredNpc.TRIGGERS)
                            + ", idle-every, greet-range, greet-cooldown, perform-range");
                    return;
                }
                if (!NpcDialogs.readable(sender, text)) return;
                property(sender, name, "animation " + key, text.isEmpty() ? "-" : text,
                        current -> current.withAnimation(key, text));
            }
        }
    }

    /**
     * Plays an animation now, for everyone who sees it, to try it out. A performance plays for
     * everyone within its perform-range, and says so when it was skipped.
     */
    @Subcommand("<name> play")
    public void play(CommandSender sender, @SuggestWith(Names.class) String name, @SuggestWith(NpcSuggestions.class) String animation) {
        if (!exists(sender, name) || !NpcDialogs.readable(sender, animation)) return;
        var live = NpcModule.get().renderer().live(name);
        if (live.isEmpty()) {
            send(sender, messages().animations().notShown(), Values.of().putText("name", name));
            return;
        }
        Values values = Values.of().putText("name", name).putText("animation", animation.trim());
        if (!NpcPerformance.isPerformance(animation.trim())) {
            live.get().play(NpcAnimation.parse(animation));
            send(sender, messages().animations().played(), values);
            return;
        }
        StoredNpc npc = NpcModule.get().store().get(name).orElseThrow();
        NpcsMessages.Animations words = messages().animations();
        if (npc.sitting()) {
            send(sender, words.performSitting(), values);
        } else if (live.get().isPerforming()) {
            send(sender, words.performBusy(), values);
        } else if (live.get().perform(NpcPerformance.parse(animation.trim()))) {
            send(sender, words.played(), values);
        } else {
            send(sender, words.performSkipped(), values.put("range", NpcViews.number(npc.animations().performRange())));
        }
    }

    /** Sits on an invisible seat, hips at its location, or stands. */
    @Subcommand("<name> sit")
    public void sit(CommandSender sender, @SuggestWith(Names.class) String name,
                    @revxrsal.commands.annotation.Suggest({"on", "off"}) String state) {
        Boolean sitting = switch (state.trim().toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes" -> true;
            case "off", "false", "no" -> false;
            default -> null;
        };
        if (sitting == null) {
            invalid(sender, "sitting", state, "on, off");
            return;
        }
        property(sender, name, "sitting", sitting ? "on" : "off", current -> current.withSitting(sitting));
    }

    // ------------------------------------------------------------------

    private static void interactions(CommandSender sender, String name, String property, String value,
                                     UnaryOperator<StoredNpc.Interactions> change) {
        property(sender, name, property, value, current -> current.withInteractions(change.apply(current.interactions())));
    }

    private static void property(CommandSender sender, String name, String property, String value,
                                 UnaryOperator<StoredNpc> change) {
        edited(sender, name, change, messages().propertySet(), Values.of().putText("property", property).putText("value", value));
    }

    private static void set(CommandSender sender, String name, String property, String value) {
        send(sender, messages().propertySet(), Values.of().putText("name", name).putText("property", property)
                .putText("value", value));
    }

    private static void invalid(CommandSender sender, String property, String value, String expected) {
        send(sender, messages().invalidValue(), Values.of().putText("property", property)
                .putText("value", value == null ? "" : value).putText("expected", expected));
    }

    private static void state(CommandSender sender, String name, boolean enabled) {
        StoredNpc saved = edited(sender, name, current -> current.withEnabled(enabled), null, Values.of());
        if (saved != null) send(sender, messages().stateChanged(), NpcViews.values(name, saved));
    }

    /**
     * Applies a change, then tells the sender {@code done} with the NPC's name added.
     *
     * @return the NPC after the change, or {@code null} when there is none by that name
     */
    private static StoredNpc edited(CommandSender sender, String name, UnaryOperator<StoredNpc> change,
                                    String done, Values values) {
        StoredNpc saved = NpcModule.get().store().edit(name, change).orElse(null);
        if (saved == null) {
            send(sender, messages().notFound(), Values.of().putText("name", name));
            return null;
        }
        if (done != null) send(sender, done, values.putText("name", name));
        return saved;
    }

    private static boolean exists(CommandSender sender, String name) {
        if (NpcModule.get().store().exists(name)) return true;
        send(sender, messages().notFound(), Values.of().putText("name", name));
        return false;
    }

    // ------------------------------------------------------------------
    // Tab completion
    // ------------------------------------------------------------------

    /** Every NPC's name. */
    public static final class Names implements Completion {
        @Override
        public Collection<String> suggest(List<String> words, Player player) {
            return NpcModule.get() == null ? List.of() : NpcModule.get().store().names();
        }
    }

    /** Every hologram's name, while the hologram module runs. */
    public static final class Holograms implements Completion {
        @Override
        public Collection<String> suggest(List<String> words, Player player) {
            return HologramModule.get() == null ? List.of() : HologramModule.get().store().names();
        }
    }

    /** The special skins, and the players online. */
    public static final class Skins implements Completion {
        @Override
        public Collection<String> suggest(List<String> words, Player player) {
            List<String> skins = new ArrayList<>(List.of("@mirror", "@none"));
            Bukkit.getOnlinePlayers().forEach(online -> skins.add(online.getName()));
            return skins;
        }
    }
}
