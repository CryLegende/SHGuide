package org.Cry_Legende.sHGuide;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsTest {

    static YamlConfiguration example() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                SettingsTest.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
    }

    @Test
    void slowPlaceholdersAreRead() {
        assertEquals(java.util.Map.of("%statistic_mine_block%", 30), Settings.read(example()).slowPlaceholders());
        YamlConfiguration c = example();
        c.set("placeholders-lents", null);
        assertTrue(Settings.read(c).slowPlaceholders().isEmpty());
    }

    @Test
    void readsExample() {
        Settings s = Settings.read(example());
        assertEquals(2, s.tasks().size());
        assertEquals("niveau", s.tasks().get(0).id());
        assertTrue(s.tasks().get(1).isCount());
        assertEquals(1, s.tasks().get(0).goal(5));
        assertEquals(9, s.rotating().get(0).goal(3));
        assertEquals(Set.of(7, 14, 21, 30), s.stepDays());
        assertEquals(5, s.objectives().size());
        assertEquals(3, s.tasksFor(LocalDate.parse("2026-11-23")).size());
        assertEquals("1 diamant", s.dailyRewardText());
        assertEquals("2 émeraudes", s.gain(14));
        assertEquals("", s.gain(15));
        assertEquals(30, s.objectiveMax());
        assertEquals("24/11", LocalDate.parse("2026-11-24").format(s.newsDate()));
    }

    @Test
    void rotatingChangesEveryDay() {
        YamlConfiguration c = example();
        c.set("programme.tournantes.peche.texte", "Pêcher {objectif} poissons");
        c.set("programme.tournantes.peche.compteurs", List.of("%statistic_fish_caught%"));
        c.set("programme.tournantes.peche.objectif", List.of(20));
        Settings s = Settings.read(c);
        LocalDate d = LocalDate.parse("2026-11-23");
        assertNotEquals(s.rotatingFor(d).id(), s.rotatingFor(d.plusDays(1)).id());
    }

    @Test
    void loopCanStartAtZero() {
        YamlConfiguration c = example();
        c.set("objectifs.suivante.boucle.de", 0);
        c.set("objectifs.suivante.boucle.a", 12);
        c.set("objectifs.suivante.boucle.tri", "%q_{i}%");
        Settings.ObjectiveDef o = Settings.read(c).objectives().stream().filter(d -> d.id().equals("suivante")).findFirst().orElseThrow();
        assertTrue(o.loop());
        assertEquals(0, o.loopFrom());
        assertEquals("%q_{i}%", o.sort());
        assertFalse(Settings.read(example()).objectives().get(0).loop());
    }

    @Test
    void badGainDayAndDateFormatAreNamed() {
        YamlConfiguration c = example();
        c.set("serie.gains.sept", "x");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Settings.read(c)).getMessage().contains("serie.gains.sept"));
        YamlConfiguration d = example();
        d.set("nouveautes-date", "jj/{{");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Settings.read(d)).getMessage().contains("nouveautes-date"));
    }

    @Test
    void badConditionNamesTheKey() {
        YamlConfiguration c = example();
        c.set("objectifs.bravo.si", List.of("{recompense_prete}"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Settings.read(c));
        assertTrue(e.getMessage().contains("objectifs.bravo.si"));
    }

    @Test
    void goalsNeedOneOrFiveValues() {
        YamlConfiguration c = example();
        c.set("programme.taches.niveau.objectif", List.of(1, 2));
        assertThrows(IllegalArgumentException.class, () -> Settings.read(c));
    }
}
