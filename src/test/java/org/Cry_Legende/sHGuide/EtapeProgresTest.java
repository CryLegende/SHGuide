package org.Cry_Legende.sHGuide;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EtapeProgresTest {

    private final Map<String, String> values = new HashMap<>();
    private final Resolver resolver = text -> values.getOrDefault(text, text);

    private static AccueilConfig read(String step) {
        YamlConfiguration c = new YamlConfiguration();
        try {
            c.loadFromString("tutoriel:\n  etapes:\n    essai:\n      fait-si:\n        - \"%fini% >= 1\"\n" + step);
        } catch (InvalidConfigurationException e) {
            throw new IllegalStateException(e);
        }
        return AccueilConfig.read(c);
    }

    private static AccueilConfig.Step step(String lines) {
        return read(lines).tutorial().steps().get(0);
    }

    private double raw(AccueilConfig.Step st) {
        return EtapeProgres.raw(st, resolver).orElse(-1);
    }

    @Test
    void sixLogsFillTheBarOneByOne() {
        AccueilConfig.Step st = step("      progres: \"%buches%\"\n      sur: 6\n");
        for (int i = 0; i <= 6; i++) {
            values.put("%buches%", i + ".0");
            assertEquals(i / 6f, EtapeProgres.since(raw(st), 0), 1e-6);
        }
    }

    @Test
    void finishedQuestIsUnreadable() {
        AccueilConfig.Step st = step("      progres: \"%avance%\"\n      sur: 100\n");
        values.put("%avance%", "/");
        assertTrue(EtapeProgres.raw(st, resolver).isEmpty());
        values.put("%avance%", "250");
        assertEquals(1, raw(st));
    }

    @Test
    void mostAdvancedValueCounts() {
        AccueilConfig.Step st = step("      progres:\n        - \"%a%\"\n        - \"%b%\"\n        - \"%c%\"\n      sur: 100\n");
        values.put("%a%", "12,5");
        values.put("%b%", "Chargement...");
        values.put("%c%", "40.0");
        assertEquals(0.4, raw(st), 1e-9);
    }

    @Test
    void conditionsShareTheBar() {
        AccueilConfig.Step st = step("      progres-si:\n        - \"%monde% == spawn\"\n        - \"%fini% >= 1\"\n");
        values.put("%monde%", "ile");
        values.put("%fini%", "0");
        assertEquals(0, raw(st));
        values.put("%monde%", "spawn");
        assertEquals(0.5, raw(st));
        values.put("%fini%", "1");
        assertEquals(1, raw(st));
    }

    @Test
    void withoutSettingTheBarFollowsDoneIf() {
        AccueilConfig.Step st = step("");
        values.put("%fini%", "0");
        assertEquals(0, raw(st));
        values.put("%fini%", "2");
        assertEquals(1, raw(st));
    }

    @Test
    void barStartsAtZeroWhateverWasAlreadyDone() {
        assertEquals(0f, EtapeProgres.since(0.25, 0.25));
        assertEquals(0.5f, EtapeProgres.since(0.625, 0.25), 1e-6);
        assertEquals(1f, EtapeProgres.since(1, 0.25));
        assertEquals(0f, EtapeProgres.since(0.1, 0.25));
        assertEquals(1f, EtapeProgres.since(1, 1));
        assertEquals(0f, EtapeProgres.since(0.4, 1));
    }

    @Test
    void readsDefaultsAndRefusesBadValues() {
        AccueilConfig c = read("");
        assertEquals(4, c.tutorial().barTicks());
        assertEquals(25, c.tutorial().fullTicks());
        assertEquals(List.of(), c.tutorial().steps().get(0).progress());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> read("      progres: \"%a%\"\n      sur: 0\n"))
                .getMessage().contains("essai.sur"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> read("      progres-si:\n        - \"rien\"\n"))
                .getMessage().contains("essai.progres-si"));
    }
}
