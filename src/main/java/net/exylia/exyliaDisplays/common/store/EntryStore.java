package net.exylia.exyliaDisplays.common.store;

import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.config.ConfigIssue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * The one door every change to a module's file goes through.
 *
 * <p>A command, a button and a hand edit of the file all end here: the entry is changed, the file
 * is written, and whoever listens is told, which is how what stands in the world follows. Nothing
 * else writes the file, so the three ways of editing can never disagree. Every module that keeps
 * things by name ({@code holograms.yml}, {@code npcs.yml}...) is one of these over its own file.
 *
 * <p>Changes are synchronised: two admins editing the same entry from two screens each apply to
 * the latest version, never to the one their screen was opened with.
 *
 * @param <F> the file's record
 * @param <T> one entry
 */
public final class EntryStore<F extends Record, T> {

    private final ConfigFile<F> file;
    private final Function<F, Map<String, T>> read;
    private final Function<Map<String, T>, F> write;
    private final BiConsumer<String, T> changed;
    private volatile Runnable written = () -> {};

    /** How many changes of one entry {@link #undo} can take back. */
    public static final int HISTORY = 10;

    /** What each entry was before its last changes, newest first; guarded by {@code this}. */
    private final Map<String, Deque<T>> history = new HashMap<>();

    /**
     * @param file    the file
     * @param read    the entries a snapshot of the file holds
     * @param write   a snapshot of the file holding these entries
     * @param changed told the name and the new entry after every change; {@code null} once removed
     */
    public EntryStore(ConfigFile<F> file, Function<F, Map<String, T>> read,
                      Function<Map<String, T>, F> write, BiConsumer<String, T> changed) {
        this.file = file;
        this.read = read;
        this.write = write;
        this.changed = changed;
    }

    public Optional<T> get(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(all().get(name));
    }

    public boolean exists(String name) {
        return get(name).isPresent();
    }

    /** Every entry, in file order. */
    public Map<String, T> all() {
        return read.apply(file.get());
    }

    public List<String> names() {
        return new ArrayList<>(all().keySet());
    }

    /** A free name based on another: {@code spawn}, then {@code spawn_2}, {@code spawn_3}... */
    public String freeName(String base) {
        String name = Names.sanitize(base);
        if (!exists(name)) {
            return name;
        }
        for (int i = 2; ; i++) {
            String suffix = "_" + i;
            String candidate = (name.length() + suffix.length() > 32
                    ? name.substring(0, 32 - suffix.length()) : name) + suffix;
            if (!exists(candidate)) {
                return candidate;
            }
        }
    }

    /** @return {@code false} when the name is taken or not valid */
    public synchronized boolean create(String name, T entry) {
        if (!Names.valid(name) || exists(name)) {
            return false;
        }
        save(entries -> entries.put(name, entry));
        changed.accept(name, entry);
        return true;
    }

    /** @return the entry after the change, or empty when there is none by that name */
    public synchronized Optional<T> edit(String name, UnaryOperator<T> change) {
        T current = all().get(name);
        if (current == null) {
            return Optional.empty();
        }
        T edited = change.apply(current);
        if (edited.equals(current)) {
            return Optional.of(current);
        }
        save(entries -> entries.put(name, edited));
        remember(name, current);
        changed.accept(name, edited);
        return Optional.of(edited);
    }

    /**
     * Puts an entry back as it was before its last change.
     *
     * @return the entry as it is now, or empty when it has nothing to undo
     */
    public synchronized Optional<T> undo(String name) {
        Deque<T> steps = history.get(name);
        if (steps == null || steps.isEmpty() || !exists(name)) {
            return Optional.empty();
        }
        T before = steps.pop();
        save(entries -> entries.put(name, before));
        changed.accept(name, before);
        return Optional.of(before);
    }

    /** How many changes of an entry {@link #undo} can still take back. */
    public synchronized int undoable(String name) {
        Deque<T> steps = history.get(name);
        return steps == null ? 0 : steps.size();
    }

    public synchronized boolean delete(String name) {
        if (!exists(name)) {
            return false;
        }
        save(entries -> entries.remove(name));
        history.remove(name);
        changed.accept(name, null);
        return true;
    }

    /** Gives an entry another name, keeping its place in the file. */
    public synchronized boolean rename(String from, String to) {
        T entry = all().get(from);
        if (entry == null || !Names.valid(to) || exists(to)) {
            return false;
        }
        Map<String, T> renamed = new LinkedHashMap<>();
        all().forEach((name, value) -> renamed.put(name.equals(from) ? to : name, value));
        write(renamed);
        Deque<T> steps = history.remove(from);
        if (steps != null) history.put(to, steps);
        changed.accept(from, null);
        changed.accept(to, entry);
        return true;
    }

    /** Adds many at once, for an import; a name already taken gets a free one. */
    public synchronized int addAll(Map<String, T> entries) {
        return addAll(entries, false);
    }

    /**
     * Adds many at once, for an import.
     *
     * @param replace whether an entry already under that name is replaced, so running the same
     *                import again corrects it instead of adding a copy next to it
     */
    public synchronized int addAll(Map<String, T> entries, boolean replace) {
        return importAll(entries, replace).size();
    }

    /**
     * {@link #addAll(Map, boolean)}, telling under which name each entry was saved.
     *
     * @return the name each entry was saved under, by the name it came with
     */
    public synchronized Map<String, String> importAll(Map<String, T> entries, boolean replace) {
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, T> added = new LinkedHashMap<>();
        Map<String, T> merged = new LinkedHashMap<>(all());
        entries.forEach((name, entry) -> {
            String free = Names.sanitize(name);
            for (int i = 2; !replace && merged.containsKey(free); i++) {
                free = Names.sanitize(name) + "_" + i;
            }
            T replaced = merged.put(free, entry);
            if (replaced != null && !replaced.equals(entry)) remember(free, replaced);
            added.put(free, entry);
            names.put(name, free);
        });
        if (added.isEmpty()) {
            return names;
        }
        write(merged);
        added.forEach(changed);
        return names;
    }

    /** Reads the file again, after somebody edited it; what the edit changed can be undone too. */
    public synchronized List<ConfigIssue> reload() {
        Map<String, T> before = all();
        List<ConfigIssue> issues = file.reload();
        Map<String, T> after = all();
        before.forEach((name, old) -> {
            T now = after.get(name);
            if (now == null) {
                history.remove(name);
            } else if (!now.equals(old)) {
                remember(name, old);
            }
        });
        return issues;
    }

    /**
     * Runs after every write of the file is on disk, off the main thread, so a watcher can take
     * this plugin's own writes as already seen.
     */
    public void onWritten(Runnable listener) {
        written = listener;
    }

    private void remember(String name, T before) {
        Deque<T> steps = history.computeIfAbsent(name, key -> new ArrayDeque<>());
        steps.push(before);
        if (steps.size() > HISTORY) {
            steps.removeLast();
        }
    }

    private void save(Consumer<Map<String, T>> change) {
        Map<String, T> edited = new LinkedHashMap<>(all());
        change.accept(edited);
        write(edited);
    }

    /**
     * Publishes the entries at once and writes the file off the main thread: a burst of edits from
     * a screen is one write, and the server never waits on the disk.
     */
    private void write(Map<String, T> entries) {
        file.updateAsync(current -> write.apply(entries)).thenRun(() -> written.run());
    }
}
