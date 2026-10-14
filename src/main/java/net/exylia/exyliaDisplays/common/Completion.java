package net.exylia.exyliaDisplays.common;

import net.exylia.exyliaDisplays.common.store.Position;
import net.exylia.lib.format.Numbers;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import revxrsal.commands.autocomplete.SuggestionProvider;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.node.ExecutionContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tab completion that reads what is typed so far, so an argument can be completed from the
 * entry an earlier argument names: its line numbers, a line's current text, a setting's
 * current value.
 *
 * <p>The words come from the raw input, not from Lamp's resolved arguments: on Paper the
 * Brigadier bridge clears those as soon as one argument fails to parse, which an argument still
 * being typed always does. What is offered is filtered by what is typed, since Brigadier sends
 * every suggestion to the client unfiltered. Runs on every keystroke: read memory only.
 */
public interface Completion extends SuggestionProvider<BukkitCommandActor> {

    /**
     * What to offer for the argument being typed.
     *
     * @param words  every word typed so far, the command's label first and the word being typed last
     * @param player who is typing, or {@code null} for the console
     */
    Collection<String> suggest(List<String> words, @Nullable Player player);

    @Override
    default @NotNull Collection<String> getSuggestions(@NotNull ExecutionContext<BukkitCommandActor> context) {
        List<String> words = words(context.input().source());
        return matching(words, suggest(words, context.actor().asPlayer()));
    }

    /** The words of a command line, the last one empty when it ends with a space. */
    static List<String> words(String input) {
        String line = input.startsWith("/") ? input.substring(1) : input;
        return Arrays.asList(line.split(" ", -1));
    }

    /** The word at {@code index}, or empty. */
    static String word(List<String> words, int index) {
        return index >= 0 && index < words.size() ? words.get(index) : "";
    }

    /** A whole number, or {@code -1} when the word is not one. */
    static int number(String word) {
        try {
            return Integer.parseInt(word.trim());
        } catch (NumberFormatException typo) {
            return -1;
        }
    }

    /** {@code from} to {@code to}, both included, as text. */
    static List<String> numbers(int from, int to) {
        List<String> numbers = new ArrayList<>();
        for (int i = from; i <= to; i++) numbers.add(String.valueOf(i));
        return numbers;
    }

    /** Where the player stands, facing them as a placed display would; {@code null} for the console. */
    static @Nullable Position here(@Nullable Player player) {
        return player == null ? null : Position.facing(player.getLocation());
    }

    /**
     * The suggestions that continue what is typed, without blanks or repeats. A suggestion of
     * several words (a line's text, three coordinates) also matches when the words typed so far
     * begin it, so it still shows while the player types into it.
     */
    static List<String> matching(List<String> words, Collection<String> suggestions) {
        Set<String> kept = new LinkedHashSet<>();
        for (String suggestion : suggestions) {
            if (suggestion == null || suggestion.isBlank()) continue;
            String lower = suggestion.toLowerCase(Locale.ROOT);
            int spaces = (int) suggestion.chars().filter(c -> c == ' ').count();
            for (int back = 0; back <= spaces && back < words.size() - 1; back++) {
                String typed = String.join(" ", words.subList(words.size() - 1 - back, words.size()));
                if (lower.startsWith(typed.toLowerCase(Locale.ROOT))) {
                    kept.add(suggestion);
                    break;
                }
            }
        }
        return new ArrayList<>(kept);
    }

    /** Where it stands on one axis, and where the player does; the first axis also offers all three. */
    static List<String> move(Position at, @Nullable Position here, int axis) {
        List<String> values = new ArrayList<>();
        switch (axis) {
            case 0 -> {
                values.add(Completion.decimal(at.x()));
                values.add(Completion.decimal(at.x()) + " " + Completion.decimal(at.y()) + " " + Completion.decimal(at.z()));
                if (here != null) {
                    values.add(Completion.rounded(here.x()));
                    values.add(Completion.rounded(here.x()) + " " + Completion.rounded(here.y()) + " " + Completion.rounded(here.z()));
                }
            }
            case 1 -> {
                values.add(Completion.decimal(at.y()));
                if (here != null) values.add(Completion.rounded(here.y()));
            }
            case 2 -> {
                values.add(Completion.decimal(at.z()));
                if (here != null) values.add(Completion.rounded(here.z()));
            }
            case 3 -> {
                values.add(at.world());
                if (here != null) values.add(here.world());
            }
            default -> {
            }
        }
        return values;
    }

    /** A number as a player types it: at most two decimals, no trailing zeros. */
    static String decimal(double value) {
        return Numbers.trimmed(value, 2);
    }

    /** A number to one decimal, for where a player stands. */
    static String rounded(double value) {
        return Numbers.trimmed(Math.round(value * 10) / 10.0, 1);
    }
}
