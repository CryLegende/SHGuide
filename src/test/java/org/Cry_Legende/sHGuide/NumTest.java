package org.Cry_Legende.sHGuide;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumTest {

    @Test
    void parsesPlainAndFormatted() {
        assertEquals(12273.0, Num.parse("12 273").getAsDouble());
        assertEquals(12273.0, Num.parse("12 273").getAsDouble());
        assertEquals(5.0, Num.parse("5.0").getAsDouble());
        assertEquals(2.5, Num.parse("2,5").getAsDouble());
        assertEquals(-3.0, Num.parse(" -3 ").getAsDouble());
    }

    @Test
    void rejectsNonNumbers() {
        assertTrue(Num.parse("%questcreator_allcompletions%").isEmpty());
        assertTrue(Num.parse("Chargement...").isEmpty());
        assertTrue(Num.parse("").isEmpty());
        assertTrue(Num.parse(null).isEmpty());
        assertTrue(Num.parse("NaN").isEmpty());
        assertTrue(Num.parse("Infinity").isEmpty());
        assertTrue(Num.parse("-Infinity").isEmpty());
    }

    @Test
    void formatsWithSpaces() {
        assertEquals("1 000", Num.format(1000));
        assertEquals("74", Num.format(74.0));
        assertEquals("6 000", Num.format(6000.4));
    }

    @Test
    void replacesTokens() {
        assertEquals("Pêcher 120 poissons", Tokens.apply("Pêcher {objectif} poissons", Map.of("objectif", "120")));
        assertEquals("{inconnu} reste", Tokens.apply("{inconnu} reste", Map.of()));
    }
}
