package net.exylia.exyliaDisplays.module;

import net.exylia.exyliaDisplays.ExyliaDisplays;

import java.util.List;

/**
 * One thing this plugin puts in the world: holograms, NPCs, and whatever comes next.
 *
 * <p>A module owns its file, its screens, its commands and its part of {@code messages}; the core
 * owns what they share (inputs, menus, actions, interactions, reloads). Adding a module is one
 * class and one line in {@link Modules}: nothing else in the plugin needs to know about it, and an
 * owner can switch it off in {@code config.yml} without it costing anything.
 */
public interface DisplayModule {

    /** The key in {@code config.yml}'s {@code modules} section, such as {@code holograms}. */
    String id();

    /** The menu files under {@code lang/<code>/} this module opens, by id. */
    default List<String> menus() {
        return List.of();
    }

    /** Starts the module: reads its files, shows what it owns, registers its commands. */
    void enable(ExyliaDisplays core);

    /** Applies its files again after a reload. */
    void reload();

    /** Takes down what it shows. Everything the library owns is released by the library. */
    void disable();
}
