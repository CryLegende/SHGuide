package org.Cry_Legende.sHGuide;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectivesTest {

    private static final Resolver ID = t -> t;

    private static Settings cfg() {
        return Settings.read(SettingsTest.example());
    }

    private static Programme.TaskView task(String id, double cur, double goal, String click) {
        var def = new Settings.TaskDef(id, id, "PAPER", click, List.of("%x%"), List.of(), "", "", List.of((int) goal), List.of(), List.of(), false);
        return new Programme.TaskView(def, "Pêcher 120 poissons", cur, goal, cur >= goal);
    }

    @Test
    void annonceComesFirst() {
        var tokens = Objectives.tokens(List.of(task("a", 0, 1, "vote")), true,
                new Annonces.Active("TNT Run dans 12 min", "minijeu join", 0), 0.8, "Plus que {reste} · {tache}");
        var c = Objectives.pick(cfg().objectives(), tokens, ID);
        assertEquals("annonce", c.id());
        assertEquals("minijeu join", c.click());
        assertEquals("TNT Run dans 12 min", c.text());
    }

    @Test
    void rewardBeatsTasks() {
        var tokens = Objectives.tokens(List.of(task("a", 0, 1, "vote")), true, null, 0.8, "x");
        assertEquals("recompense", Objectives.pick(cfg().objectives(), tokens, ID).id());
    }

    @Test
    void nearTaskBeatsNextTask() {
        var tokens = Objectives.tokens(List.of(task("a", 0, 1, "vote"), task("p", 100, 120, "")), false, null, 0.8,
                "Plus que {reste} · {tache}");
        var c = Objectives.pick(cfg().objectives(), tokens, ID);
        assertEquals("presque", c.id());
        assertEquals("Plus que 20 · Pêcher 120 poissons", c.text());
    }

    @Test
    void nextTaskWhenNothingNear() {
        var tokens = Objectives.tokens(List.of(task("a", 1, 1, "vote"), task("b", 0, 3, "jobs")), false, null, 0.8, "x");
        var c = Objectives.pick(cfg().objectives(), tokens, ID);
        assertEquals("suivante", c.id());
        assertEquals("jobs", c.click());
    }

    @Test
    void allDoneEndsOnBravo() {
        var tokens = Objectives.tokens(List.of(task("a", 1, 1, "vote")), false, null, 0.8, "x");
        assertEquals("bravo", Objectives.pick(cfg().objectives(), tokens, ID).id());
    }

    @Test
    void loopTriesEachIndex() {
        var def = new Settings.ObjectiveDef("aventure", List.of("%q_{i}_id% ~ ^p\\d\\d_(0[1-46-8]|10)$"),
                "%q_{i}_name%", List.of(), "quest", "MAP", 1, 4);
        Map<String, String> v = new HashMap<>(Map.of("%q_1_id%", "p09_05", "%q_2_id%", "p09_09",
                "%q_3_id%", "p09_03", "%q_3_name%", "Artisan IV"));
        Resolver r = t -> {
            for (var e : v.entrySet()) {
                t = t.replace(e.getKey(), e.getValue());
            }
            return t;
        };
        assertEquals("Artisan IV", Objectives.pick(List.of(def), Map.of(), r).text());
        v.put("%q_3_id%", "p09_09");
        assertNull(Objectives.pick(List.of(def), Map.of(), r));
    }

    @Test
    void annonceExpiresAfterGrace() {
        Annonces a = new Annonces();
        a.set(15 * 60_000L, "TNT Run", "minijeu join");
        assertNotNull(a.current(14 * 60_000L, 15, 5));
        assertNotNull(a.current(19 * 60_000L, 15, 5));
        assertNull(a.current(21 * 60_000L, 15, 5));
        assertEquals("TNT Run dans 12 min", Annonces.label(a.current(3 * 60_000L, 15, 5), 3 * 60_000L,
                "{texte} dans {minutes} min", "{texte} commence"));
        assertEquals("TNT Run commence", Annonces.label(a.current(16 * 60_000L, 15, 5), 16 * 60_000L,
                "{texte} dans {minutes} min", "{texte} commence"));
    }

    @Test
    void annonceShowsOnlyInTheWindowBeforeStart() {
        Annonces a = new Annonces();
        a.set(30 * 60_000L, "TNT Run", "minijeu join");
        assertNull(a.current(0, 15, 5));
        assertNotNull(a.current(15 * 60_000L, 15, 5));
    }

    @Test
    void annonceStartFromMinutesOrClockTime() {
        java.time.ZonedDateTime now = java.time.ZonedDateTime.of(2026, 11, 23, 19, 30, 0, 0, java.time.ZoneId.of("Europe/Paris"));
        long ms = now.toInstant().toEpochMilli();
        assertEquals(ms + 10 * 60_000L, Annonces.startOf("10", now, 5).getAsLong());
        assertEquals(ms + 30 * 60_000L, Annonces.startOf("20:00", now, 5).getAsLong());
        // 19:28 vient de commencer : on garde aujourd'hui
        assertEquals(ms - 2 * 60_000L, Annonces.startOf("19:28", now, 5).getAsLong());
        // 00:00 est passé depuis longtemps : c'est demain
        assertEquals(now.plusDays(1).withHour(0).withMinute(0).toInstant().toEpochMilli(),
                Annonces.startOf("00:00", now, 5).getAsLong());
        assertTrue(Annonces.startOf("bientôt", now, 5).isEmpty());
    }
}
