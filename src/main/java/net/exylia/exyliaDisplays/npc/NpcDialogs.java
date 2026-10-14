package net.exylia.exyliaDisplays.npc;

import net.exylia.exyliaDisplays.ExyliaDisplays;
import net.exylia.exyliaDisplays.common.store.Names;
import net.exylia.exyliaDisplays.common.store.Place;
import net.exylia.exyliaDisplays.hologram.HologramModule;
import net.exylia.lib.effect.Ticks;
import net.exylia.lib.input.FormField;
import net.exylia.lib.input.FormKey;
import net.exylia.lib.item.Source;
import net.exylia.lib.npc.NpcAnimation;
import net.exylia.lib.npc.NpcAnimations;
import net.exylia.lib.npc.NpcLook;
import net.exylia.lib.npc.NpcPose;
import net.exylia.lib.text.Values;
import net.exylia.lib.util.editor.EditorForm;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Every window that changes an NPC, and the edits commands share with them.
 *
 * <p>As with holograms, each window opens with what it edits already in its boxes, asks about one
 * subject at once, saves through the module's store, and brings back the screen the player came
 * from with the change on it.
 */
public final class NpcDialogs {

    private static final FormKey<String> NAME = FormKey.text("name");
    private static final FormKey<String> PRESET = FormKey.text("preset");
    private static final FormKey<String> SKIN = FormKey.text("skin");
    private static final FormKey<String> SKIN_TYPE = FormKey.text("skin_type");
    private static final FormKey<String> TEXTURE = FormKey.text("texture");
    private static final FormKey<String> SIGNATURE = FormKey.text("signature");
    private static final FormKey<String> POSE = FormKey.text("pose");
    private static final FormKey<BigDecimal> SCALE = FormKey.decimal("scale");
    private static final FormKey<String> GLOW = FormKey.text("glow");
    private static final FormKey<String> LINES = FormKey.text("lines");
    private static final FormKey<BigDecimal> HEIGHT = FormKey.decimal("height");
    private static final FormKey<String> HOLOGRAM = FormKey.text("hologram");
    private static final FormKey<String> ACTIONS = FormKey.text("actions");
    private static final FormKey<String> USE_PERMISSION = FormKey.text("use_permission");
    private static final FormKey<String> COOLDOWN = FormKey.text("cooldown");
    private static final FormKey<String> DENY = FormKey.text("deny");
    private static final FormKey<String> WAIT = FormKey.text("wait");
    private static final FormKey<Boolean> FACE = FormKey.flag("face");
    private static final FormKey<String> LOOK = FormKey.text("look");
    private static final FormKey<BigDecimal> LOOK_RANGE = FormKey.decimal("look_range");
    private static final FormKey<BigDecimal> VIEW_DISTANCE = FormKey.decimal("view_distance");
    private static final FormKey<String> PERMISSION = FormKey.text("permission");
    private static final FormKey<String> WORLD = FormKey.text("world");
    private static final FormKey<BigDecimal> X = FormKey.decimal("x");
    private static final FormKey<BigDecimal> Y = FormKey.decimal("y");
    private static final FormKey<BigDecimal> Z = FormKey.decimal("z");
    private static final FormKey<BigDecimal> YAW = FormKey.decimal("yaw");
    private static final FormKey<BigDecimal> PITCH = FormKey.decimal("pitch");
    private static final FormKey<Boolean> SITTING = FormKey.flag("sitting");
    private static final FormKey<String> LEFT_CLICK = FormKey.text("left_click");
    private static final FormKey<String> RIGHT_CLICK = FormKey.text("right_click");
    private static final FormKey<String> IDLE = FormKey.text("idle");
    private static final FormKey<String> IDLE_EVERY = FormKey.text("idle_every");
    private static final FormKey<String> GREET = FormKey.text("greet");
    private static final FormKey<BigDecimal> GREET_RANGE = FormKey.decimal("greet_range");
    private static final FormKey<String> GREET_COOLDOWN = FormKey.text("greet_cooldown");

    private NpcDialogs() {
        throw new AssertionError("No instances.");
    }

    private static ExyliaDisplays plugin() {
        return ExyliaDisplays.getInstance();
    }

    private static NpcsMessages messages() {
        return NpcsMessages.get();
    }

    private static NpcsMessages.Dialogs words() {
        return NpcsMessages.get().dialogs();
    }

    private static EditorForm form(Player player, String title) {
        return EditorForm.of(plugin().getPlugin(), player, title);
    }

    // ------------------------------------------------------------------
    // Creating
    // ------------------------------------------------------------------

    /** Name, preset and skin in one window; the new NPC stands where the player does. */
    public static void create(Player player, Runnable back) {
        NpcsConfig config = NpcsConfig.get();
        List<FormField.Option> presets = new ArrayList<>();
        config.presets().forEach((id, preset) -> presets.add(new FormField.Option(id, preset.name())));
        EditorForm form = form(player, words().createTitle())
                .field(NAME, FormField.text(NAME, words().nameLabel())
                        .defaultValue(NpcModule.get().store().freeName("npc"))
                        .validate(name -> Names.valid(name.trim()), plain(messages().invalidName())))
                .hint(words().nameHint());
        if (!presets.isEmpty()) {
            form.choice(PRESET, words().presetLabel(), presets.get(0).key(), presets);
        }
        form.field(SKIN, FormField.text(SKIN, words().skinLabel()).defaultValue(player.getName()).optional()
                        .validate(skin -> StoredNpc.Skin.parse(skin) != null, words().skinHint()))
                .hint(words().skinHint());
        answer(player, form.ask(values -> values), back, values -> {
            String name = values.getOr(NAME, "").trim();
            if (createHere(player, name, StoredNpc.Skin.parse(values.getOr(SKIN, "")),
                    config.presets().get(values.getOr(PRESET, "")))) {
                NpcViews.openHub(player, name);
            } else {
                back.run();
            }
        });
    }

    /**
     * Creates an NPC where the player stands, telling them how it went. Opens nothing: a command
     * calls it as it is, a window opens the editor after it.
     *
     * @param skin   what it wears; a preset's own skin wins over none
     * @param preset what it starts from, or {@code null} for a bare NPC with its name above it
     */
    public static boolean createHere(Player player, String name, StoredNpc.Skin skin, NpcsConfig.Preset preset) {
        Values values = Values.of().putText("name", name);
        if (!Names.valid(name)) {
            plugin().getMessages().send(player, messages().invalidName(), values);
            return false;
        }
        if (NpcModule.get().store().exists(name)) {
            plugin().getMessages().send(player, messages().alreadyExists(), values);
            return false;
        }
        NpcsConfig config = NpcsConfig.get();
        Place at = Place.of(player.getLocation());
        at = config.faceCreator() ? at.facingBack(at.yaw()) : new Place(at.world(), at.x(), at.y(), at.z(), at.yaw(), 0);
        StoredNpc npc = new StoredNpc().withLocation(at)
                .withViewDistance(config.defaultViewDistance())
                .withLook(new StoredNpc.Look(config.defaultLook(), new StoredNpc.Look().range()));
        StoredNpc.Skin chosen = skin == null ? new StoredNpc.Skin() : skin;
        if (preset != null) {
            StoredNpc.Skin presetSkin = StoredNpc.Skin.parse(preset.skin());
            if (presetSkin != null && presetSkin.type() != StoredNpc.Skin.Type.NONE) chosen = presetSkin;
            npc = npc.withLabelLines(preset.label().stream().map(line -> line.replace("%npc%", name)).toList())
                    .withInteractions(npc.interactions().withActions(preset.actions()));
            npc = preset.body().applyTo(npc);
        } else {
            npc = npc.withLabelLines(List.of("{primary}&l" + name));
        }
        NpcModule.get().store().create(name, npc.withSkin(chosen));
        plugin().getMessages().send(player, messages().created(), values);
        return true;
    }

    // ------------------------------------------------------------------
    // The NPC
    // ------------------------------------------------------------------

    /**
     * What it looks like, in two windows so each fits on screen: its skin or, with {@code body},
     * its pose, size and outline.
     */
    public static void appearance(Player player, String name, boolean body) {
        StoredNpc npc = NpcModule.get().store().get(name).orElse(null);
        if (npc == null) return;
        NpcsMessages.Dialogs words = words();
        StoredNpc.Skin skin = npc.skin();
        Runnable back = () -> NpcViews.openHub(player, name);
        if (body) {
            EditorForm form = form(player, words.bodyTitle())
                    .choice(POSE, words.poseLabel(), npc.pose().name(), options(NpcPose.values()))
                    .decimal(SCALE, words.scaleLabel(), BigDecimal.valueOf(npc.scale()))
                    .field(GLOW, FormField.text(GLOW, words.glowLabel()).defaultValue(npc.glow()).optional()
                            .validate(glow -> glow.isBlank() || StoredNpc.isColour(glow), "#RRGGBB"))
                    .hint(words.glowHint())
                    .flag(SITTING, messages().animations().sittingLabel(), npc.sitting());
            answer(player, form.ask(values -> values), back, values -> save(player, name, current -> current
                    .withSitting(values.getBoolean(SITTING))
                    .withPose(NpcPose.of(values.getOr(POSE, "STANDING")))
                    .withScale(positive(values.getDecimal(SCALE), current.scale()))
                    .withGlow(values.getOr(GLOW, "").trim()), back));
            return;
        }
        EditorForm form = form(player, words.appearanceTitle())
                .choice(SKIN_TYPE, words.skinTypeLabel(), skin.type().name(), options(StoredNpc.Skin.Type.values()))
                .text(SKIN, words.skinPlayerLabel(), skin.player())
                .text(TEXTURE, words.textureLabel(), skin.texture(), 2)
                .hint(words.textureHint())
                .text(SIGNATURE, words.signatureLabel(), skin.signature(), 2);
        answer(player, form.ask(values -> values), back, values -> save(player, name, current -> current
                .withSkin(new StoredNpc.Skin(StoredNpc.Skin.Type.valueOf(values.getOr(SKIN_TYPE, "NONE")),
                        values.getOr(SKIN, "").trim(), values.getOr(TEXTURE, "").trim(),
                        values.getOr(SIGNATURE, "").trim())), back));
    }

    /** Every label line in one box, and how high it floats. */
    public static void label(Player player, String name) {
        StoredNpc npc = NpcModule.get().store().get(name).orElse(null);
        if (npc == null) return;
        Runnable back = () -> NpcViews.openHub(player, name);
        EditorForm form = form(player, words().labelTitle())
                .text(LINES, words().labelLinesLabel(), String.join("\n", npc.label().lines()), 8)
                .hint(words().labelLinesHint())
                .decimal(HEIGHT, words().heightLabel(), BigDecimal.valueOf(npc.label().heightOffset()));
        answer(player, form.ask(values -> values), back, values -> {
            List<String> lines = lines(values.getOr(LINES, ""));
            NpcModule.get().store().edit(name, current -> current.withLabel(
                    new StoredNpc.Label(lines, values.getDecimal(HEIGHT).doubleValue()))).ifPresent(saved ->
                    plugin().getMessages().send(player, messages().labelSaved(),
                            Values.of().putText("name", name).put("count", saved.label().lines().size())));
            back.run();
        });
    }

    /** Which hologram of holograms.yml follows it. */
    public static void link(Player player, String name) {
        StoredNpc npc = NpcModule.get().store().get(name).orElse(null);
        if (npc == null) return;
        Runnable back = () -> NpcViews.openHub(player, name);
        HologramModule holograms = HologramModule.get();
        if (holograms == null) {
            plugin().getMessages().send(player, messages().hologramsOff(), Values.of());
            back.run();
            return;
        }
        List<FormField.Option> options = new ArrayList<>();
        options.add(new FormField.Option("", words().linkNone()));
        holograms.store().names().forEach(hologram -> options.add(new FormField.Option(hologram, hologram)));
        String current = holograms.store().exists(npc.linkedHologram()) ? npc.linkedHologram() : "";
        EditorForm form = form(player, words().linkTitle()).choice(HOLOGRAM, words().linkLabel(), current, options);
        answer(player, form.ask(values -> values.getOr(HOLOGRAM, "")), back,
                hologram -> save(player, name, saved -> saved.withLinkedHologram(hologram), back));
    }

    /**
     * What clicking it does or, with {@code rules}, who may and how often: two windows, so the
     * tall actions box has one to itself.
     */
    public static void interactions(Player player, String name, boolean rules) {
        StoredNpc npc = NpcModule.get().store().get(name).orElse(null);
        if (npc == null) return;
        StoredNpc.Interactions click = npc.interactions();
        NpcsMessages.Dialogs words = words();
        Runnable back = () -> NpcViews.openHub(player, name);
        if (!rules) {
            EditorForm form = form(player, words.interactionsTitle())
                    .text(ACTIONS, words.actionsLabel(), String.join("\n", click.actions()), 8)
                    .hint(words.actionsHint());
            answer(player, form.ask(values -> lines(values.getOr(ACTIONS, "")).stream().map(String::trim)
                            .filter(line -> !line.isEmpty()).toList()), back,
                    actions -> save(player, name, current -> current.withInteractions(
                            current.interactions().withActions(actions)), back));
            return;
        }
        EditorForm form = form(player, words.rulesTitle())
                .text(USE_PERMISSION, words.usePermissionLabel(), click.permission())
                .hint(words.usePermissionHint())
                .field(COOLDOWN, FormField.text(COOLDOWN, words.cooldownLabel())
                        .defaultValue(NpcViews.number(click.cooldown() / 1000.0) + "s").optional()
                        .validate(value -> cooldown(value) >= 0, "500ms, 2s, 1m"))
                .hint(words.cooldownHint())
                .text(DENY, words.denyLabel(), click.denyMessage())
                .text(WAIT, words.cooldownMessageLabel(), click.cooldownMessage())
                .hint(words.messageHint())
                .flag(FACE, words.faceClickerLabel(), click.faceClicker());
        answer(player, form.ask(values -> values), back, values -> save(player, name, current -> current.withInteractions(
                new StoredNpc.Interactions(current.interactions().actions(),
                        values.getOr(USE_PERMISSION, "").trim(),
                        Math.max(0, cooldown(values.getOr(COOLDOWN, "0"))),
                        values.getOr(DENY, "").trim(),
                        values.getOr(WAIT, "").trim(),
                        values.getBoolean(FACE))), back));
    }

    /**
     * What it plays: on a click or, with {@code auto}, on its own and to greet a player. Two
     * windows, so each fits on screen. Words nobody knows are reported and nothing is saved.
     */
    public static void animations(Player player, String name, boolean auto) {
        StoredNpc npc = NpcModule.get().store().get(name).orElse(null);
        if (npc == null) return;
        NpcsMessages.Animations words = messages().animations();
        NpcAnimations now = npc.animations();
        String hint = Values.of("presets", String.join(", ", NpcAnimation.presets())).apply(words.animationHint());
        Runnable back = () -> NpcViews.openHub(player, name);
        Runnable again = () -> animations(player, name, auto);
        if (!auto) {
            EditorForm form = form(player, words.clickTitle());
            animationBox(form, LEFT_CLICK, words.leftClickLabel(), now.leftClick());
            animationBox(form, RIGHT_CLICK, words.rightClickLabel(), now.rightClick()).hint(hint);
            answer(player, form.ask(values -> values), back, values -> {
                String left = values.getOr(LEFT_CLICK, "").trim();
                String right = values.getOr(RIGHT_CLICK, "").trim();
                if (!readable(player, left, right)) {
                    again.run();
                    return;
                }
                save(player, name, current -> current.withAnimation("left-click", left).withAnimation("right-click", right), back);
            });
            return;
        }
        EditorForm form = form(player, words.autoTitle());
        animationBox(form, IDLE, words.idleLabel(), now.idle());
        form.field(IDLE_EVERY, time(IDLE_EVERY, words.idleEveryLabel(), now.idleEvery()));
        animationBox(form, GREET, words.greetLabel(), now.greet()).hint(hint)
                .decimal(GREET_RANGE, words.greetRangeLabel(), BigDecimal.valueOf(now.greetRange()))
                .field(GREET_COOLDOWN, time(GREET_COOLDOWN, words.greetCooldownLabel(), now.greetCooldown()))
                .hint(words.timeHint());
        answer(player, form.ask(values -> values), back, values -> {
            String idle = values.getOr(IDLE, "").trim();
            String greet = values.getOr(GREET, "").trim();
            if (!readable(player, idle, greet)) {
                again.run();
                return;
            }
            save(player, name, current -> {
                NpcAnimations a = current.animations();
                return current.withAnimations(new NpcAnimations(a.leftClick(), a.rightClick(), idle,
                        java.time.Duration.ofMillis(Math.max(0, cooldown(values.getOr(IDLE_EVERY, "0")))), greet,
                        Math.max(0, values.getDecimal(GREET_RANGE).doubleValue()),
                        java.time.Duration.ofMillis(Math.max(0, cooldown(values.getOr(GREET_COOLDOWN, "0"))))));
            }, back);
        });
    }

    private static EditorForm animationBox(EditorForm form, FormKey<String> key, String label, String current) {
        return form.field(key, FormField.text(key, label).defaultValue(current).optional());
    }

    private static FormField<String> time(FormKey<String> key, String label, java.time.Duration current) {
        return FormField.text(key, label).defaultValue(NpcViews.seconds(current)).optional()
                .validate(value -> cooldown(value) >= 0, "500ms, 20s, 1m");
    }

    /** The words of an animation nobody knows; empty when every word reads. */
    static List<String> unknownWords(String text) {
        List<String> unknown = new ArrayList<>();
        NpcAnimation.parse(text, unknown::add);
        return unknown;
    }

    /** Whether every word of an animation reads, telling the sender the ones that do not. */
    static boolean readable(CommandSender sender, String... texts) {
        List<String> unknown = new ArrayList<>();
        for (String text : texts) unknown.addAll(unknownWords(text));
        if (unknown.isEmpty()) return true;
        plugin().getMessages().send(sender, messages().animations().unknownWords(), Values.of()
                .putText("words", String.join(", ", unknown))
                .putText("presets", String.join(", ", NpcAnimation.presets())));
        return false;
    }

    /** Puts the NPC back as it was before its last change, telling the sender how it went. */
    public static boolean undo(CommandSender sender, String name) {
        var store = NpcModule.get().store();
        if (!store.exists(name)) {
            plugin().getMessages().send(sender, messages().notFound(), Values.of().putText("name", name));
            return false;
        }
        boolean undone = store.undo(name).isPresent();
        plugin().getMessages().send(sender, undone ? messages().undone() : messages().nothingToUndo(),
                Values.of().putText("name", name).put("left", store.undoable(name)));
        return undone;
    }

    /** Where its head turns, and who sees it from how far. */
    public static void visibility(Player player, String name) {
        StoredNpc npc = NpcModule.get().store().get(name).orElse(null);
        if (npc == null) return;
        NpcsMessages.Dialogs words = words();
        Runnable back = () -> NpcViews.openHub(player, name);
        EditorForm form = form(player, words.visibilityTitle())
                .choice(LOOK, words.lookLabel(), npc.look().mode().name(), options(NpcLook.values()))
                .decimal(LOOK_RANGE, words.lookRangeLabel(), BigDecimal.valueOf(npc.look().range()))
                .decimal(VIEW_DISTANCE, words.viewDistanceLabel(), BigDecimal.valueOf(npc.viewDistance()))
                .text(PERMISSION, words.permissionLabel(), npc.permission())
                .hint(words.permissionHint());
        answer(player, form.ask(values -> values), back, values -> save(player, name, current -> current
                .withLook(new StoredNpc.Look(NpcLook.valueOf(values.getOr(LOOK, current.look().mode().name())),
                        Math.max(0, values.getDecimal(LOOK_RANGE).doubleValue())))
                .withViewDistance(positive(values.getDecimal(VIEW_DISTANCE), current.viewDistance()))
                .withPermission(values.getOr(PERMISSION, "").trim()), back));
    }

    /** The exact place and facing, for a nudge a step at a time. */
    public static void location(Player player, String name) {
        StoredNpc npc = NpcModule.get().store().get(name).orElse(null);
        if (npc == null) return;
        Place at = npc.location();
        Runnable back = () -> NpcViews.openHub(player, name);
        List<FormField.Option> worlds = new ArrayList<>();
        worlds.add(new FormField.Option(at.world(), at.world()));
        for (World world : Bukkit.getWorlds()) {
            if (!world.getName().equals(at.world())) worlds.add(new FormField.Option(world.getName(), world.getName()));
        }
        EditorForm form = form(player, words().locationTitle())
                .choice(WORLD, words().worldLabel(), at.world(), worlds)
                .decimal(X, "X", BigDecimal.valueOf(at.x()))
                .decimal(Y, "Y", BigDecimal.valueOf(at.y()))
                .decimal(Z, "Z", BigDecimal.valueOf(at.z()))
                .decimal(YAW, words().yawLabel(), BigDecimal.valueOf(at.yaw()))
                .decimal(PITCH, words().pitchLabel(), BigDecimal.valueOf(at.pitch()));
        answer(player, form.ask(values -> new Place(values.getOr(WORLD, at.world()),
                        values.getDecimal(X).doubleValue(), values.getDecimal(Y).doubleValue(),
                        values.getDecimal(Z).doubleValue(), values.getDecimal(YAW).doubleValue(),
                        values.getDecimal(PITCH).doubleValue())), back,
                place -> save(player, name, current -> current.withLocation(place), back));
    }

    /** Turns it, where it stands, to face the player. */
    public static void faceMe(CommandSender sender, Player player, String name) {
        StoredNpc saved = NpcModule.get().store().edit(name, current -> current.withLocation(
                current.location().facing(player.getLocation().getX(), player.getLocation().getZ()))).orElse(null);
        plugin().getMessages().send(sender, saved == null ? messages().notFound() : messages().faced(),
                Values.of().putText("name", name));
    }

    // ------------------------------------------------------------------
    // Equipment
    // ------------------------------------------------------------------

    /**
     * Puts what the player holds into a slot; an empty hand empties the slot.
     *
     * <p>Stored as ExyliaLib writes an icon: a material when that is all the item is, the whole
     * item otherwise, so an enchanted or dyed piece keeps its look.
     */
    public static void equipFromHand(CommandSender sender, Player player, String name, EquipmentSlot slot) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        String icon = hand.getType().isAir() ? "" : Source.of(hand).raw();
        StoredNpc saved = NpcModule.get().store().edit(name, current -> current.equip(slot, icon)).orElse(null);
        Values values = Values.of().putText("name", name).put("slot", StoredNpc.slotName(slot));
        if (saved == null) {
            plugin().getMessages().send(sender, messages().notFound(), values);
        } else {
            plugin().getMessages().send(sender, icon.isEmpty() ? messages().unequipped() : messages().equipped(), values);
        }
    }

    public static void unequip(CommandSender sender, String name, EquipmentSlot slot) {
        StoredNpc saved = NpcModule.get().store().edit(name, current -> current.equip(slot, "")).orElse(null);
        plugin().getMessages().send(sender, saved == null ? messages().notFound() : messages().unequipped(),
                Values.of().putText("name", name).put("slot", StoredNpc.slotName(slot)));
    }

    // ------------------------------------------------------------------
    // Renaming, copying, deleting
    // ------------------------------------------------------------------

    public static void rename(Player player, String name) {
        Runnable back = () -> NpcViews.openHub(player, name);
        plugin().getInputs().answered(player,
                plugin().getInputs().text(player, words().renameTitle()).defaultValue(name).maxLength(32).open(),
                typed -> {
                    String renamed = typed.trim();
                    if (rename(player, name, renamed)) NpcViews.openHub(player, renamed);
                    else back.run();
                },
                back);
    }

    /** Renames, telling the sender how it went. */
    public static boolean rename(CommandSender sender, String name, String renamed) {
        Values values = Values.of().putText("name", name).putText("new", renamed);
        if (!NpcModule.get().store().exists(name)) {
            plugin().getMessages().send(sender, messages().notFound(), values);
            return false;
        }
        if (!Names.valid(renamed)) {
            plugin().getMessages().send(sender, messages().invalidName(), values);
            return false;
        }
        if (!NpcModule.get().store().rename(name, renamed)) {
            plugin().getMessages().send(sender, messages().alreadyExists(), Values.of().putText("name", renamed));
            return false;
        }
        plugin().getMessages().send(sender, messages().renamed(), values);
        return true;
    }

    public static void cloneNpc(Player player, String name) {
        Runnable back = () -> NpcViews.openHub(player, name);
        plugin().getInputs().answered(player,
                plugin().getInputs().text(player, words().cloneTitle())
                        .defaultValue(NpcModule.get().store().freeName(name)).maxLength(32).open(),
                typed -> {
                    String copy = typed.trim();
                    if (copy(player, name, copy)) NpcViews.openHub(player, copy);
                    else back.run();
                },
                back);
    }

    /** Copies, telling the sender how it went. The copy stands in the same place. */
    public static boolean copy(CommandSender sender, String name, String copy) {
        Values values = Values.of().putText("name", name).putText("new", copy);
        Optional<StoredNpc> original = NpcModule.get().store().get(name);
        if (original.isEmpty()) {
            plugin().getMessages().send(sender, messages().notFound(), values);
            return false;
        }
        if (!Names.valid(copy)) {
            plugin().getMessages().send(sender, messages().invalidName(), values);
            return false;
        }
        // Two NPCs cannot carry one hologram: both would pull it to themselves.
        if (!NpcModule.get().store().create(copy, original.get().withLinkedHologram(""))) {
            plugin().getMessages().send(sender, messages().alreadyExists(), Values.of().putText("name", copy));
            return false;
        }
        plugin().getMessages().send(sender, messages().cloned(), values);
        return true;
    }

    /** Asks before deleting, then shows the list. */
    public static void delete(Player player, String name, Runnable back) {
        plugin().getInputs().answered(player,
                plugin().getInputs().confirm(player,
                        Values.of().putText("name", name).apply(words().confirmDelete())).dangerous().open(),
                confirmed -> {
                    if (confirmed && NpcModule.get().store().delete(name)) {
                        plugin().getMessages().send(player, messages().deleted(), Values.of().putText("name", name));
                        NpcViews.openList(player);
                        return;
                    }
                    back.run();
                },
                back);
    }

    // ------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------

    /** Saves a change and brings the screen back with it. */
    static void save(Player player, String name, UnaryOperator<StoredNpc> change, Runnable back) {
        NpcModule.get().store().edit(name, change).ifPresentOrElse(
                saved -> plugin().getMessages().send(player, messages().saved(), Values.of().putText("name", name)),
                () -> plugin().getMessages().send(player, messages().notFound(), Values.of().putText("name", name)));
        back.run();
    }

    /**
     * Waits for a window's answer on the player's own thread: the answer, or the screen they came
     * from when they closed it.
     */
    private static <T> void answer(Player player, CompletionStage<Optional<T>> asked, Runnable back, Consumer<T> then) {
        player.closeInventory();
        asked.whenComplete((answer, failure) -> plugin().getTasks().runAtEntity(player, () -> {
            if (failure != null) {
                plugin().getDebug().error("An NPC window failed", failure);
                back.run();
            } else if (answer.isPresent()) {
                then.accept(answer.get());
            } else {
                back.run();
            }
        }));
    }

    /** A box's rows, without the blank ones a trailing newline leaves. */
    static List<String> lines(String text) {
        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\\R", -1)));
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    /** A cooldown as typed, {@code 2s}, {@code 500ms} or {@code 1.5}, in milliseconds; -1 when unreadable. */
    static long cooldown(String typed) {
        double seconds = Ticks.parseSeconds(typed == null ? "" : typed.trim(), Double.NaN);
        return Double.isNaN(seconds) || seconds < 0 ? -1 : Math.round(seconds * 1000);
    }

    private static List<FormField.Option> options(Enum<?>... values) {
        return Arrays.stream(values).map(value -> new FormField.Option(value.name(), value.name())).toList();
    }

    private static double positive(BigDecimal value, double fallback) {
        double number = value.doubleValue();
        return number > 0 ? number : fallback;
    }

    /** A message as plain text, for a validation error under a box. */
    private static String plain(String message) {
        return plugin().getMessages().plain(message.replace("%prefix%", ""), Values.of());
    }
}
