package net.exylia.exyliaDisplays.hologram;

import net.exylia.lib.config.Comment;
import net.exylia.lib.config.Time;
import net.exylia.lib.hologram.HologramConfig;

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

        public Templates() {
            this(Map.of());
        }

        public Templates {
            templates = templates == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(templates));
        }
    }
}
