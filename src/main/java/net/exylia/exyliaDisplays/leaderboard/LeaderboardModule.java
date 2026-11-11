package net.exylia.exyliaDisplays.leaderboard;

import net.exylia.exyliaDisplays.ExyliaDisplays;
import net.exylia.exyliaDisplays.module.DisplayModule;
import net.exylia.lib.config.ConfigFile;
import net.exylia.lib.config.Configs;
import net.exylia.lib.leaderboard.LeaderboardSource;
import net.exylia.lib.leaderboard.LeaderboardView;
import net.exylia.lib.leaderboard.Leaderboards;
import net.exylia.lib.leaderboard.Span;
import net.exylia.lib.placeholder.Placeholders;
import net.exylia.lib.placeholder.Request;
import net.exylia.lib.text.Values;
import net.exylia.lib.util.PeriodClock;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.time.DayOfWeek;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Leaderboards: {@code leaderboards.yml}, the boards ExyliaLib keeps for it, and the
 * {@code %exyliadisplays_lb_<stat>:<period>:<place>:<field>%} placeholders a leaderboard
 * hologram (or an NPC's skin, or a scoreboard) reads them through.
 *
 * <p>Starts before holograms, so a leaderboard hologram finds its stats' names when it is drawn.
 */
public final class LeaderboardModule implements DisplayModule {

    public static final String ID = "leaderboards";

    private static LeaderboardModule instance;

    private ExyliaDisplays core;
    private ConfigFile<LeaderboardsConfig> file;
    private Leaderboards boards;
    /** The ids this plugin tracks, so a stat taken out of the file stops being kept. */
    private final Set<String> tracked = new HashSet<>();

    /** The running module, or {@code null} when it is switched off. */
    public static @Nullable LeaderboardModule get() {
        return instance;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ExyliaDisplays core) {
        instance = this;
        this.core = core;
        LeaderboardsMessages.install(Configs.define(core.getPlugin(), "messages/leaderboards", LeaderboardsMessages.class)
                .translated().load());
        file = Configs.define(core.getPlugin(), "leaderboards", LeaderboardsConfig.class).load();
        boards = Leaderboards.of(core.getPlugin());
        apply();
        Placeholders.group(core.getPlugin(), "exyliadisplays")
                .async()
                .verbatim()
                .describe("Leaderboards: lb_<stat>:<period>:<place>:name|value|taken, lb_<stat>:<period>:me:rank|value, lb_<stat>:<period>:resets")
                .add("lb", this::resolve)
                .register();
    }

    @Override
    public void reload() {
        file.reload();
        apply();
    }

    @Override
    public void disable() {
        tracked.forEach(id -> boards.untrack(id));
        tracked.clear();
        instance = null;
    }

    /** The loaded {@code leaderboards.yml}, for the settings screens. */
    public ConfigFile<LeaderboardsConfig> file() {
        return file;
    }

    public LeaderboardsConfig settings() {
        return file.get();
    }

    public Leaderboards boards() {
        return boards;
    }

    /** The stat's name as players read it, or its id when the file does not know it. */
    public String statName(String id) {
        LeaderboardsConfig.Stat stat = settings().stats().get(id);
        return stat == null ? id : stat.name();
    }

    /** The ids the file defines, in its order. */
    public List<String> statIds() {
        return List.copyOf(settings().stats().keySet());
    }

    /** Tracks what the file says, and stops tracking what it no longer does. */
    private void apply() {
        LeaderboardsConfig settings = settings();
        DayOfWeek weekStart;
        try {
            weekStart = DayOfWeek.valueOf(settings.weekStartsOn().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            core.getDebug().warn("leaderboards.yml: week-starts-on " + settings.weekStartsOn() + " is not a day; using MONDAY.");
            weekStart = DayOfWeek.MONDAY;
        }
        boards.clock(PeriodClock.of(settings.timeZone(), settings.dayStartsAt(), weekStart));
        boards.sampleEvery(settings.sampleEvery());
        boards.sortEvery(settings.sortEvery());
        Set<String> now = new HashSet<>();
        settings.stats().forEach((id, stat) -> {
            LeaderboardSource source = source(id, stat);
            if (source == null) return;
            try {
                boards.track(id, source);
                now.add(id);
            } catch (IllegalArgumentException e) {
                core.getDebug().warn("leaderboards.yml: " + id + ": " + e.getMessage());
            }
        });
        for (String id : tracked) {
            if (!now.contains(id)) boards.untrack(id);
        }
        tracked.clear();
        tracked.addAll(now);
        core.getDebug().log("Leaderboards: " + tracked.size());
    }

    /** What a stat's {@code source} names, or {@code null} (reported) when it names nothing. */
    private @Nullable LeaderboardSource source(String id, LeaderboardsConfig.Stat stat) {
        LeaderboardSource source = parse(stat.source().trim());
        if (source == null) {
            core.getDebug().warn("leaderboards.yml: " + id + " has a source nothing reads: '" + stat.source() + "'.");
            return null;
        }
        return stat.perServer() ? source.perServer() : source;
    }

    /** Reads a source as {@code leaderboards.yml} writes it. */
    static @Nullable LeaderboardSource parse(String written) {
        String lower = written.toLowerCase(Locale.ROOT);
        if (lower.equals("playtime")) return LeaderboardSource.playtime();
        if (lower.equals("balance")) return LeaderboardSource.balance();
        if (lower.startsWith("balance:")) return LeaderboardSource.balance(written.substring(8).trim());
        if (lower.startsWith("placeholder:")) {
            String placeholder = written.substring(12).trim();
            return placeholder.isEmpty() ? null : LeaderboardSource.placeholder(placeholder);
        }
        if (!lower.startsWith("statistic:")) return null;
        String[] parts = written.substring(10).trim().split(":", 2);
        Statistic statistic;
        try {
            statistic = Statistic.valueOf(parts[0].trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (parts.length == 1) {
            return statistic.getType() == Statistic.Type.UNTYPED ? LeaderboardSource.statistic(statistic) : null;
        }
        String sub = parts[1].trim().toUpperCase(Locale.ROOT);
        if (statistic.getType() == Statistic.Type.ENTITY) {
            try {
                return LeaderboardSource.statistic(statistic, EntityType.valueOf(sub));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        Material material = Material.matchMaterial(sub);
        return material == null || statistic.getType() == Statistic.Type.UNTYPED
                ? null : LeaderboardSource.statistic(statistic, material);
    }

    /** A period id as boards and files write it: {@code all-time}, {@code weekly}... */
    public static Optional<Span> span(String id) {
        if (id == null) return Optional.empty();
        String key = id.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return Optional.ofNullable(switch (key) {
            case "all-time", "alltime", "all", "total" -> Span.ALL_TIME;
            case "daily", "day", "today" -> Span.DAILY;
            case "weekly", "week" -> Span.WEEKLY;
            case "monthly", "month" -> Span.MONTHLY;
            case "yearly", "year" -> Span.YEARLY;
            default -> null;
        });
    }

    /** The id a file writes for a period. */
    public static String spanId(Span span) {
        return span.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** A period's name as players read it. */
    public static String spanName(String id) {
        LeaderboardsMessages words = LeaderboardsMessages.get();
        return span(id).map(span -> switch (span) {
            case ALL_TIME -> words.allTime();
            case DAILY -> words.daily();
            case WEEKLY -> words.weekly();
            case MONTHLY -> words.monthly();
            case YEARLY -> words.yearly();
        }).orElse(id);
    }

    /**
     * {@code <stat>:<period>:<place>:name|value|taken}, {@code <stat>:<period>:me:rank|value} and
     * {@code <stat>:<period>:resets}. Memory only, from any thread.
     */
    private Object resolve(Request request) {
        String[] parts = request.arg(0, "").split(":");
        if (parts.length < 3) return null;
        Optional<Span> span = span(parts[1]);
        if (span.isEmpty()) return null;
        LeaderboardView view = boards.view(parts[0], span.get());
        String format = Optional.ofNullable(settings().stats().get(parts[0]))
                .map(LeaderboardsConfig.Stat::format).orElse("");
        if (parts[2].equals("resets")) {
            LeaderboardsMessages words = LeaderboardsMessages.get();
            return view.remaining()
                    .map(left -> Values.of("time", net.exylia.lib.text.TimeTokens.countdown(left.toSeconds())).apply(words.resetsIn()))
                    .orElse(words.resetsNever());
        }
        if (parts.length < 4) return null;
        String field = parts[3];
        if (parts[2].equals("me")) {
            if (!request.hasViewer()) return null;
            java.util.UUID viewer = request.requireViewer().getUniqueId();
            return switch (field) {
                case "rank" -> view.rankOf(viewer).orElse(0);
                case "value" -> LeaderboardView.format(view.valueOf(viewer).orElse(0), format);
                default -> null;
            };
        }
        int rank;
        try {
            rank = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return null;
        }
        Optional<LeaderboardView.Entry> entry = view.at(rank);
        return switch (field) {
            case "taken" -> entry.isPresent() ? 1 : 0;
            case "name" -> entry.map(LeaderboardView.Entry::name).orElse("");
            case "value" -> entry.map(e -> e.formatted(format)).orElse("");
            default -> null;
        };
    }

    /** The stats a list names that the file does not define. */
    public List<String> unknown(List<String> stats) {
        Map<String, LeaderboardsConfig.Stat> known = settings().stats();
        return stats.stream().filter(id -> !known.containsKey(id)).toList();
    }
}
