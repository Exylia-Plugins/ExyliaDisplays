# ExyliaHolograms

Free, open-source holograms for Paper and Folia that do what the others can't: lines of different sizes that make room for each other, floating items and spinning blocks inside the same hologram, shine and colour flow, a typewriter effect, lines only some players see, and click actions. Everything can be edited **in game through native dialog windows**, **with commands**, or **by hand in `holograms.yml`**, and all three stay in sync.

Built on [ExyliaLib](https://github.com/DiGround-s/ExyliaLib)'s packet holograms: no entities on the server and nothing saved in chunks. A line is only sent again when its text actually changes.

## Why another hologram plugin

| | ExyliaHolograms |
| --- | --- |
| **Per-line size** | `<size:2.2>` makes one line bigger, and the lines above move up to make room |
| **Models in the stack** | `<item:DIAMOND>` and `<block:BEACON>` lines between text lines, with `<glow:#59a4ff>` outlines |
| **Motion** | `<spin>`, `<bob>`, `<pulse>`: the client interpolates between keyframes, so the server sends two packets a second, not twenty |
| **Colour that moves** | `<shine>` sweeps a band of light across a title; `<flow>` makes a gradient's colours run along it |
| **Typewriter** | `<typewriter>` types a line in when a player first sees it |
| **Lines for some players** | `<range:8>` shows a line only up close, `<perm:vip>` only to VIPs, `<if:"%player_level% >= 10">` only while a condition holds. The stack re-lays itself out for each viewer |
| **Clickable** | Commands on click, and the hologram grows slightly while you look at it, so players can tell it's a button |
| **Three ways to edit** | Screens and dialogs, `/holo` commands, or `holograms.yml`, which is applied as soon as you save it |
| **Imports** | `/holo import` reads DecentHolograms, HolographicDisplays and FancyHolograms and leaves their files untouched |

## Requirements

- Paper or Folia 1.21+, Java 21 (dialog windows need 1.21.6+; older clients and Bedrock players get the same fields through chat or forms)
- [PacketEvents](https://modrinth.com/plugin/packetevents)
- [ExyliaLib](https://github.com/DiGround-s/ExyliaLib/releases). If it is missing, the plugin downloads the latest release on first start and asks for a restart.

## Updates

ExyliaLib keeps both itself and this plugin up to date from their GitHub releases: a new version is downloaded in the background and applied on the next restart. A new major version waits for you to install it by hand, and a release that needs a newer ExyliaLib waits until the library has updated. Turn it off or tune it under `plugin-updates` in `plugins/ExyliaLib/config.yml`; `/exylialib updates` shows what is pending.

Optional: PlaceholderAPI, whose placeholders work in any line.

## Getting started

1. Stand where you want it and run `/holo create spawn`, or open `/holo` and click **CREATE HOLOGRAM** to start from a preset.
2. In its editor, open **LINES**:
   - left-click a line to change its text;
   - right-click it for size, motion and visibility, all in one window;
   - shift-click to move it up or down; press Q to remove it.
3. **EDIT ALL AT ONCE** opens every line in a single box, one per row, which is the fastest way to write a whole hologram.

## Line tags

Tags go at the very start of a line and style that line alone:

An arena entrance: a block turning above a shining title, a line each player
reads differently, and a hint that types itself in only when you get close.

```yaml
lines:
  - '<block:NETHERITE_BLOCK><spin:45><glow:#ff6b9d>'
  - '<size:2.4><shine>{accent}&lTHE ARENA'
  - '<flow>{secondary}Season II · open now'
  - '<size:0.5> '
  - '<if:"%player_level% >= 10">{success}✔ You qualify'
  - '<if:"%player_level% < 10">{error}Reach level 10 to enter'
  - '<range:5><typewriter><background:#55000000>{letters}Right-click to join the queue'
```

A members' door: one line for those with the permission, another for everyone else.

```yaml
lines:
  - '<item:AMETHYST_SHARD><bob><glow:#b48fd9>'
  - '<size:1.6><pulse:0.04>{secondary}&lTHE OBSERVATORY'
  - '<perm:observatory.key>{success}Welcome in'
  - '<perm:!observatory.key><opacity:180>{letters_black}Find the key in the library'
```

More starting points ship as presets in `config.yml`: a portal sign, a members-only lounge, a parkour checkpoint and a welcome sign.

| Tag | Effect |
| --- | --- |
| `<size:2>` | bigger (or smaller) than the rest; the stack makes room |
| `<opacity:160>` | text opacity, 0 to 255 |
| `<background:#66000000>` | the line's own background, `#AARRGGBB` |
| `<shadow:false>` | shadow off (or `<shadow>` on) for this line |
| `<item:DIAMOND>` / `<block:BEACON>` | a floating item or block instead of text |
| `<glow>` / `<glow:#8a51c4>` | glow outline on an item or block line |
| `<spin>` / `<spin:180>` | turns, in degrees a second |
| `<bob>` / `<bob:0.15>` | floats up and down, in blocks |
| `<pulse>` / `<pulse:0.1>` | grows and shrinks by that fraction |
| `<shine>` / `<shine:#ffd700>` | a band of light sweeps over it every few seconds |
| `<flow>` / `<flow:8>` | its colours flow along it |
| `<typewriter>` / `<typewriter:40>` | typed in, in characters a second |
| `<range:8>` | only within 8 blocks |
| `<perm:node>` / `<perm:!node>` | only with (or without) a permission |
| `<if:"%placeholder% >= 10">` | only while the comparison holds |

Anything else at the start of a line, such as `<gradient:...>`, stays as text. Colours use ExyliaLib's palette tokens (`{primary}`, `{highlight}`...) as well as `&` codes, hex and MiniMessage.

## Commands

Everything needs `exyliaholograms.admin` (operators by default). Line numbers count from 1, top first.

| Command | |
| --- | --- |
| `/holo` (`/hologram`) | Every hologram, on a screen |
| `/holo create <name> [text]` | A new hologram where you stand |
| `/holo edit <name>` · `/holo lines <name>` | Its editor, or its lines |
| `/holo addline <name> <text>` | Add a line at the bottom |
| `/holo setline <name> <line> <text>` | Replace a line |
| `/holo insertline <name> <line> <text>` | Insert a line before another |
| `/holo removeline <name> <line>` | Remove a line |
| `/holo moveline <name> <from> <to>` | Move a line |
| `/holo movehere <name>` · `/holo move <name> <x> <y> <z> [world]` · `/holo center <name>` | Move it |
| `/holo tp <name>` | Go to it |
| `/holo near [radius]` | The ones around you, with click-to-edit |
| `/holo info <name>` · `/holo list` | Details, or every hologram in chat |
| `/holo enable\|disable <name>` | Show or hide it |
| `/holo clone <name> <new>` · `/holo rename <name> <new>` · `/holo delete <name>` | |
| `/holo import <DECENTHOLOGRAMS\|HOLOGRAPHICDISPLAYS\|FANCYHOLOGRAMS>` | Bring holograms over from another plugin |
| `/holo reload` | Read the files again |

## Files

- **`holograms.yml`**: every hologram. Edit it while the server runs and the change is applied within a second (turn this off with `watch-file` in `config.yml`). Every key:

  ```yaml
  holograms:
    arena:
      enabled: true
      location: { world: world, x: 10.5, y: 66.0, z: -4.5 }
      lines:
        - '<size:2><shine>{accent}&lTHE ARENA'
      permission: ''          # who sees it; empty for everyone
      view-distance: 48.0
      per-player: false       # each viewer gets their own %player_...% values
      appearance:             # billboard, alignment, scale-x/y/z, shadow, see-through,
        billboard: CENTER     # line-width, line-spacing, text-opacity, default-background,
        line-spacing: 0.25    # background-color, brightness
      refresh:
        update-interval: 20   # ticks between placeholder refreshes
        auto-update: true
      click:
        commands:             # 'console: ...' runs from the console, a bare line as the player
          - 'arena join'
        width: 1.5            # click box width, in blocks
        cooldown: 500         # milliseconds between two clicks of the same player
        hover-scale: 1.08     # how much it grows while looked at; 1 turns it off
  ```

- **`config.yml`**: file watching, where new holograms stand, the default view distance, and the **presets** offered when creating from the menu. Add your own presets there.
- **`lang/<code>/messages.yml`** and **`lang/<code>/menus/`**: every message and screen, in English and Spanish. The language follows ExyliaLib's.

## Building

```
./gradlew build
```

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[GNU General Public License v3.0](LICENSE).
