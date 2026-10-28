package net.exylia.exyliaDisplays.npc;

import net.exylia.exyliaDisplays.common.Completion;
import net.exylia.exyliaDisplays.common.store.Place;
import net.exylia.exyliaDisplays.common.store.Position;
import net.exylia.lib.interaction.Interactions;
import net.exylia.lib.npc.NpcAnimation;
import net.exylia.lib.npc.NpcAnimations;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import static net.exylia.exyliaDisplays.common.Completion.word;

/**
 * Completes every argument of {@code /npc <name> ...} from the NPC the name points at: each
 * setting's current value next to the values it takes, its label lines and its actions.
 */
public final class NpcSuggestions implements Completion {

    @Override
    public Collection<String> suggest(List<String> words, @Nullable Player player) {
        NpcModule module = NpcModule.get();
        StoredNpc npc = module == null ? null : module.store().get(word(words, 1)).orElse(null);
        if (npc == null) return List.of();
        List<String> online = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return suggest(npc, words, Completion.here(player), online);
    }

    /**
     * What to offer after {@code /npc <name>}, read from the NPC alone.
     *
     * @param words  every word typed, the label first and the word being typed last
     * @param here   where the player stands, facing them, or {@code null} for the console
     * @param online the names of the players online
     */
    static List<String> suggest(StoredNpc npc, List<String> words, @Nullable Position here, Collection<String> online) {
        String command = word(words, 2).toLowerCase(Locale.ROOT);
        List<String> args = words.subList(Math.min(3, words.size()), words.size());
        int index = args.size() - 1;
        if (index < 0) return List.of();
        Place at = npc.location();
        List<String> values = new ArrayList<>();
        switch (command) {
            case "rename" -> values.add(word(words, 1));
            case "clone" -> values.add(word(words, 1) + "-copy");
            case "move" -> {
                Position where = new Position(at.world(), at.x(), at.y(), at.z(), (float) at.yaw(), (float) at.pitch());
                values.addAll(Completion.move(where, here, index));
            }
            case "rotate" -> {
                if (index == 0) values.addAll(List.of(number(at.yaw()), "0", "90", "180", "-90"));
                if (index == 1) values.addAll(List.of(number(at.pitch()), "0"));
            }
            case "skin" -> {
                values.add(skin(npc.skin()));
                values.addAll(List.of("@mirror", "@none"));
                values.addAll(online);
            }
            case "scale" -> values.addAll(List.of(number(npc.scale()), "1"));
            case "glow" -> values.addAll(List.of(npc.glow(), "none"));
            case "equip" -> {
                if (index == 0) {
                    values.addAll(StoredNpc.SLOTS);
                    values.add("clear");
                } else {
                    values.add(NpcViews.worn(npc, args.get(0).toLowerCase(Locale.ROOT)));
                    values.add("air");
                }
            }
            case "look" -> {
                if (index == 0) {
                    values.add(npc.look().mode().name().toLowerCase(Locale.ROOT).replace('_', '-'));
                    values.addAll(List.of("fixed", "nearest", "each-viewer"));
                } else if (index == 1) {
                    values.add(number(npc.look().range()));
                }
            }
            case "viewdistance" -> values.add(number(npc.viewDistance()));
            case "permission" -> values.add(npc.permission());
            case "label" -> {
                List<String> lines = npc.label().lines();
                switch (index == 0 ? "" : args.get(0).toLowerCase(Locale.ROOT)) {
                    case "set" -> values.addAll(lines);
                    case "remove" -> values.addAll(Completion.numbers(1, lines.size()));
                    case "height" -> values.add(number(npc.label().heightOffset()));
                    default -> {
                    }
                }
            }
            case "action" -> {
                StoredNpc.Interactions click = npc.interactions();
                switch (index == 0 ? "" : args.get(0).toLowerCase(Locale.ROOT)) {
                    case "add" -> {
                        if (index == 1) values.addAll(Interactions.starters());
                    }
                    case "set" -> {
                        if (index == 1) values.addAll(Completion.numbers(1, click.actions().size()));
                        if (index == 2) {
                            int action = Completion.number(args.get(1)) - 1;
                            if (npc.hasAction(action)) values.add(click.actions().get(action));
                            values.addAll(Interactions.starters());
                        }
                    }
                    case "remove" -> values.addAll(Completion.numbers(1, click.actions().size()));
                    case "permission" -> values.add(click.permission());
                    case "cooldown" -> values.addAll(List.of(seconds(Duration.ofMillis(click.cooldown())), "0"));
                    case "denymessage" -> values.add(click.denyMessage());
                    case "cooldownmessage" -> values.add(click.cooldownMessage());
                    default -> {
                    }
                }
            }
            case "animation" -> {
                if (index == 0) {
                    values.addAll(StoredNpc.TRIGGERS);
                    values.addAll(List.of("idle-every", "greet-range", "greet-cooldown", "perform-range"));
                } else {
                    values.addAll(animation(npc.animations(), args.get(0).toLowerCase(Locale.ROOT)));
                }
            }
            case "play" -> {
                NpcAnimations a = npc.animations();
                values.addAll(List.of(a.leftClick(), a.rightClick(), a.idle(), a.greet()));
                values.addAll(NpcAnimation.presets());
                values.addAll(NpcPerformances.names());
            }
            default -> {
            }
        }
        return values;
    }

    /** {@code /npc <name> animation <trigger>}: what it plays now first, then the presets and the performances. */
    static List<String> animation(NpcAnimations a, String trigger) {
        return switch (trigger) {
            case "left-click", "left" -> presets(a.leftClick());
            case "right-click", "right" -> presets(a.rightClick());
            case "idle" -> presets(a.idle());
            case "greet" -> presets(a.greet());
            case "idle-every" -> List.of(seconds(a.idleEvery()));
            case "greet-cooldown" -> List.of(seconds(a.greetCooldown()));
            case "greet-range" -> List.of(number(a.greetRange()));
            case "perform-range" -> List.of(number(a.performRange()));
            default -> List.of();
        };
    }

    private static List<String> presets(String current) {
        List<String> values = new ArrayList<>();
        values.add(current);
        values.addAll(NpcAnimation.presets());
        values.addAll(NpcPerformances.names());
        values.add("none");
        return values;
    }

    /** The skin as {@code /npc <name> skin} takes it; a texture cannot be typed, so it has none. */
    private static String skin(StoredNpc.Skin skin) {
        return switch (skin.type()) {
            case PLAYER -> skin.player();
            case MIRROR -> "@mirror";
            case NONE -> "@none";
            case TEXTURE -> "";
        };
    }

    private static String seconds(Duration time) {
        return time == null ? "" : NpcViews.seconds(time);
    }

    private static String number(double value) {
        return NpcViews.number(value);
    }
}
