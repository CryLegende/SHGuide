package org.Cry_Legende.sHGuide;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetourTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-11-23");

    @Test
    void newPlayerGetsNoReturn() {
        assertFalse(Retour.isReturn(null, TODAY, 3));
    }

    @Test
    void twoDaysIsNotAReturn() {
        assertFalse(Retour.isReturn("2026-11-21", TODAY, 3));
    }

    @Test
    void threeDaysIsAReturn() {
        assertTrue(Retour.isReturn("2026-11-20", TODAY, 3));
    }

    @Test
    void lastSeenIsSeededFromTheServerForANewRecord() {
        PlayerState s = PlayerState.fresh("Bot");
        long millis = java.time.ZonedDateTime.of(2026, 11, 10, 22, 0, 0, 0, java.time.ZoneId.of("Europe/Paris")).toInstant().toEpochMilli();
        Retour.seedLastSeen(s, millis, java.time.ZoneId.of("Europe/Paris"));
        assertEquals("2026-11-10", s.lastSeen);
        Retour.seedLastSeen(s, 0, java.time.ZoneId.of("Europe/Paris"));
        assertEquals("2026-11-10", s.lastSeen);
        PlayerState known = PlayerState.fresh("Bot");
        known.lastSeen = "2026-11-22";
        Retour.seedLastSeen(known, millis, java.time.ZoneId.of("Europe/Paris"));
        assertEquals("2026-11-22", known.lastSeen);
        PlayerState never = PlayerState.fresh("Bot");
        Retour.seedLastSeen(never, 0, java.time.ZoneId.of("Europe/Paris"));
        assertEquals(null, never.lastSeen);
    }
}
