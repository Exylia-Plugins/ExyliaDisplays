# ExyliaHolograms

## Releasing

- Free and open source (GPL-3.0), public at `Exylia-Plugins/ExyliaHolograms`.
- Ordinary changes never touch `version`; it changes only during a release (`/exylia-release`), which bumps `build.gradle`, tags the commit `v<version>` and pushes both.
- The release workflow only runs on `v*` tags and expects the tag to match the `build.gradle` version; a push to `main` without a tag only builds.

## Layout

- `holograms.yml` is the single source of truth. Every edit — command, screen or hand edit — goes through `store/HologramStore`, which writes the file and tells `service/HologramRenderer`; `service/FileWatcher` applies hand edits.
- Line tags are ExyliaLib's (`net.exylia.lib.hologram.HologramLine`); a new per-line feature is added there, not here.
