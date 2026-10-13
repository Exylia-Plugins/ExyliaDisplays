package net.exylia.exyliaDisplays.module;

import net.exylia.exyliaDisplays.hologram.HologramModule;
import net.exylia.exyliaDisplays.npc.NpcModule;

import java.util.List;
import java.util.function.Supplier;

/**
 * Every module this plugin ships, in the order they start.
 *
 * <p>The one place a new module is added. Order matters only where one module builds on another:
 * NPCs draw their labels as holograms, so holograms start first.
 */
public final class Modules {

    private Modules() {
        throw new AssertionError("No instances.");
    }

    /** A fresh instance of every module; the core keeps the ones the config switches on. */
    public static List<Supplier<DisplayModule>> all() {
        return List.of(HologramModule::new, NpcModule::new);
    }
}
