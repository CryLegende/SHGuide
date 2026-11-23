package org.Cry_Legende.sHGuide;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgrammeTest {

    private static final LocalDate DAY = LocalDate.parse("2026-11-23");

    private Settings cfg;
    private final Map<String, String> values = new HashMap<>();
    private final Resolver r = text -> {
        String out = text;
        for (var e : values.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    };

    @BeforeEach
    void setUp() {
        cfg = Settings.read(SettingsTest.example());
        values.put("%player_level%", "10");
        values.put("%player_gamemode%", "SURVIVAL");
        values.put("%statistic_jump%", "100");
    }

    @Test
    void firstTickTakesBaselines() {
        PlayerState s = PlayerState.fresh("Bot");
        Programme.Tick t = Programme.update(s, cfg, r, DAY);
        assertTrue(t.dayChanged());
        assertEquals(DAY.toString(), s.day);
        assertEquals(1, s.palier);
        assertEquals(10.0, s.baselines.get("niveau#0"));
        assertEquals(0, t.tasks().stream().filter(Programme.TaskView::done).count());
    }

    @Test
    void countersProgressAndComplete() {
        PlayerState s = PlayerState.fresh("Bot");
        Programme.update(s, cfg, r, DAY);
        values.put("%player_level%", "11");
        values.put("%player_gamemode%", "CREATIVE");
        values.put("%statistic_jump%", "102");
        Programme.Tick t = Programme.update(s, cfg, r, DAY);
        assertEquals(2, t.newlyDone().size());
        assertFalse(t.newlyCompleted());
        values.put("%statistic_jump%", "103");
        t = Programme.update(s, cfg, r, DAY);
        assertTrue(t.newlyCompleted());
        assertEquals(1, s.streak);
        assertEquals(List.of("jour"), s.pending);
    }

    @Test
    void notReadyCounterTakesBaselineLater() {
        values.put("%player_level%", "Chargement...");
        PlayerState s = PlayerState.fresh("Bot");
        Programme.update(s, cfg, r, DAY);
        assertNull(s.baselines.get("niveau#0"));
        values.put("%player_level%", "250");
        Programme.Tick t = Programme.update(s, cfg, r, DAY);
        assertEquals(250.0, s.baselines.get("niveau#0"));
        assertFalse(t.tasks().get(0).done());
        assertEquals(0.0, t.tasks().get(0).current());
    }

    @Test
    void midnightRollsOverWhileOnline() {
        PlayerState s = PlayerState.fresh("Bot");
        Programme.update(s, cfg, r, DAY);
        values.put("%player_level%", "11");
        Programme.update(s, cfg, r, DAY);
        assertTrue(s.done.contains("niveau"));
        Programme.Tick t = Programme.update(s, cfg, r, DAY.plusDays(1));
        assertTrue(t.dayChanged());
        assertTrue(s.done.isEmpty());
        assertEquals(11.0, s.baselines.get("niveau#0"));
        assertEquals(cfg.rotatingFor(DAY.plusDays(1)).id(), s.rotating);
    }

    @Test
    void baselineWaitsUntilTheJoinDelayIsOver() {
        PlayerState s = PlayerState.fresh("Bot");
        Programme.Tick t = Programme.update(s, cfg, r, DAY, false);
        assertNull(s.baselines.get("niveau#0"));
        assertFalse(t.tasks().get(0).done());
        values.put("%player_level%", "13");
        Programme.update(s, cfg, r, DAY, true);
        assertEquals(13.0, s.baselines.get("niveau#0"));
        values.put("%player_level%", "14");
        assertTrue(Programme.update(s, cfg, r, DAY, true).tasks().get(0).done());
    }

    @Test
    void palierIsFrozenOnlyAfterTheJoinDelay() {
        YamlConfiguration c = SettingsTest.example();
        c.set("palier", "%palier%");
        cfg = Settings.read(c);
        values.put("%palier%", "1");
        PlayerState s = PlayerState.fresh("Bot");
        Programme.update(s, cfg, r, DAY, false);
        assertEquals(0, s.palier);
        values.put("%palier%", "4");
        Programme.update(s, cfg, r, DAY, true);
        assertEquals(4, s.palier);
    }

    @Test
    void valueTaskCountsWhatWasDoneBeforeJoining() {
        YamlConfiguration c = SettingsTest.example();
        c.set("programme.taches.vote.texte", "Voter une fois");
        c.set("programme.taches.vote.valeur", "%votes_jour%");
        c.set("programme.taches.vote.objectif", List.of(1));
        cfg = Settings.read(c);
        values.put("%votes_jour%", "1");
        PlayerState s = PlayerState.fresh("Bot");
        Programme.Tick t = Programme.update(s, cfg, r, DAY, true);
        assertTrue(t.tasks().stream().filter(v -> v.def().id().equals("vote")).findFirst().orElseThrow().done());
    }

    @Test
    void countTaskIgnoresYesterdayUntilTheReset() {
        PlayerState s = PlayerState.fresh("Bot");
        values.put("%player_gamemode%", "CREATIVE");
        // minuit passé, mais le plugin compté n'a pas encore remis ses quêtes à zéro
        Programme.Tick t = Programme.update(s, cfg, r, DAY, true);
        assertFalse(t.tasks().get(1).done());
        values.put("%player_gamemode%", "SURVIVAL");
        Programme.update(s, cfg, r, DAY, true);
        values.put("%player_gamemode%", "CREATIVE");
        assertTrue(Programme.update(s, cfg, r, DAY, true).tasks().get(1).done());
    }

    @Test
    void decreasingCounterRebases() {
        PlayerState s = PlayerState.fresh("Bot");
        Programme.update(s, cfg, r, DAY);
        values.put("%player_level%", "4");
        Programme.Tick t = Programme.update(s, cfg, r, DAY);
        assertEquals(4.0, s.baselines.get("niveau#0"));
        assertEquals(0.0, t.tasks().get(0).current());
        values.put("%player_level%", "5");
        t = Programme.update(s, cfg, r, DAY);
        assertTrue(t.tasks().get(0).done());
    }

    @Test
    void completionCountsOncePerDay() {
        PlayerState s = PlayerState.fresh("Bot");
        Programme.update(s, cfg, r, DAY);
        values.put("%player_level%", "11");
        values.put("%player_gamemode%", "CREATIVE");
        values.put("%statistic_jump%", "110");
        assertTrue(Programme.update(s, cfg, r, DAY).newlyCompleted());
        values.put("%player_gamemode%", "SURVIVAL");
        Programme.update(s, cfg, r, DAY);
        values.put("%player_gamemode%", "CREATIVE");
        assertFalse(Programme.update(s, cfg, r, DAY).newlyCompleted());
        assertEquals(1, s.streak);
        assertEquals(1, s.pending.size());
    }

    @Test
    void doneTaskStaysDone() {
        PlayerState s = PlayerState.fresh("Bot");
        Programme.update(s, cfg, r, DAY);
        values.put("%player_gamemode%", "CREATIVE");
        Programme.update(s, cfg, r, DAY);
        values.put("%player_gamemode%", "SURVIVAL");
        assertTrue(Programme.update(s, cfg, r, DAY).tasks().get(1).done());
    }

    @Test
    void palierIsFrozenForTheDay() {
        YamlConfiguration c = SettingsTest.example();
        c.set("palier", "%palier%");
        cfg = Settings.read(c);
        values.put("%palier%", "4.0");
        PlayerState s = PlayerState.fresh("Bot");
        Programme.update(s, cfg, r, DAY);
        values.put("%palier%", "1");
        Programme.Tick t = Programme.update(s, cfg, r, DAY);
        assertEquals(4, s.palier);
        assertEquals(12.0, t.tasks().get(2).goal());
        assertEquals("Sauter 12 fois", t.tasks().get(2).text());
    }
}
