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
| **Models in the stack** | `<item:DIAMOND>`, `<block:BEACON>` and `<head:Notch>` lines between text lines, with glow outlines |
| **Pages per player** | A hologram can have several pages and every player reads their own: a `[PAGE] next` click turns it for them alone, or each player's page turns on its own every `page-interval` |
| **Motion** | `<spin>`, `<sway>`, `<bob>`, `<pulse>`, each with its own period: the client interpolates between keyframes, so the server sends two packets a second, not twenty |
| **Colour that moves** | `<shine>` sweeps a band of light across a title; `<flow>` makes a gradient's colours run along it; `<rainbow>` runs a soft rainbow through it |
| **Text that moves** | `<frames>` turns a line between several texts, `<marquee>` scrolls a long one, `<fade>`, `<blink>`, `<typewriter>` and `<scramble>` bring it in |
| **Buttons** | A line tagged `<button:join>` is a button of its own, with its own actions, that lights up, grows and presses; a hologram clickable as a whole gets a slim panel fitted to its lines. Clicks reach as far as the highlight |
| **Readable from afar** | `distance-scale` grows a hologram with each viewer's distance; `<autoscale>` does it for one line |
| **Presets and templates** | One click puts several tags on a line (title, badge, ticker, live, glitch, far, button...); eight ready-made templates (welcome, kiosk, status, showcase, landmark, vault, event, signpost) use every tag and setting between them |
| **NPCs that move** | Animations on left and right click, an idle one every few seconds, and a greeting for whoever walks up; NPCs can sit |
| **Lines for some players** | `<range:8>`, `<perm:vip>`, `<if:"%player_level% >= 10">`: the stack re-lays itself out for each viewer |
| **Three ways to edit** | Screens and dialogs, `/holo` and `/npc` commands, or `holograms.yml` and `npcs.yml`, which are applied as soon as you save them |
| **Imports** | DecentHolograms, HolographicDisplays, FancyHolograms, Citizens and FancyNpcs, pages and heads included. Their files are read and never changed. Import NPCs before holograms so a FancyHolograms hologram linked to an NPC stays linked |
| **Modular** | Turn holograms or NPCs off in `config.yml`. New modules plug into the same core |

## Requirements

- Paper or Folia 1.21.4 or newer, Java 21 (dialog windows need 1.21.6+; older clients and Bedrock players get the same fields through chat or forms)
- [PacketEvents](https://modrinth.com/plugin/packetevents)
- [ExyliaLib](https://modrinth.com/plugin/exylialib) ([GitHub releases](https://github.com/DiGround-s/ExyliaLib/releases)). Install it first; without it the plugin stays off and says where to get it.

Optional: PlaceholderAPI, whose placeholders work in any line and any action; ExyliaEmotes, whose emotes NPCs can perform.

## Updates

ExyliaLib checks this plugin's GitHub releases and announces a new version in the console and to admins. Nothing is downloaded on its own: run `/exylialib plugins` to download the update, which is applied on the next restart. A new major version waits for you to install it by hand, and a release that needs a newer ExyliaLib waits until the library has updated. Tune it under `plugin-updates` in `plugins/ExyliaLib/config.yml`.

**Coming from ExyliaHolograms?** This is the same plugin, renamed now that it does more. On first start, `plugins/ExyliaHolograms/holograms.yml` is copied over (the old folder is left untouched). Remove the old jar. `exyliaholograms.admin` keeps working.

## Getting started

**A hologram:** stand where you want it and run `/holoadmin create spawn`, or open `/holoadmin` and click **CREATE HOLOGRAM** to start from a preset or a template. In its editor, **LINES**:
- left-click a line to change its text;
- right-click it for size, motion and visibility, all in one window;
- shift-click to move it up or down; press F to duplicate it, Q to remove it.

**An NPC:** `/npcadmin create guide Notch`, or open `/npcadmin` and click **CREATE NPC** to start from a preset. The presets are a greeter that waves at whoever walks up, a sitting bartender, an armoured guard that watches the nearest player, a merchant, a server portal and a mirror. Its editor has cards for:
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
| `[ANIMATE] wave` · `[ANIMATE] swing, 300ms, hurt` · `[ANIMATE] anim:bow` | NPCs: plays an animation, a preset, moves or a [performance](#performances); `[ANIMATE] swing` and `[ANIMATE] hurt` read as before |
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
| `<head:Notch>` | a player head: a name, a UUID, a base64 texture or a `textures.minecraft.net` URL. `<size:0.5>` makes it small, `<size:2>` large |
| `<glow>` / `<glow:#8a51c4>` | glow outline on an item or block line |
| `<gap:0.3>` | extra blocks above the line; negative pulls the lines above closer |
| `<align:left>` / `center` / `right` | the line's own alignment |
| `<width:120>` | where the line wraps, in pixels |
| `<billboard:fixed>` / `center` / `vertical` / `horizontal` | how the line turns towards the player |
| `<seethrough>` / `<seethrough:false>` | whether it shows through blocks |
| `<brightness:15>` | its light level, 0 to 15, -1 for the light where it stands |
| `<autoscale>` / `<autoscale:4,24,3>` | grows with the viewer's distance: on the hologram's curve, or its own `start,end,max` |
| `<spin>` / `<spin:180>` | turns, in degrees a second |
| `<sway>` / `<sway:20,period=6>` | turns back and forth by that many degrees |
| `<bob>` / `<bob:0.15,period=3>` | floats up and down, in blocks |
| `<pulse>` / `<pulse:0.1,period=2>` | grows and shrinks by that fraction |
| `<shine>` / `<shine:highlight,speed=15,pause=1,width=4>` | a band of light sweeps over it every few seconds |
| `<flow>` / `<flow:8>` | its colours flow along it |
| `<rainbow>` / `<rainbow:10>` | a soft rainbow runs along it, in characters a second |
| `<frames:2>` | the text is split by `\|\|` into frames that take turns, each shown 2 seconds |
| `<marquee:24,speed=6>` | shows 24 characters of a longer line at a time, scrolling |
| `<fade:3>` / `<blink:1>` | fades out and back in, or shows and hides, every that many seconds |
| `<typewriter>` / `<typewriter:40>` | typed in, in characters a second |
| `<scramble>` / `<scramble:30>` | random glyphs settle into it from the left |
| `<button:join>` | clicking this line runs `click.buttons.join` instead of the hologram's click actions |
| `<range:8>` | only within 8 blocks |
| `<perm:node>` / `<perm:!node>` | only with (or without) a permission |
| `<if:"%placeholder% >= 10">` | only while the comparison holds |

In game, right-click a line on the lines screen: a **preset**, or one window per theme (look, layout, movement, colour effects, text effects, frames, who sees it). By command, `/holo <name> line <n> <tag> [value]` sets one tag (`line 2 sway 20,period=6`, `on` for the bare tag, no value to take it off) and `/holo <name> line <n> preset <preset>` several at once.

### Presets

A preset puts several tags on a line at once; its tags replace the line's own of the same kind, and the text stays. They live in `config.yml` under `holograms.line-presets`, and you can add your own:

```yaml
line-presets:
  title: '<size:1.6><gap:0.15><shine>'
  subtitle: '<size:0.9><opacity:191>'
  highlight: '<rainbow>'
  subtle: '<opacity:200><fade:4>'
  ticker: '<marquee:28,speed=5>'
  button: '<gap:0.12><size:1.15><button:main>'
  badge: '<size:0.9><background:#998a51c4><shadow:false>'
  whisper: '<range:6><opacity:170>'
  glitch: '<scramble:18>'
  live: '<blink:1.2>'
  wide: '<width:320>'
  left: '<align:left><width:150>'
  showcase: '<spin:60><bob:0.08,period=4><glow>'
  far: '<autoscale:6,30,2.5>'
```

### Templates

A template is a whole look under a name: lines, pages, appearance, refresh and click. `templates.yml` ships with eight that, between them, use every line tag and setting, so browsing them is a tour of the plugin: `welcome` (pages that turn on their own), `kiosk` (button lines), `status` (live placeholders), `showcase` (a turning item on a beacon), `landmark` (size by distance), `vault` (lines by permission, level and distance), `event` (frames, rainbow, blink, fade) and `signpost` (a fixed, left-aligned wall sign). Pick one under **CREATE HOLOGRAM**, or run `/holoadmin create <name> --template <t>`. A server whose `templates.yml` still held none gets them once.

### Buttons and size by distance

```yaml
holograms:
  arena:
    lines:
      - '{primary}&lTHE ARENA'
      - '<gap:0.1><button:join>{success}&l▶ JOIN'
      - '<button:spectate>{info}Spectate'
    appearance:
      distance-scale: {enabled: true, start: 8.0, end: 32.0, min: 1.0, max: 2.5}
    click:
      commands: ['[MESSAGE] {letters}Pick a button below.']
      style: BUTTON              # BUTTON or NONE
      color: '{primary}'         # palette name, #RRGGBB or #AARRGGBB
      hover-color: '{secondary}'
      sound: 'UI_BUTTON_CLICK;0.6;1.4'
      range: 8                   # blocks away it can be aimed at and clicked
      buttons:
        join: ['arena join']
        spectate: ['left: arena spectate', '[PAGE] next']
```

A `<button:...>` line is drawn as a button, its own background sized to its label (a column of buttons shares one width), that lights up while aimed at and presses when clicked. A hologram without button lines that is clickable as a whole gets one slim panel fitted to its lines instead; `style: NONE` keeps only the hover growth. Left-clicks, and right-clicks holding an item, work from as far as `range` (default 8 blocks); a right-click with an empty hand only from as close as the game lets a hand reach an entity (3 blocks in survival), because the client sends nothing further away. In the hologram's editor, **SIZE BY DISTANCE** edits the curve, and **CLICK ACTIONS** edits the actions (left-click), the look and sound (right-click) and the buttons (shift-click).

Anything else at the start of a line, such as `<gradient:...>`, stays as text. Colours use ExyliaLib's palette tokens (`{primary}`, `{highlight}`...) as well as `&` codes, hex and MiniMessage.

## Pages

`lines` is page 1, so a hologram written before pages existed is unchanged; `pages` adds more.
Every player sees one page at a time. `click` runs on page 1 and each page's own `click` on that
page, so the same box can say different things on each.

```yaml
holograms:
  rules:
    lines:
      - '<head:Notch><size:0.8>'
      - '{primary}&lWELCOME'
      - '{warning}➥ Click for the rules'
    pages:
      - lines:
          - '{primary}&lRULES'
          - '{letters}No griefing, no cheating.'
        click: ['left: [PAGE] previous', 'right: [PAGE] 1']
    page-interval: 200          # ticks; each player's page turns on its own. 0 never
    click:
      commands: ['[PAGE] next']
```

`[PAGE] next`, `[PAGE] previous`, `[PAGE] first`, `[PAGE] last` and `[PAGE] 2` turn the page of
the player who clicked. In game, the book on the lines screen moves between pages (left and right
click), adds one (shift-left), edits that page's click actions (shift-right) or removes it (Q).

## NPC animations

```yaml
npcs:
  guide:
    sitting: false
    animations:
      left-click: 'flinch'
      right-click: 'wave'
      idle: 'nod'
      idle-every: 20s           # 0 for never
      greet: 'bow'              # seen only by the player who walks up
      greet-range: 4.0
      greet-cooldown: 30s
```

An animation is a preset (`wave`, `wave-offhand`, `nod`, `bow`, `flinch`, `punch`, `cheer`) or moves joined by commas: `swing`, `offhand`, `hurt`, `crouch`, `stand` and waits such as `250ms`. In game, **ANIMATIONS** in the NPC's editor edits the click animations (left-click) and the idle and greeting ones (right-click); a word nobody knows is reported and nothing is saved.

### Performances

Any of the four triggers can hold one **performance** instead of vanilla moves: a whole-body
animation, the ragdoll body ExyliaEmotes dances with, wearing the NPC's skin and standing in its
place. A value with a colon is a performance; one without is moves.

```yaml
    animations:
      right-click: 'anim:bow'            # plugins/ExyliaDisplays/animations/bow.json
      idle: 'emote:salute'               # an ExyliaEmotes emote
      idle-every: 45s
      greet: 'follow:1;keys:0.25 arm_r=150,0,30 elbow_r=40 | 0.3 arm_r=150,0,60 | 0.3 stand'
      perform-range: 24.0                # who is shown it; an idle with nobody this close is skipped
```

| Written | What it plays |
|---|---|
| `anim:<name>` | a Blockbench or Emotecraft animation file from `plugins/ExyliaDisplays/animations` |
| `emote:<id>` | an emote of ExyliaEmotes (below) |
| `keys:<frames>` | frames written in place, in `[RAGDOLL]`'s frame format; `[RAGDOLL]` options such as `follow:2;breathe:0;` may come first |

Frames and emote ids are checked before they are saved, by command or window. In game,
shift-left-click **ANIMATIONS** in the NPC's editor for the **performance picker**: every emote and
animation file, left-click one to put it on a trigger, right-click to try it on the NPC.
`/npc <name> play <value>` tries one too and says when it was skipped, and `[ANIMATE] anim:bow`
performs from a click action.

Limits: one performance at a time per NPC (others are skipped, not queued); a **sitting NPC never
performs**; players who walk up mid-play see the plain NPC; the body draws the NPC's head item and
held items but not chest, legs and feet armour; a looping emote is cut after 8 seconds. A body is a
few dozen display entities per viewer, so prefer vanilla moves for anything a crowd clicks all day.

### ExyliaEmotes

With ExyliaEmotes installed, `emote:<id>` plays any
of its solo emotes (duets are written for two people and are left out). Its `emotes.yml` is read,
never changed, when this plugin starts, on `/npcadmin reload`, when ExyliaEmotes is enabled and
whenever the file is saved; ExyliaEmotes is an optional dependency and nothing else of it is used.
Without it, `emote:` values are refused with a message saying so.

## Commands

| Command | Permission | |
| --- | --- | --- |
| `/displays help\|modules\|reload\|editor` | `exyliadisplays.admin` | The plugin as a whole; `editor` opens holograms and NPCs in the browser (see below) |
| `/holoadmin` (`/hologramadmin`) | `exyliadisplays.holograms` | Every hologram, on a screen, plus what is not about one hologram: `editor`, `create <name> [text] [--template <t>]`, `list`, `near`, `templates`, `templates delete <t>`, `import`, `reload`, `help` |
| `/holo <name> [setting] ...` (`/hologram`) | `exyliadisplays.holograms` | One hologram: its editor with no setting, or any setting by command. `info`, `tp`, `movehere`, `move`, `center`, `rotate <yaw> [pitch]`, `face`, `adjust` (see below), `rename`, `clone`, `enable`, `disable`, `delete`, `undo` (takes back its last change, up to 10); templates with `savetemplate <t>` and `applytemplate <t>`; lines with `addline`, `setline`, `insertline`, `removeline`, `moveline`, `lines`, and one tag of one line with `line <n> <tag> [value]`; pages with `page add`, `page remove <n>`, `page <n>` (turns it for you), `pageinterval <ticks>`, and any line command on another page as `page <n> addline\|setline\|insertline\|removeline\|moveline\|line ...`; the look with `billboard`, `alignment`, `scale`, `shadow`, `seethrough`, `linewidth`, `spacing`, `opacity`, `background`, `brightness`; who sees it with `viewdistance`, `permission`, `perplayer`, `refresh`, `autoupdate`; which players see it with `visibility all\|manual`, `show\|hide <player\|@a>`, `viewers [clear]`; what it rides with `attach <player\|uuid\|nearest>`, `detach`, `attachoffset <blocks>`; size by distance with `distancescale <on\|off\|start end min max>`; clicks with `click add\|remove\|clear\|width\|cooldown\|hover`, the button look with `click style <button\|none>`, `click color\|hovercolor <colour>`, `click sound [sound]`, and buttons with `button <button> <action>` and `button <button> remove`; presets with `line <n> preset <preset>` |
| `/npcadmin` (`/npcsadmin`) | `exyliadisplays.npcs` | Every NPC, on a screen, plus what is not about one NPC: `create <name> [skin]`, `list`, `near`, `import`, `reload`, `help` |
| `/npc <name> [setting] ...` (`/npcs`) | `exyliadisplays.npcs` | One NPC: its editor with no setting, or any setting by command. `info`, `tp`, `movehere`, `move`, `center`, `rotate <yaw> [pitch]`, `face`, `adjust` (see below), `rename`, `clone`, `enable`, `disable`, `delete`, `undo` (takes back its last change, up to 10); the look with `skin`, `texture <value> [signature]`, `pose`, `scale`, `glow`, `equip <slot\|clear> [item]`, `equipment`; `look <mode> [range]`, `viewdistance`, `permission`; the label with `label set\|add\|remove\|clear\|height`, or a whole hologram with `link`, `unlink`; clicks with `action add\|remove\|clear\|list` and `action permission\|cooldown\|denymessage\|cooldownmessage\|faceclicker`; animations with `animation <left-click\|right-click\|idle\|greet> [animation]`, `animation idle-every\|greet-cooldown <time>`, `animation greet-range\|perform-range <blocks>`, `play <animation>` (to everyone watching; a performance to everyone within its perform-range) and `sit <on\|off>` |

`exyliadisplays.admin` includes both module permissions; `exyliadisplays.*` grants everything. If Citizens is installed too, its `/npc` and this one compete for the name; use `/npcs <name>` (`/npcadmin` does not clash).

### Fine adjust

`/holo <name> adjust` (or **FINE ADJUST** in the hologram's editor) lets you place a hologram with the mouse wheel while you look at it. Nothing is written until you finish; the hologram moves live as you scroll.

| Key | Does |
| --- | --- |
| Scroll | Change the value by one step |
| Sneak + scroll | Bigger or smaller step |
| F | Next axis: X, Y, Z, forward (where you look), turn, tilt, scale, line spacing; sneak + F goes back |
| Sneak + click | Snap: block centre, half block for height, nearest 15° |
| Click, or `/holo <name> adjust` again | Save and finish |
| `/holo <name> adjust cancel` (or **UNDO** in chat) | Put everything back |

Two boss bars show the axis, value and step, and the keys. Leaving, dying, changing world or teleporting far saves and finishes; one player adjusts a hologram at a time.

`/npc <name> adjust` (or **FINE ADJUST** in the NPC's editor) works the same way for an NPC, with `adjust save` and `adjust cancel`. Its axes are X, Y, Z, forward, turn, tilt, scale and label height; the label and a linked hologram follow it live, and both files are written once, when you save.

## Web editor

`/displays editor` (or `/holoadmin editor`, or ExyliaLib's `/exylialib editor ExyliaDisplays`) uploads your holograms and NPCs to the Exylia web editor and answers with a link. Holograms get their own screen with a live preview; NPCs a form drawn from `npcs.yml`'s own keys. Save in the browser, then run the `/exylialib editor apply <code>` line the chat gave you: the files are written and applied as a hand edit would be, and the admins are told. A change to click actions, or any line that runs as the console, is shown first and applied only with `confirm`; the same if the file changed on the server since the browser loaded it.

The server never opens a port and never applies anything on its own: only that command, typed in game, brings a change down. The first browser to open the link owns the session; any other sees a code to confirm with `/exylialib editor trust <code> <challenge>`. The flow, its security and the `web-editor` setting are ExyliaLib's; see its `docs/web-editor.md`.

## Files

- **`config.yml`** contains:
  - which modules run;
  - file watching;
  - each module's defaults;
  - the **presets** offered when creating from the menu (add your own; an NPC preset's `body` sets sitting, glow, equipment, look and animations);
  - the **line presets** a line can take at once (add your own).
- **`holograms.yml`** and **`npcs.yml`** hold everything you place. Edit them while the server runs and the change is applied within a second. A hand-written entry only needs the keys it changes. An NPC:

  ```yaml
  npcs:
    guide:
      location: {world: lobby, x: 0.5, y: 64.0, z: -3.5, yaw: 180.0, pitch: 0.0}
      skin: {type: player, player: Notch}      # player, texture, mirror, none
      pose: standing                           # sneaking, lying, crawling, spinning
      sitting: false
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

- **`lang/<code>/messages.yml`**, **`messages/holograms.yml`**, **`messages/npcs.yml`** and **`menus/`** contain every message and screen, in English, Spanish, Russian and Turkish. The language follows ExyliaLib's.

## Building

```
./gradlew build
```

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[GNU General Public License v3.0](LICENSE).
