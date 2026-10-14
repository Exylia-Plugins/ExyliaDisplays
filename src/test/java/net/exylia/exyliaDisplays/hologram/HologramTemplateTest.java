package net.exylia.exyliaDisplays.hologram;

import net.exylia.exyliaDisplays.common.store.Position;
import net.exylia.lib.config.Configs;
import net.exylia.lib.hologram.HologramConfig;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A hologram's look kept as a template, written to templates.yml and worn by another hologram. */
class HologramTemplateTest {

    @TempDir
    Path folder;

    @AfterEach
    void release() {
        Configs.releaseAll();
    }

    private Plugin plugin() {
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "HologramTemplateTest";
                    case "getDataFolder" -> folder.toFile();
                    case "getLogger" -> Logger.getLogger("HologramTemplateTest");
                    case "isEnabled" -> false;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    @Test
    @DisplayName("a template reads back the same, and dressing a hologram keeps where it stands and who sees it")
    void roundTrip() {
        HologramConfig.Properties look = new HologramConfig.Properties("FIXED", "LEFT", 2, 2, 2, false, true,
                150, 0.4, 200, false, "#66000000", 15, false);
        StoredHologram source = new StoredHologram()
                .withLines(List.of("<size:2><shine>{primary}&lSHOP", "<item:DIAMOND><spin>"))
                .withPages(List.of(new HologramConfig.Page(List.of("page two"), List.of("[PAGE] next"))))
                .withPageInterval(100)
                .withAppearance(look)
                .withRefresh(new HologramConfig.Refresh(40, true))
                .withClick(new HologramConfig.Click(List.of("right: [SERVER] lobby"), 2, 500, 1.08));
        HologramTemplate template = HologramTemplate.of(source);

        Configs.define(plugin(), "templates", HologramTemplate.Templates.class).load()
                .update(current -> new HologramTemplate.Templates(Map.of("shop", template)));
        Configs.releaseAll();
        HologramTemplate read = Configs.define(plugin(), "templates", HologramTemplate.Templates.class).load().get()
                .templates().get("shop");
        assertEquals(template, read);

        UUID ana = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        StoredHologram target = StoredHologram.at(new Position("world", 1, 2, 3), List.of("old"), 16)
                .withPermission("vip.see")
                .withVisibility(new StoredHologram.Visibility().withMode("manual").show(List.of(ana)));
        StoredHologram dressed = read.applyTo(target);
        assertEquals(source.lines(), dressed.lines());
        assertEquals(source.pages(), dressed.pages());
        assertEquals(100, dressed.pageInterval());
        assertEquals(look, dressed.appearance());
        assertEquals(source.refresh(), dressed.refresh());
        assertEquals(source.click(), dressed.click());
        assertEquals(target.location(), dressed.location());
        assertEquals("vip.see", dressed.permission());
        assertEquals(16, dressed.viewDistance());
        assertEquals(target.visibility(), dressed.visibility());
    }
}
