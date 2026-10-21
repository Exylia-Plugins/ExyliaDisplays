package net.exylia.exyliaDisplays.demo;

import net.exylia.exyliaDisplays.common.store.Place;
import net.exylia.exyliaDisplays.common.store.Position;
import net.exylia.exyliaDisplays.hologram.HologramModule;
import net.exylia.exyliaDisplays.hologram.StoredHologram;
import net.exylia.exyliaDisplays.npc.NpcModule;
import net.exylia.exyliaDisplays.npc.StoredNpc;
import net.exylia.lib.npc.NpcAnimations;
import net.exylia.lib.npc.NpcLook;
import net.exylia.lib.npc.NpcPose;
import org.bukkit.Location;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * A showcase of holograms and NPCs built around a player, for screenshots and first looks.
 *
 * <p>Everything it builds is an ordinary entry of holograms.yml and npcs.yml named {@code demo-*},
 * so the screens, commands and web editor work on it, and {@link #clear} removes exactly those.
 * Three scenes, each framed on its own from where the player stood: a hologram ahead, a guide that
 * speaks on the right, and a cast of five in an arc behind.
 */
public final class DemoShowcase {

    public static final String PREFIX = "demo-";
    public static final String HOLOGRAM = PREFIX + "welcome";
    public static final String GUIDE = PREFIX + "guide";

    /**
     * Signed skins from mineskin.org (verified against Mojang's keys), all distinct: the guide's
     * first, then the cast's. Used where no account name is given.
     */
    static final List<StoredNpc.Skin> SKINS = List.of(
            // guide (adventurer, goggles)
            StoredNpc.Skin.texture(
                    "ewogICJ0aW1lc3RhbXAiIDogMTcyMzc0MTkxNjE4MCwKICAicHJvZmlsZUlkIiA6ICIyNGFjYWZhMmVlNDA0ZGMwYmRjMjViNWVjNjgxZmRjZiIsCiAgInByb2ZpbGVOYW1lIiA6ICJXaWxsZXJtaW5hRDM0dGgiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTZiOWU4NDViNWJiMTZiNzRiZjdjZjNjZjQ4MTE2YzEzMTFjNDA2YzRmNzhhNjk2ZDcxMDQ5NTlkMTZjOTY2NCIKICAgIH0KICB9Cn0=",
                    "ELywC8yMnGOSqd7riye2mr0SABOX5ATA1XIg/2CovalWfAbIO4pWVaDWv2hEmDt7NveTEccAojNKN6Op3VYlwqKtgx4eIxbksAGbdukL3KhEqVrhsrDz8/SX5YqSeHhelLJCyOsyfCj2zmcj15akh1zW/561LEUnM4uF92+VUT8zwdl5YaS3+F8jhU8jwqp/W7ErQEzOhUxi/XFv/aU7qFf7jm8izc1GzTr6wLCvBObnj290eRjYqQUvH0rlpSXJhtZmCuHbSw7sZ0nT1Kh3B4rQTPdExM98zcnXVcTAP5Z6wfG3St0PTBEVSnLelJpVqvRYwUQPDOHjdwgEuvx1mVxxFtrXLVVFKUCoVj8zVbgODIAvO3a/fHVTQ8z+7TSYzVj+K22LFt2bFk4ppt3ivMbMhfGEWjCC+3AXlgQbJwocWmqkP+tBgRRx4IpSyY+WJbpj2wMEmQlo8xR2g3RGoVjr/LKe6dOsfEVrYX5L8dUDJb3kfPi+xF6eQM4/nyD3FwwIpL+UUYiBfMyhCEp5MwV9vfy+vCC1TXuc/ELvdKA7+2XZ5hY1grkGOADLPJ7HKn29v8m05PX0dNbBLXWPwmLk98niLW166DTErCSQ9cgfZW/Jlh58vEGZTVEsL1R89uZKUaOQmBJKWeacyUzpdVR20G5+N3L0xuvpx5/EaLw="),
            // blue and gold mage
            StoredNpc.Skin.texture(
                    "ewogICJ0aW1lc3RhbXAiIDogMTc5MTQ2Mjc4MTczNSwKICAicHJvZmlsZUlkIiA6ICI4ZGQ5OWEzMDEwMDA0NTRiYmYwNGU5Mjk3NGIzYWNmMCIsCiAgInByb2ZpbGVOYW1lIiA6ICJQeXIwdGVjaG5payIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS84MDkyZjlhNWE1MGI1ZDU2MTVmYTg3NDA1ZWVmNzc3ZWMyZDBkM2U3M2ZlMjMxMGUxMjkwMTRmNTc5ZjIwNWY0IgogICAgfQogIH0KfQ==",
                    "upSkTOjiUOTfsyXEebtIH7tuc77chstzq0JZeZSlBvImzyPEs+z0TsYU+XsmZ6SGMeVNxra4U2pwJiLlIuODp+PeJxM2dru8rp2+UNogEVqLUQnpyb1MpHT+xaAqJ3WYisFwq4+1CyQ4OWcrmtG4UE+caCdJwCvEaajurFPij9ZuzazDM0MJDAmdcn5e73QswvdgbdI68sEbdmJN0qE7loy68DMQIPWdZXdTRPqy1QK2xPsR5QRLOP5IdhryOrFj0Zaq/Qjfr4En56c9WTT1tnhMgnl4PyrIjMFOfYmvurIYs4i1pzYTcf8yYiciekghaPULt+4cuyw9r/JZI6VHeMl76PUOeF1cYSUbhywDfzXLUKV72z8wgwmcWiU1svy9D4m2ymbHWgpFzzGEoB0oyT/lSB5n1NwPVmblOOHPkI+5E3/WgZAHq+DGyyP6vecqub0BatSmElT3V9H0rpsJfWqllOVeVKfLI2kms03ZGJwxisJBfO00w3mNksV+9iB+nvF3irtEDxIC4/thCMCX4PrHN0WWKyA4rl9lN+AsnnW1cJBQo5w4NS2bGVBgwRO32ehNLM+OBt6ijbNBY+p8I1y+Mus98TEPSLUJgHMxPdS2b+Et2fl+B3X/3zpTpFBBKdo9yDY/jA/H5ZatX4AyDUlIMemFp4wAfLbb5GT0wnI="),
            // red-haired ranger
            StoredNpc.Skin.texture(
                    "ewogICJ0aW1lc3RhbXAiIDogMTc5MjU5Mjk0MzAxNCwKICAicHJvZmlsZUlkIiA6ICI2YjllM2Q0MDRiZmY0MTEyODJiY2VkOTAzY2M3ZDJmZiIsCiAgInByb2ZpbGVOYW1lIiA6ICJsdWgyOSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8zMzAxNTdiNTAyZGU2NDgzYzI0MDBjNjJhNGFkZWRlNmFlMGM3MTJkNzQyMmUwNzFmODRiMDdlNmUzZjhhZThkIgogICAgfQogIH0KfQ==",
                    "gyDYN0r9b568I1HjjlQQN6SlwYF5JGoNqrHJxwH8siEdHnfZY11g1EJBM8ry50Of0qkEvSGQ0P7s5JuSw5WrLLiUEdq+u5ZBwEuMxc/xho6npsfwyf+ZzsXLf/eNzMuBXXbGBdp442DZk1fcE/WfPI5ebIAI0/TB4xA7HAgN8hWBbtStWWRHvfc5cONMKdKlruGQkoPZwlXPliQlIy6+Q3zxbyJ7bG7U96UO7nB/SGHBdunbiBAJs1ZPBcqTYdkS/cA/kxvQUdBAu2nITpwQIxlR98Nx9/vujdJhU+UwBHqFtL0EasQ/0c3SteVhMl6Wv63bWFrLo7zg3fOgy2nQ7N/zo1M7CZpqz95JVVONE03a3Ra7zhAXwsPhV8fRVcWSc7UJFFmpoE2RxJGf0Ue16HxiCn3N63yLMxn4LAxU2TBdqG3Zzruk2qoAriJc4zsGKIUfBVXrgaT0DwoXHkHln0Df9+lOiq82I9dgKez+1LeZlsFZp/GZr06xpDMlpOEjAvD+oNjdbIuMhXDDE7LWCH943J5yWzfWMK814ld0hf+7TaOaw4kDEH/KIAdFBT74EwNMYg96ApspfqRsK3+dvPt9Rl7Wo8Cfb5uDCjnhvbFRn6wp1ewXvF0u2B8lwmyHIYzW4veYM/kSn38LbOJvI6a3vh3RfaoVLEklMZCPzgs="),
            // blond squire
            StoredNpc.Skin.texture(
                    "ewogICJ0aW1lc3RhbXAiIDogMTc5MjU5MzAyMDUxMCwKICAicHJvZmlsZUlkIiA6ICI4NzA1MzkxMDBlOGQ0MzU2YjJiYjlmZGI1NjFhOGU3ZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJfcmVncmV0dHQiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzc1YjEyMGU2M2YxZWMzZDI3ZGFiNWE1NWY1NTI0ODNhZWEwMmNlMWU3YjU0NGYyODRiYzBkNTg1ZTgwMzM5ZCIKICAgIH0KICB9Cn0=",
                    "bw4m3dNOmTLORL2UOSqRGItAxmPH6pjeaucoW8HBZBzioJkHLFQkCK5PugDG5fQZHjvdysNImuSHEMLFrFQ0QY3DzKlsPTOJvzzoXqDu3WTr7lFt+vNhxq1JAqCIVp7zIiD9QXBvohBtlZFtmgivxUabBjLaoD3eWUdB3yiEKpxehVxI2ZSAKN7grySFOsTDltUETb7sriYPSDULpRP8TqKWH59ZKj9bpqZvjqWcLF5azo5nPcsBptkKb9iD0sFit2pmFlV/I5pDWQ9tOFPuWgd+KCcee4gS06nVVciaJQnm8hxUNH+aHUO7WYPFx4fysyhlYTt3/vt5YruyWp8jIz+b4jdVd/zeKqqEzWSUi6ENW5N5RO+nWH5UnyDcYD7m/z9ZlkhzgX2Z2wfG6v8mx2ZxFgUs26rZjEAFkuDjbTpM5WgfFcn0tq5XO/LI1VznBNRjUIlo5ZZNzlzNJu/xHyMjiFRTuTK/Fk9sCSmvS7hzErw1hxdipwpFaPU38GT+ED1HspjDy/nAFoR/R2CAEYW7PA8L/UwdPiMkuA+I9UMZMmbwKfLkq070sOr+vuxX1emOI/2fWd/xo0vZ4RYDbETD6HUe0HTKnAE4ToCR/BW+bi147j3Aq2KFvyYpha5ose3f0ZSG0hHN7tJFV7KuUxQ3G18w5QQgr5zD8bPHNm0="),
            // dark-haired traveller
            StoredNpc.Skin.texture(
                    "ewogICJ0aW1lc3RhbXAiIDogMTc4OTM1NjEzMzAwNywKICAicHJvZmlsZUlkIiA6ICI0ZmNlNWJhYjJmMjI0MTdjYTk1YWVlYzA4OTRhMTY3OSIsCiAgInByb2ZpbGVOYW1lIiA6ICJWYWF0d2FzdGFibGV0IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzM5NDI5MGI4YjAyOWNkNDg4OGYwYjMxMDM3YTJmNGU2ZjJmODFjMzM3ZTc3YzJhOTI2NTk0YjkyZjM4ZmRjYzkiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ==",
                    "s+ohlFbYxVksqZykO2Dn4iWtDXp7LAjyqfzwPzQfwwEIuUsHleRU/qE4YxlahLleR//52egIpwFi5/erLqQ6y54Pq6gAmCj6e6LxL0sKsyKIY6pNSp5NFXOslB+qPUlIzVWzU1NQJVpo/36q/+/+ZJzCd3IVJ/IjoJ7oq7wabVYHf+Ld4J3faHe0rl2R3hNdwf71xLuK3Bc9oG5KOjB2PdrQUcu0YLE4mfcxq0WcPeEc0kTAYSjzPcxlMh5umkqexSM+IW01C632lBUaazb6im9u+MOXJM9N+u+7sPgTG0yhtHnuKk4EaCld3I2Kt1k/PKLkR/qDbbazDE2+YcYiqRNWhZqtw6vWnrvi8qRCe2oyifYjLFE8NWzwH7cHJPuAiiykkxeWX/6A6D6LZ3s8iKM+YpQCROwv/ljCV8GCX5pKKFZosgi39i9QmscaaT5vb956vjUCRz+M7uqYgNxtw3dXxXsIE6HzRQd8d++dEnd5fBNtxvPe9QjSKP7ksZ5ks4k7AfZbpwvvN9mzS5W7QxWdde52Rdlq8/NQ8BLyaZxwmIF485/TtYqIzRXW5tQY6tnKMoM+x4rTrxb3Y7r5ts70rqOrfnq+2uMx766PkE4MTQTcZJRJGmrUwzjOHZSQeCOyM2ykRZWyQfpTaWytZrx8vzXm/7+RkkJkNwnt4RA="),
            // red sweater
            StoredNpc.Skin.texture(
                    "ewogICJ0aW1lc3RhbXAiIDogMTc5MjU5MzA0NTA4OCwKICAicHJvZmlsZUlkIiA6ICIzN2VhNTQ5MTE3MTU0NDgzYmY0N2VmNGM3MzMwMWIzYiIsCiAgInByb2ZpbGVOYW1lIiA6ICJSeXV6bG4iLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzhhZDgxN2I5ZGVlNzg5ZTBjMGM3NTkzZjQzNDg4YTc2Y2Q0ZmU5OGM3NzhhMWVhMjM4ZDY2ZDgzZDkzYzIxZiIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9",
                    "HrxS4V2TOMycMopkuT7PJ7iXimtxi88pYUIk/bLcIRvZRD40dUCbeQmckz8Zs9UoTvMcCqpP370/uH/XUj2Y8GZdb0a3ghPV06A2RvwTgQrMGXbSwl1sPeNT/VQGA2BCLJ+oFHLmwL3e3eFkaV+v3MJzdW6QqOF4lRT45nfPg63vXnenCR0NUOlsjaEllMXMHmgFi9FW5vVZtYAUBGCbTpXD3C0uyn35hupSsdli/SJyoezoadXZKK3ucLzq7G4eb/hETdESBFIhdt5YmBcGd4N2ohC3MPiOLslmSN2gPpkn1C6TocFijcdbn+56YjMHj8RKXExnE3jwPz2J1+yWruCCUiDueLsKei1NGO+9vNrOHo6W+WTR0l73kDvyts3hGIpgfI66vh6v8bJ7dIUEWIVjAhyGGvWAUTsDGmP9Mn5ZHVCpfHf2eiio9RfZvGTYprDW7nsLxFIzXKc9DnIGic2Kf2sqLfrT870QbX6V+BGTtX0moOzkpGWGngfuO+IdC/ZAMzbjNN4HIoEmZehK0atKJpwnWgd+MGdQDT13Z/LJ4CpixNZe+1kJ/nlzW9Fn6gQrLCYRbyiIGUWEJk8tI2t0yAD7zHwQBx0Rq4DTMWT52ZFYK418voie/I28KQ6EY/Go5PgjKSL6Z5Q1Gg1m7qO33r2PUkr9BcvOgQ1QIcY=")    );

    /** How many skins a seed wears: the guide and the cast of five. */
    public static final int SKIN_COUNT = 6;

    private DemoShowcase() {
        throw new AssertionError("No instances.");
    }

    /**
     * Builds the showcase around a spot, replacing an earlier one.
     *
     * @param at    where the player stands and looks
     * @param skins account skins to wear in order (guide first); the built-in ones fill the rest
     */
    public static void seed(Location at, List<StoredNpc.Skin> skins) {
        clear();
        String world = at.getWorld().getName();
        double yaw = Math.toRadians(at.getYaw());
        // Forward and right along the ground: yaw 0 looks south (+z), the right hand is west.
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double x = at.getX(), y = at.getY(), z = at.getZ();

        // Lines of every size: five blocks ahead, read from where the player stands.
        Location ahead = new Location(at.getWorld(), x + fx * 5, y + 0.6, z + fz * 5, at.getYaw(), 0);
        HologramModule.get().store().create(HOLOGRAM, StoredHologram.at(Position.facing(ahead), List.of(
                "<block:BEACON><spin:60><bob:0.08,period=4><glow:#8a51c4>",
                "<size:0.4> ",
                "<size:2.2><shine:highlight,speed=14,pause=2.5,width=4>{primary}&lWELCOME TO EXYLIA",
                "<size:1.1><opacity:220>{letters}Your next adventure starts here",
                "<size:0.5> ",
                "<frames:3>{letters_black}▎ {letters}Survival {letters_black}» {success}Online||"
                        + "{letters_black}▎ {letters}Skyblock {letters_black}» {success}Online||"
                        + "{letters_black}▎ {letters}Events {letters_black}» {highlight}Tonight",
                "<size:0.9><flow:6>{secondary}&l✦ SEASON IV ✦",
                "<typewriter:30>{letters_black}Step closer to begin"), 48.0));

        // NPCs that speak: a guide on the right, facing back, who answers a right-click.
        Place guide = new Place(world, x + fx * 2 + rx * 6, y, z + fz * 2 + rz * 6, 0, 0).facing(x, z);
        NpcModule.get().store().create(GUIDE, npc(guide, skin(skins, 0), "#8a51c4", List.of(
                        "<size:1.3><shine:highlight,pause=4>{primary}&lGUIDE",
                        "<opacity:200>{letters_black}Right-click to say hello"))
                .withEquipment(Map.of("hand", "COMPASS"))
                .withInteractions(new StoredNpc.Interactions().withActions(List.of(
                        "right: [SOUND] ENTITY_VILLAGER_CELEBRATE;0.6;1.2",
                        "right: [ANIMATE] wave",
                        "right: [SAY] {letters}Welcome to {primary}Exylia{letters}, {highlight}%player_name%{letters}!;15s",
                        "left: [SAY] {letters}Need a hand? {highlight}Right-click {letters}me.;4s")))
                .withAnimations(new NpcAnimations("flinch", "", "nod", Duration.ofSeconds(20), "wave", 6,
                        Duration.ofSeconds(30))));

        // A cast of NPCs: five in an arc behind, six blocks away, every one facing the spot.
        record Member(String name, String title, String role, String glow, NpcPose pose, boolean sitting,
                      double scale, Map<String, String> equipment, String idle) { }
        List<Member> cast = List.of(
                new Member("aurora", "{info}&lAURORA", "Mage", "#59a4ff", NpcPose.STANDING, false, 1.0,
                        Map.of("hand", "BLAZE_ROD"), "nod"),
                new Member("ember", "{accent}&lEMBER", "Duelist", "#ff6b9d", NpcPose.STANDING, false, 1.0,
                        Map.of("hand", "NETHERITE_SWORD"), "punch"),
                new Member("solar", "<shine:highlight,pause=3>{highlight}&lSOLAR", "Champion", "#ffd700",
                        NpcPose.STANDING, false, 1.15, Map.of("head", "GOLDEN_HELMET", "hand", "TRIDENT"), "cheer"),
                new Member("nova", "{success}&lNOVA", "Scout", "#8fffc1", NpcPose.SNEAKING, false, 1.0,
                        Map.of("hand", "CROSSBOW"), ""),
                new Member("echo", "{secondary}&lECHO", "Scholar", "#aa76de", NpcPose.STANDING, true, 1.0,
                        Map.of("hand", "WRITABLE_BOOK"), "nod"));
        for (int i = 0; i < cast.size(); i++) {
            Member member = cast.get(i);
            double angle = Math.toRadians(-36 + i * 18);
            // Behind the player: the backward direction turned by the angle.
            double bx = -fx * Math.cos(angle) + rx * Math.sin(angle);
            double bz = -fz * Math.cos(angle) + rz * Math.sin(angle);
            Place spot = new Place(world, x + bx * 6, y, z + bz * 6, 0, 0).facing(x, z);
            NpcModule.get().store().create(PREFIX + member.name(), npc(spot, skin(skins, i + 1), member.glow(), List.of(
                            "<size:1.2>" + member.title(),
                            "<opacity:200>{letters_black}" + member.role()))
                    .withPose(member.pose())
                    .withSitting(member.sitting())
                    .withScale(member.scale())
                    .withEquipment(member.equipment())
                    .withAnimations(new NpcAnimations("flinch", "wave", member.idle(), Duration.ofSeconds(14 + i * 3),
                            "", 0, Duration.ZERO)));
        }
    }

    /** Removes every hologram and NPC named {@code demo-*}, and nothing else. */
    public static Removed clear() {
        int holograms = 0, npcs = 0;
        for (String name : HologramModule.get().store().names()) {
            if (name.startsWith(PREFIX) && HologramModule.get().store().delete(name)) holograms++;
        }
        for (String name : NpcModule.get().store().names()) {
            if (name.startsWith(PREFIX) && NpcModule.get().store().delete(name)) npcs++;
        }
        return new Removed(holograms, npcs);
    }

    /** What {@link #clear} removed. */
    public record Removed(int holograms, int npcs) { }

    /** The skin at a place in the order: the one given, else the built-in one. */
    static StoredNpc.Skin skin(List<StoredNpc.Skin> given, int index) {
        return index < given.size() ? given.get(index) : SKINS.get(index);
    }

    private static StoredNpc npc(Place at, StoredNpc.Skin skin, String glow, List<String> label) {
        return new StoredNpc().withLocation(at).withSkin(skin).withGlow(glow).withLabelLines(label)
                .withLook(new StoredNpc.Look(NpcLook.EACH_VIEWER, 12));
    }
}
