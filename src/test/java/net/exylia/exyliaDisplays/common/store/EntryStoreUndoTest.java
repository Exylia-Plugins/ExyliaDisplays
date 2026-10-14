package net.exylia.exyliaDisplays.common.store;

import net.exylia.exyliaDisplays.hologram.HologramFile;
import net.exylia.exyliaDisplays.hologram.StoredHologram;
import net.exylia.lib.config.Configs;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The undo history every change through the store leaves behind. */
class EntryStoreUndoTest {

    @TempDir
    Path folder;

    @AfterEach
    void release() {
        Configs.releaseAll();
    }

    private EntryStore<HologramFile, StoredHologram> store() {
        Plugin plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "EntryStoreUndoTest";
                    case "getDataFolder" -> folder.toFile();
                    case "getLogger" -> Logger.getLogger("EntryStoreUndoTest");
                    case "isEnabled" -> false;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
        return new EntryStore<>(Configs.define(plugin, "holograms", HologramFile.class).load(),
                HologramFile::holograms, HologramFile::new, (name, hologram) -> { });
    }

    private static StoredHologram reading(String line) {
        return new StoredHologram().withLines(List.of(line));
    }

    @Test
    @DisplayName("undo walks back one change at a time, and then has nothing left")
    void undoStepByStep() {
        EntryStore<HologramFile, StoredHologram> store = store();
        store.create("shop", reading("a"));
        assertTrue(store.undo("shop").isEmpty(), "creating is not a change to undo");
        store.edit("shop", current -> reading("b"));
        store.edit("shop", current -> reading("c"));
        store.edit("shop", current -> current);
        assertEquals(2, store.undoable("shop"), "an edit that changes nothing is not remembered");

        assertEquals(reading("b"), store.undo("shop").orElseThrow());
        assertEquals(reading("a"), store.undo("shop").orElseThrow());
        assertEquals(reading("a"), store.get("shop").orElseThrow());
        assertTrue(store.undo("shop").isEmpty());
    }

    @Test
    @DisplayName("only the last changes are kept")
    void bounded() {
        EntryStore<HologramFile, StoredHologram> store = store();
        store.create("shop", reading("0"));
        for (int i = 1; i <= EntryStore.HISTORY + 5; i++) {
            String line = String.valueOf(i);
            store.edit("shop", current -> reading(line));
        }
        assertEquals(EntryStore.HISTORY, store.undoable("shop"));
        StoredHologram oldest = null;
        while (store.undoable("shop") > 0) oldest = store.undo("shop").orElseThrow();
        assertEquals(reading("5"), oldest);
    }

    @Test
    @DisplayName("a rename carries the history along, a delete forgets it")
    void renameAndDelete() {
        EntryStore<HologramFile, StoredHologram> store = store();
        store.create("shop", reading("a"));
        store.edit("shop", current -> reading("b"));
        store.rename("shop", "store");
        assertEquals(0, store.undoable("shop"));
        assertEquals(reading("a"), store.undo("store").orElseThrow());

        store.edit("store", current -> reading("c"));
        store.delete("store");
        store.create("store", reading("d"));
        assertTrue(store.undo("store").isEmpty(), "a new entry under an old name starts clean");
    }

    @Test
    @DisplayName("a hand edit of the file can be undone like any other change")
    void handEdit() throws IOException {
        EntryStore<HologramFile, StoredHologram> store = store();
        store.create("shop", reading("a"));
        Path file = folder.resolve("holograms.yml");
        Files.writeString(file, Files.readString(file).replace("- a", "- edited"));
        store.reload();
        assertEquals(reading("edited"), store.get("shop").orElseThrow());
        assertEquals(reading("a"), store.undo("shop").orElseThrow());
    }
}
