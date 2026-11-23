package org.Cry_Legende.sHGuide;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsTest {

    static YamlConfiguration example() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                SettingsTest.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
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
        assertEquals(List.of(20, 21, 22, 23), s.taskSlots());
        assertEquals(3, s.tasksFor(LocalDate.parse("2026-11-23")).size());
        assertEquals(LocalDate.parse("2026-11-23"), s.news().get(0).date());
        assertEquals(1, s.help().size());
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
    void unquotedNewsDateIsRead() {
        YamlConfiguration c = YamlConfiguration.loadConfiguration(new java.io.StringReader(
                example().saveToString().replaceAll("date: ['\"]?2026-11-23['\"]?", "date: 2026-11-23")));
        assertTrue(((java.util.Map<?, ?>) c.getMapList("retour.nouveautes").get(0)).get("date") instanceof java.util.Date);
        assertEquals(LocalDate.parse("2026-11-23"), Settings.read(c).news().get(0).date());
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
