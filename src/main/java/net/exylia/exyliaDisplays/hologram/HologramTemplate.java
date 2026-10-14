package net.exylia.exyliaDisplays.hologram;

import net.exylia.lib.config.Comment;
import net.exylia.lib.config.Migration;
import net.exylia.lib.config.Time;
import net.exylia.lib.hologram.HologramConfig;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A hologram's look kept under a name, to create holograms from or to dress one up with: its
 * lines and pages with their tags, how it is drawn, how often it refreshes and what a click does.
 * Where it stands, who sees it and what it rides stay with each hologram.
 *
 * @param lines        the first page's lines, with their tags
 * @param pages        the pages after the first
 * @param pageInterval ticks before a viewer is turned to the next page; 0 never
 * @param appearance   how it is drawn
 * @param refresh      how often its placeholders refresh
 * @param click        what clicking it does
 */
public record HologramTemplate(
        @Comment("The lines, top first, with their tags, as in holograms.yml.")
        List<String> lines,
        List<HologramConfig.Page> pages,
        @Time(Time.Unit.TICKS) long pageInterval,
        HologramConfig.Properties appearance,
        HologramConfig.Refresh refresh,
        HologramConfig.Click click) {

    public HologramTemplate() {
        this(List.of(), List.of(), 0, new HologramConfig.Properties(), new HologramConfig.Refresh(),
                new HologramConfig.Click());
    }

    public HologramTemplate {
        lines = lines == null ? List.of() : List.copyOf(lines);
        pages = pages == null ? List.of() : List.copyOf(pages);
        pageInterval = Math.max(0, pageInterval);
        if (appearance == null) appearance = new HologramConfig.Properties();
        if (refresh == null) refresh = new HologramConfig.Refresh();
        if (click == null) click = new HologramConfig.Click();
    }

    /** What of a hologram a template keeps. */
    public static HologramTemplate of(StoredHologram hologram) {
        return new HologramTemplate(hologram.lines(), hologram.pages(), hologram.pageInterval(),
                hologram.appearance(), hologram.refresh(), hologram.click());
    }

    /** The hologram dressed as this template, where it stands and with whoever sees it. */
    public StoredHologram applyTo(StoredHologram hologram) {
        return hologram.withLines(lines).withPages(pages).withPageInterval(pageInterval)
                .withAppearance(appearance).withRefresh(refresh).withClick(click);
    }

    /**
     * {@code templates.yml}: every hologram template, by name.
     *
     * @param templates the templates, in the order they were saved
     */
    @Comment("Hologram templates, by name: a hologram's lines, pages, appearance, refresh and click,")
    @Comment("without where it stands or who sees it.")
    @Comment("")
    @Comment("/holo <name> savetemplate <template> saves one, /holo <name> applytemplate <template>")
    @Comment("dresses a hologram with one, /holoadmin create <name> --template <template> starts from one.")
    public record Templates(
            @Comment("One block per template. The name is what the commands take.")
            Map<String, HologramTemplate> templates) {

        /**
         * Gives a file written before templates shipped, still holding none, the defaults once: an
         * empty {@code templates} is taken out so it is read as missing. One with templates is left alone.
         */
        public static final Migration SEED_WHEN_EMPTY = data -> {
            Object current = data.get("templates");
            boolean empty = current instanceof ConfigurationSection section ? section.getKeys(false).isEmpty()
                    : current instanceof Map<?, ?> map && map.isEmpty();
            if (empty) data.remove("templates");
        };

        /** A new install's templates: between them, every line tag and every setting at least once. */
        public Templates() {
            this(defaults());
        }

        public Templates {
            templates = templates == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(templates));
        }

        static Map<String, HologramTemplate> defaults() {
            Map<String, HologramTemplate> templates = new LinkedHashMap<>();
            HologramConfig.Properties plain = new HologramConfig.Properties();

            // Three pages that turn on their own every 10s; a click turns them by hand.
            templates.put("welcome", new HologramTemplate(List.of(
                    "<head:Notch><size:0.9><bob:0.06,period=4>",
                    "<size:2.2><shine:highlight,speed=14,pause=3,width=4>{primary}&lWELCOME",
                    "<flow:3>{secondary}to the server, {highlight}%player_name%",
                    "<size:0.5> ",
                    "{warning}➥ {letters}Click for the next page"), List.of(
                    new HologramConfig.Page(List.of(
                            "<size:1.6>{primary}&lRULES",
                            "<gap:0.1>{letters_black}▎ {letters}Be kind, play fair",
                            "{letters_black}▎ {letters}No griefing, no cheats",
                            "{letters_black}▎ {letters}Have fun out there",
                            "<size:0.5> ",
                            "{warning}➥ {letters}Left-click back {letters_black}· {letters}right-click on"), List.of(
                            "left: [PAGE] previous",
                            "right: [PAGE] next")),
                    new HologramConfig.Page(List.of(
                            "<size:1.6>{primary}&lGET STARTED",
                            "<typewriter:30>{letters}Type {highlight}/spawn {letters}to come back here",
                            "<typewriter:30>{letters}and {highlight}/help {letters}for the rest",
                            "<size:0.5> ",
                            "{warning}➥ {letters}Click to start over"), List.of(
                            "[PAGE] first"))),
                    200, plain, new HologramConfig.Refresh(),
                    new HologramConfig.Click(List.of("[PAGE] next"), 2.5, 300, 1.05, HologramConfig.Click.STYLE_BUTTON,
                            "{primary}", "{secondary}", "ITEM_BOOK_PAGE_TURN;0.8;1.0", Map.of())));

            // One panel per destination: each button line runs its own commands.
            templates.put("kiosk", new HologramTemplate(List.of(
                    "<item:COMPASS><size:0.8><spin:60><glow:#8a51c4>",
                    "<size:1.6>{primary}&lTRAVEL",
                    "<opacity:200>{letters_black}Pick a destination",
                    "<gap:0.2><size:1.1><button:spawn>{success}&l⌂ SPAWN",
                    "<size:1.1><button:shop>{highlight}&l✦ SHOP",
                    "<size:1.1><button:arena>{error}&l⚔ ARENA"), List.of(), 0, plain, new HologramConfig.Refresh(),
                    new HologramConfig.Click(List.of("[ACTION_BAR] {letters}Pick one of the buttons"), 2.0, 400, 1.1,
                            HologramConfig.Click.STYLE_BUTTON, "{primary}", "{secondary}", "UI_BUTTON_CLICK;0.6;1.4",
                            Map.of(
                                    "spawn", List.of("spawn"),
                                    "shop", List.of("warp shop"),
                                    "arena", List.of(
                                            "left: [MESSAGE] {letters}The arena opens at {highlight}8 PM{letters}.",
                                            "right: warp arena")))));

            // Live numbers on a dark panel, refreshed every second.
            templates.put("status", new HologramTemplate(List.of(
                    "<size:0.9><blink:1.2>{error}● {letters}LIVE",
                    "<size:1.6>{primary}&lSERVER STATUS",
                    "<gap:0.1>{letters}Online {letters_black}» {info}%server_online%{letters_black}/{info}%server_max_players%",
                    "{letters}TPS {letters_black}» {success}%server_tps_1%",
                    "{letters}Uptime {letters_black}» {info}%server_uptime% ⌚",
                    "<gap:0.1><marquee:30,speed=5>{letters_black}Vote daily for rewards · Join our Discord · Season II starts soon ·"),
                    List.of(), 0,
                    new HologramConfig.Properties("CENTER", "CENTER", 1, 1, 1, true, false, 200, 0.28, 255,
                            false, "#59000000", -1, false),
                    new HologramConfig.Refresh(20, true), new HologramConfig.Click()));

            // A prize on a turning beacon: it sways, floats, breathes and glows.
            templates.put("showcase", new HologramTemplate(List.of(
                    "<size:1.5><rainbow:5>&lLEGENDARY BLADE",
                    "{letters}Damage {letters_black}» {info}12 {letters_black}· {letters}Speed {letters_black}» {info}1.6",
                    "<item:NETHERITE_SWORD><size:1.4><sway:25,period=5><bob:0.1,period=4><glow:#ffd700>",
                    "<block:BEACON><size:0.6><spin:45><pulse:0.05,period=3>"), List.of(), 0, plain,
                    new HologramConfig.Refresh(), new HologramConfig.Click()));

            // Readable from across the map: it grows with the viewer's distance and shows through walls.
            templates.put("landmark", new HologramTemplate(List.of(
                    "<block:END_PORTAL_FRAME><size:0.8><spin:30>",
                    "<size:2.6>{primary}&lSPAWN",
                    "<seethrough><opacity:150>{letters_black}Seen through walls",
                    "<autoscale:4,24,2><fade:4>{muted}/warp spawn"), List.of(), 0,
                    new HologramConfig.Properties("CENTER", "CENTER", 1, 1, 1, true, false, 200, 0.25, 255,
                            false, "#00000000", 15, false, new HologramConfig.DistanceScale(true, 10, 48, 1, 3)),
                    new HologramConfig.Refresh(), new HologramConfig.Click()));

            // Each viewer reads their own lines: by permission, by level and by distance.
            templates.put("vault", new HologramTemplate(List.of(
                    "<item:AMETHYST_SHARD><bob><glow:#b48fd9>",
                    "<size:1.6><pulse:0.04>{secondary}&lTHE VAULT",
                    "<perm:vault.access>{success}✔ {letters}Welcome back, {highlight}%player_name%",
                    "<perm:!vault.access><opacity:180>{letters_black}Members only",
                    "<if:\"%player_level% >= 30\">{info}Your level opens the inner room",
                    "<if:\"%player_level% < 30\">{letters_black}Reach level {highlight}30 {letters_black}for the inner room",
                    "<gap:0.1><range:5><scramble:24>{letters}The code is whispered, never written"), List.of(), 0, plain,
                    new HologramConfig.Refresh(), new HologramConfig.Click()));

            // An announcement that keeps moving; a click sends a title. No panel, only the hover growth.
            templates.put("event", new HologramTemplate(List.of(
                    "<size:0.9><blink:1>{error}● {letters}LIVE EVENT",
                    "<size:2><rainbow:8>&lSTAR FALL",
                    "<frames:2.5>{letters}Double drops all weekend||{letters}Meteors land at spawn||{letters}Bring a friend",
                    "<fade:5>{letters_black}Saturday · {highlight}8 PM",
                    "<gap:0.12><typewriter:20>{warning}➥ {letters}Click for a reminder"), List.of(), 0, plain,
                    new HologramConfig.Refresh(),
                    new HologramConfig.Click(List.of(
                            "[TITLE] {primary}&lSTAR FALL;{letters}Saturday at 8 PM;0.3;2.5;0.5",
                            "[SOUND] BLOCK_AMETHYST_BLOCK_CHIME;1;1.2"), 2.0, 2000, 1.05,
                            HologramConfig.Click.STYLE_NONE, "{primary}", "{secondary}", "", Map.of())));

            // A flat sign on a wall: fixed facing, left-aligned, no shadow, a tinted header pill.
            templates.put("signpost", new HologramTemplate(List.of(
                    "<size:1.4><align:center><background:#998a51c4>{letters}&l NORTH WING ",
                    "<gap:0.1>{letters_black}▎ {letters}Library",
                    "{letters_black}▎ {letters}Observatory",
                    "{letters_black}▎ {letters}Gardens",
                    "<gap:0.1><width:120><opacity:200>{muted}Follow the purple lanterns past the fountain.",
                    "<billboard:center><shadow><brightness:15>{warning}➥ {letters}This line turns to you"),
                    List.of(), 0,
                    new HologramConfig.Properties("FIXED", "LEFT", 1, 1, 1, false, false, 160, 0.25, 255,
                            false, "#59000000", -1, false),
                    new HologramConfig.Refresh(), new HologramConfig.Click()));
            return templates;
        }
    }
}
