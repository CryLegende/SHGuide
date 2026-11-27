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
        var def = new Settings.TaskDef(id, id, "PAPER", click, List.of("%x%"), List.of(), "", "", List.of((int) goal), List.of(), List.of(), false, List.of());
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

    private static Resolver values(Map<String, String> v) {
        return t -> {
            for (var e : v.entrySet()) {
                t = t.replace(e.getKey(), e.getValue());
            }
            return t;
        };
    }

    private static Settings.ObjectiveDef loop(String sort) {
        return new Settings.ObjectiveDef("aventure", List.of("%q_{i}_id% ~ ^p\\d\\d_(0[1-46-8]|10)$"),
                "%q_{i}_name%", "%q_{i}_todo%", List.of(), "quest", "MAP", true, 0, 4, sort);
    }

    @Test
    void loopTriesEachIndexFromZero() {
        Map<String, String> v = new HashMap<>(Map.of("%q_0_id%", "p09_01", "%q_0_name%", "Zéro", "%q_0_todo%", "/"));
        assertEquals("Zéro", Objectives.pick(List.of(loop("")), Map.of(), values(v)).text());
        v.putAll(Map.of("%q_0_id%", "p09_05", "%q_1_id%", "p09_09", "%q_3_id%", "p09_03", "%q_3_name%", "Artisan IV"));
        assertEquals("Artisan IV", Objectives.pick(List.of(loop("")), Map.of(), values(v)).text());
        v.put("%q_3_id%", "p09_09");
        assertNull(Objectives.pick(List.of(loop("")), Map.of(), values(v)));
    }

    @Test
    void sortKeepsTheSmallest() {
        Map<String, String> v = new HashMap<>(Map.of("%q_0_id%", "p01_09", "%q_0_name%", "Bâtisseur", "%q_1_id%", "p01_10",
                "%q_1_name%", "Boulanger", "%q_2_id%", "p01_03", "%q_2_name%", "Bûcheron", "%q_2_todo%", "Casser 6 bûches (0/6)"));
        var c = Objectives.pick(List.of(loop("%q_{i}_id%")), Map.of(), values(v));
        assertEquals("Bûcheron", c.text());
        assertEquals("Casser 6 bûches (0/6)", c.todo());
    }

    @Test
    void todoIsForgottenWhenNothingAnswers() {
        Map<String, String> v = new HashMap<>(Map.of("%q_0_id%", "p01_03", "%q_0_name%", "Bûcheron", "%q_0_todo%", "/"));
        assertEquals("", Objectives.pick(List.of(loop("")), Map.of(), values(v)).todo());
        v.remove("%q_0_todo%");
        assertEquals("", Objectives.pick(List.of(loop("")), Map.of(), values(v)).todo());
    }

    @Test
    void scoreboardShowsWhatToDoWhenItFits() {
        var c = new Objectives.Choice("a", "&eBâtisseur", "Poser 32 blocs (12/32)", List.of(), "", "MAP");
        assertEquals("Poser 32 blocs (12/32)", c.shortText(30));
        assertEquals("&eBâtisseur", c.shortText(10));
        assertEquals("&eBâtisseur", new Objectives.Choice("a", "&eBâtisseur", "", List.of(), "", "MAP").shortText(30));
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
