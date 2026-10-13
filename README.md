# ExyliaDisplays

Free, open-source **holograms and NPCs** for Paper and Folia, with one interaction system shared by both. Holograms can have lines of different sizes, spinning blocks, shine and typewriter effects, and lines only some players see. NPCs can greet each player in their own speech bubble, show every viewer their own skin, and send players to another server. Edit everything **in game through native dialog windows**, **with commands**, or **by hand in YAML**, and all three stay in sync.

Built on [ExyliaLib](https://github.com/DiGround-s/ExyliaLib): everything is packets. Nothing is a real entity, nothing is saved in chunks, and nothing ticks on the server.

## What it does that others don't

| | |
| --- | --- |
| **One interaction system** | Holograms and NPCs run the same click lines: `right: [SERVER] survival`, `left: [SAY] Hey!`, `console: give %player_name% diamond 1`, `[MESSAGE]`, `[SOUND]`, `[TITLE]`, `[DELAY] 1s`, `[TELEPORT]`. Left and right clicks can do different things |
| **NPCs that talk** | `[SAY]` puts a speech bubble above the NPC that only the clicking player sees, typed in letter by letter |
| **Mirror skin** | An NPC with `@mirror` shows each player their own skin |
| **Looks at each of you** | In `each-viewer` mode, every player sees the NPC turn its head toward them, not toward someone else |
| **Labels that are holograms** | An NPC's name is a hologram with every line tag (size, shine, typewriter, per-player lines), and clicking it does what clicking the NPC does. Link any hologram to an NPC and it follows the NPC wherever you move it |
| **Per-line size** | `<size:2.2>` makes one hologram line bigger, and the lines above move up to make room |
| **Models in the stack** | `<item:DIAMOND>` and `<block:BEACON>` lines between text lines, with glow outlines |
| **Motion** | `<spin>`, `<bob>`, `<pulse>`: the client interpolates between keyframes, so the server sends two packets a second, not twenty |
| **Colour that moves** | `<shine>` sweeps a band of light across a title; `<flow>` makes a gradient's colours run along it |
| **Lines for some players** | `<range:8>`, `<perm:vip>`, `<if:"%player_level% >= 10">`: the stack re-lays itself out for each viewer |
| **Three ways to edit** | Screens and dialogs, `/holo` and `/npc` commands, or `holograms.yml` and `npcs.yml`, which are applied as soon as you save them |
| **Imports** | DecentHolograms, HolographicDisplays, FancyHolograms, Citizens and FancyNpcs. Their files are read and never changed. Import NPCs before holograms so a FancyHolograms hologram linked to an NPC stays linked |
| **Modular** | Turn holograms or NPCs off in `config.yml`. New modules plug into the same core |

## Requirements

- Paper or Folia 1.21+, Java 21 (dialog windows need 1.21.6+; older clients and Bedrock players get the same fields through chat or forms)
- [PacketEvents](https://modrinth.com/plugin/packetevents)
- [ExyliaLib](https://github.com/DiGround-s/ExyliaLib/releases). If it is missing, the plugin downloads the latest release on first start and asks for a restart.

Optional: PlaceholderAPI, whose placeholders work in any line and any action.

## Updates

ExyliaLib keeps both itself and this plugin up to date from their GitHub releases: a new version is downloaded in the background and applied on the next restart. A new major version waits for you to install it by hand, and a release that needs a newer ExyliaLib waits until the library has updated. Turn it off or tune it under `plugin-updates` in `plugins/ExyliaLib/config.yml`; `/exylialib updates` shows what is pending.

**Coming from ExyliaHolograms?** This is the same plugin, renamed now that it does more. On first start, `plugins/ExyliaHolograms/holograms.yml` is copied over (the old folder is left untouched). Remove the old jar. `exyliaholograms.admin` keeps working.

## Getting started

**A hologram:** stand where you want it and run `/holoadmin create spawn`, or open `/holoadmin` and click **CREATE HOLOGRAM** to start from a preset. In its editor, **LINES**:
- left-click a line to change its text;
- right-click it for size, motion and visibility, all in one window;
- shift-click to move it up or down; press F to duplicate it, Q to remove it.

**An NPC:** `/npcadmin create guide Notch`, or open `/npcadmin` and click **CREATE NPC** to start from a preset. The presets are a guide that greets you, a server portal, a shopkeeper and a mirror. Its editor has cards for:
- appearance: skin, pose, scale, glow;
- equipment: wear the item in your hand;
- label: the hologram above its head;
- interactions;
- visibility;
- location.

## Interactions

The same lines work in a hologram's `click.commands` and an NPC's `interactions.actions`. One action per line, run in order:

```yaml
actions:
  - 'right: [SOUND] ENTITY_VILLAGER_AMBIENT;1;1.2'
  - 'right: [SAY] {letters}Welcome, {highlight}%player_name%{letters}!;6s'
  - 'left: [ANIMATE] hurt'
  - 'shift_right: [SERVER] survival'
  - 'console: give %player_name% bread 1'
```

| Line | Does |
| --- | --- |
| `left:` `right:` `shift_left:` `shift_right:` `shift:` `any:` | Optional prefix: only that click runs the line |
| `command` · `player: command` · `console: command` | Runs a command as the player or from the console |
| `player-proxy: server lobby` · `console-proxy: alert ...` | Runs it on the proxy (with ExyliaProxyUtils) |
| `[SERVER] lobby` | Sends the player to another server of the network (BungeeCord or Velocity) |
| `[TELEPORT] world;x;y;z[;yaw;pitch]` | Teleports the player |
| `[DELAY] 1s` | Waits before the lines after it |
| `[MESSAGE] text` · `[TITLE] title;subtitle` · `[ACTION_BAR] text` · `[SOUND] NAME;volume;pitch` · `[TOAST] ...` · `[PARTICLE] ...` | Shown only to the player who clicked |
| `[SAY] text;5s` | NPCs: a speech bubble above the NPC, only for the player who clicked |
| `[ANIMATE] swing` · `[ANIMATE] hurt` | NPCs: swings an arm, or flinches |
| `[LOOK]` | NPCs: turns to the player who clicked |
| `[ACTION] namespace:id` | Runs an action another Exylia plugin registered |

NPC interactions can also require a permission and have a per-player cooldown, each with its own refusal message.

## Line tags

Hologram lines and NPC labels both use these tags. They go at the very start of a line and style that line alone.

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

Anything else at the start of a line, such as `<gradient:...>`, stays as text. Colours use ExyliaLib's palette tokens (`{primary}`, `{highlight}`...) as well as `&` codes, hex and MiniMessage.

## Commands

| Command | Permission | |
| --- | --- | --- |
| `/displays help\|modules\|reload` | `exyliadisplays.admin` | The plugin as a whole |
| `/holoadmin` (`/hologramadmin`) | `exyliadisplays.holograms` | Every hologram, on a screen, plus what is not about one hologram: `create`, `list`, `near`, `import`, `reload`, `help` |
| `/holo <name> [setting] ...` (`/hologram`) | `exyliadisplays.holograms` | One hologram: its editor with no setting, or any setting by command. `info`, `tp`, `movehere`, `move`, `center`, `rotate <yaw> [pitch]`, `face`, `rename`, `clone`, `enable`, `disable`, `delete`; lines with `addline`, `setline`, `insertline`, `removeline`, `moveline`, `lines`, and one tag of one line with `line <n> <tag> [value]`; the look with `billboard`, `alignment`, `scale`, `shadow`, `seethrough`, `linewidth`, `spacing`, `opacity`, `background`, `brightness`; who sees it with `viewdistance`, `permission`, `perplayer`, `refresh`, `autoupdate`; which players see it with `visibility all\|manual`, `show\|hide <player\|@a>`, `viewers [clear]`; what it rides with `attach <player\|uuid\|nearest>`, `detach`, `attachoffset <blocks>`; clicks with `click add\|remove\|clear\|width\|cooldown\|hover` |
| `/npcadmin` (`/npcsadmin`) | `exyliadisplays.npcs` | Every NPC, on a screen, plus what is not about one NPC: `create <name> [skin]`, `list`, `near`, `import`, `reload`, `help` |
| `/npc <name> [setting] ...` (`/npcs`) | `exyliadisplays.npcs` | One NPC: its editor with no setting, or any setting by command. `info`, `tp`, `movehere`, `move`, `center`, `rotate <yaw> [pitch]`, `face`, `rename`, `clone`, `enable`, `disable`, `delete`; the look with `skin`, `texture <value> [signature]`, `pose`, `scale`, `glow`, `equip <slot\|clear> [item]`, `equipment`; `look <mode> [range]`, `viewdistance`, `permission`; the label with `label set\|add\|remove\|clear\|height`, or a whole hologram with `link`, `unlink`; clicks with `action add\|remove\|clear\|list` and `action permission\|cooldown\|denymessage\|cooldownmessage\|faceclicker` |

`exyliadisplays.admin` includes both module permissions; `exyliadisplays.*` grants everything. If Citizens is installed too, its `/npc` and this one compete for the name; use `/npcs <name>` (`/npcadmin` does not clash).

## Files

- **`config.yml`** contains:
  - which modules run;
  - file watching;
  - each module's defaults;
  - the **presets** offered when creating from the menu (add your own).
- **`holograms.yml`** and **`npcs.yml`** hold everything you place. Edit them while the server runs and the change is applied within a second. A hand-written entry only needs the keys it changes. An NPC:

  ```yaml
  npcs:
    guide:
      location: {world: lobby, x: 0.5, y: 64.0, z: -3.5, yaw: 180.0, pitch: 0.0}
      skin: {type: player, player: Notch}      # player, texture, mirror, none
      pose: standing                           # sneaking, lying, crawling, spinning
      look: {mode: each-viewer, range: 8.0}    # fixed, nearest, each-viewer
      glow: '#8a51c4'
      equipment: {hand: DIAMOND_SWORD}
      label: {lines: ['<size:1.2><shine>{primary}&lGUIDE'], height-offset: 0.25}
      linked-hologram: ''                      # a hologram that follows it
      interactions:
        actions: ['right: [SAY] Hello, %player_name%!;5s']
        permission: ''
        cooldown: 2s
        face-clicker: true
  ```

- **Which players see a hologram, and what it rides**, are kept in `holograms.yml` with the rest of it, so they survive a restart and a hand edit changes them:

  ```yaml
  holograms:
    nametag:
      visibility:
        mode: manual          # all: everyone but hidden-from; manual: only shown-to
        shown-to: ['069a79f4-44e9-4726-a5be-fca90e38aaf5']
        hidden-from: []
      attach:
        entity: '069a79f4-44e9-4726-a5be-fca90e38aaf5'   # a player or a saved mob; empty for none
        offset: 0.5
  ```

  `/holo <name> show` and `hide` work in both modes: in `all` they take a player off or put them on `hidden-from`, in `manual` they put them on or take them off `shown-to`; `@a` means every player online. Only the exceptions are written, as UUIDs, so a renamed player keeps their place. An attached hologram rides its player whenever they are online (joining, respawning and changing worlds included) and a saved mob whenever its chunk is loaded, and is hidden while they are away. A mob that is not saved with its chunk is ridden until it is gone or the server restarts, without being written down.

- **`lang/<code>/messages.yml`**, **`messages/holograms.yml`**, **`messages/npcs.yml`** and **`menus/`** contain every message and screen, in English and Spanish. The language follows ExyliaLib's.

## Building

```
./gradlew build
```

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[GNU General Public License v3.0](LICENSE).
