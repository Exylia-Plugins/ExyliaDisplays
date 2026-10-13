# ExyliaHolograms

## Releasing

- Free and open source (GPL-3.0), public at `Exylia-Plugins/ExyliaHolograms`.
- Ordinary changes never touch `version`; it changes only during a release (`/exylia-release`), which bumps `build.gradle`, tags the commit `v<version>` and pushes both.
- The release workflow only runs on `v*` tags and expects the tag to match the `build.gradle` version; a push to `main` without a tag only builds.

## ExyliaLib

- Always the newest: `build.gradle` resolves the latest GitHub release (or a newer one in mavenLocal) at build time, and that version becomes plugin.yml's `exylia-lib`. There is no pinned version to bump; `-PexyliaLibVersion` pins one for a single build.
- The bootstrap installer downloads the latest release, falling back to the built-against one.
- Updates of this plugin come from ExyliaLib's `PluginUpdater` (`exylia-updates` in plugin.yml, the fixed-name `ExyliaHolograms.jar` on every release).

## Layout

- `holograms.yml` is the single source of truth. Every edit — command, screen or hand edit — goes through `store/HologramStore`, which writes the file and tells `service/HologramRenderer`; `service/FileWatcher` applies hand edits.
- Line tags are ExyliaLib's (`net.exylia.lib.hologram.HologramLine`); a new per-line feature is added there, not here.
