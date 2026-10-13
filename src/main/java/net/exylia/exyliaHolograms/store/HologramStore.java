package net.exylia.exyliaHolograms.store;

import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.config.ConfigIssue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * The one door every change goes through.
 *
 * <p>A command, a button and a hand edit of {@code holograms.yml} all end here: the record is
 * changed, the file is written, and whoever listens is told, which is how the hologram in the
 * world follows. Nothing else writes the file, so the three ways of editing can never disagree.
 *
 * <p>Changes are synchronised: two admins editing the same hologram from two screens each apply
 * to the latest version, never to the one their screen was opened with.
 */
public final class HologramStore {

    /** What a hologram may be called: what a command can type and a file key can hold. */
    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    private final ConfigFile<HologramFile> file;
    private final BiConsumer<String, StoredHologram> changed;
    private volatile long lastWrite;

    /**
     * @param file    {@code holograms.yml}
     * @param changed told the name and the new hologram after every change; {@code null} when it
     *                was removed
     */
    public HologramStore(ConfigFile<HologramFile> file, BiConsumer<String, StoredHologram> changed) {
        this.file = file;
        this.changed = changed;
    }

    /** Whether a name can be used for a hologram. */
    public static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    /** A name turned into one that can be used, for an import: lowercase, no spaces or symbols. */
    public static String sanitize(String name) {
        String cleaned = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "_");
        cleaned = cleaned.replaceAll("^_+|_+$", "");
        if (cleaned.isEmpty()) {
            cleaned = "hologram";
        }
        return cleaned.length() > 32 ? cleaned.substring(0, 32) : cleaned;
    }

    public Optional<StoredHologram> get(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(file.get().holograms().get(name));
    }

    public boolean exists(String name) {
        return get(name).isPresent();
    }

    /** Every hologram, in file order. */
    public Map<String, StoredHologram> all() {
        return file.get().holograms();
    }

    public List<String> names() {
        return new ArrayList<>(all().keySet());
    }

    /** A free name based on another: {@code spawn}, then {@code spawn_2}, {@code spawn_3}... */
    public String freeName(String base) {
        String name = sanitize(base);
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

    /**
     * Adds a hologram.
     *
     * @return {@code false} when the name is taken or not valid
     */
    public synchronized boolean create(String name, StoredHologram hologram) {
        if (!validName(name) || exists(name)) {
            return false;
        }
        write(holograms -> holograms.put(name, hologram));
        changed.accept(name, hologram);
        return true;
    }

    /**
     * Changes a hologram as it is saved now.
     *
     * @return the hologram after the change, or empty when there is none by that name
     */
    public synchronized Optional<StoredHologram> edit(String name, UnaryOperator<StoredHologram> change) {
        StoredHologram current = all().get(name);
        if (current == null) {
            return Optional.empty();
        }
        StoredHologram edited = change.apply(current);
        if (edited.equals(current)) {
            return Optional.of(current);
        }
        write(holograms -> holograms.put(name, edited));
        changed.accept(name, edited);
        return Optional.of(edited);
    }

    public synchronized boolean delete(String name) {
        if (!exists(name)) {
            return false;
        }
        write(holograms -> holograms.remove(name));
        changed.accept(name, null);
        return true;
    }

    /** Gives a hologram another name, keeping its place in the file. */
    public synchronized boolean rename(String from, String to) {
        StoredHologram hologram = all().get(from);
        if (hologram == null || !validName(to) || exists(to)) {
            return false;
        }
        Map<String, StoredHologram> renamed = new LinkedHashMap<>();
        all().forEach((name, value) -> renamed.put(name.equals(from) ? to : name, value));
        lastWrite = System.currentTimeMillis();
        file.update(current -> new HologramFile(renamed));
        changed.accept(from, null);
        changed.accept(to, hologram);
        return true;
    }

    /** Adds many at once, for an import; a name already taken gets a free one. */
    public synchronized int addAll(Map<String, StoredHologram> holograms) {
        Map<String, StoredHologram> added = new LinkedHashMap<>();
        Map<String, StoredHologram> merged = new LinkedHashMap<>(all());
        holograms.forEach((name, hologram) -> {
            String free = sanitize(name);
            for (int i = 2; merged.containsKey(free); i++) {
                free = sanitize(name) + "_" + i;
            }
            merged.put(free, hologram);
            added.put(free, hologram);
        });
        if (added.isEmpty()) {
            return 0;
        }
        lastWrite = System.currentTimeMillis();
        file.update(current -> new HologramFile(merged));
        added.forEach(changed);
        return added.size();
    }

    /** Reads the file again, after somebody edited it. */
    public synchronized List<ConfigIssue> reload() {
        return file.reload();
    }

    /** When this plugin last wrote the file, so a watcher can tell its own writes apart. */
    public long lastWrite() {
        return lastWrite;
    }

    private void write(java.util.function.Consumer<Map<String, StoredHologram>> change) {
        Map<String, StoredHologram> edited = new LinkedHashMap<>(all());
        change.accept(edited);
        lastWrite = System.currentTimeMillis();
        file.update(current -> new HologramFile(edited));
    }
}
