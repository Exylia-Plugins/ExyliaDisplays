# ExyliaDisplays

Formerly ExyliaHolograms: holograms, NPCs and future "things in the world" modules, free and open source (GPL-3.0), public at `Exylia-Plugins/ExyliaDisplays`.

## Releasing

- Ordinary changes never touch `version`; it changes only during a release, which bumps `build.gradle`, tags the commit `v<version>` and pushes both.
- The release workflow only runs on `v*` tags and expects the tag to match the `build.gradle` version; a push to `main` without a tag only builds.

## ExyliaLib

- Always the newest: `build.gradle` resolves the latest GitHub release (or a newer one in mavenLocal) at build time, and that version becomes plugin.yml's `exylia-lib`. There is no pinned version to bump; `-PexyliaLibVersion` pins one for a single build.
- The bootstrap installer downloads the latest release, falling back to the built-against one.
- Updates of this plugin come from ExyliaLib's `PluginUpdater` (`exylia-updates` in plugin.yml, the fixed-name `ExyliaDisplays.jar` on every release).
- Generic pieces live in ExyliaLib, not here: line tags (`HologramLine`), placed NPCs (`Npcs.of(plugin).place`), click interactions (`Interactions`, `Click`). A new interaction token that only makes sense for one module is registered by that module with `Interactions.token`.

## Layout

- `ExyliaDisplays` is the core: shared services and the module list. A module implements `module/DisplayModule` and is added to `module/Modules` (order matters only when one builds on another: NPCs after holograms).
- Each module keeps everything about its subject in its own package (`hologram/`, `npc/`): a record per entry, a file record, a renderer, views, dialogs, actions (prefixed `holo_`, `npc_`), a command, messages in `messages/<module>`, menus `menus/admin/<module>_*.yml`.
- `common/store/EntryStore` is the one door every change to a module file goes through (command, screen or hand edit); `common/FileWatcher` applies hand edits; `common/interaction/InteractionGate` adds permission and cooldown in front of an interaction.
