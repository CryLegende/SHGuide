package org.Cry_Legende.sHGuide;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreakTest {

    private static final Set<Integer> STEPS = Set.of(7, 14, 21, 30);

    private static LocalDate d(String s) {
        return LocalDate.parse(s);
    }

    @Test
    void consecutiveDaysGrow() {
        PlayerState s = PlayerState.fresh("Bot");
        assertEquals(1, Streak.onComplete(s, d("2026-11-23"), 30, STEPS));
        assertEquals(2, Streak.onComplete(s, d("2026-11-24"), 30, STEPS));
        assertEquals(2, s.best);
    }

    @Test
    void sameDayTwiceCountsOnce() {
        PlayerState s = PlayerState.fresh("Bot");
        Streak.onComplete(s, d("2026-11-23"), 30, STEPS);
        assertEquals(1, Streak.onComplete(s, d("2026-11-23"), 30, STEPS));
        assertEquals(1, s.pending.size());
    }

    @Test
    void oneMissedDayUsesTheJoker() {
        PlayerState s = PlayerState.fresh("Bot");
        Streak.onComplete(s, d("2026-11-23"), 30, STEPS);
        assertEquals(2, Streak.onComplete(s, d("2026-11-25"), 30, STEPS));
        assertTrue(s.jokerWeeks.contains(Streak.week(d("2026-11-24"))));
        assertFalse(Streak.jokerAvailable(s, d("2026-11-25")));
    }

    @Test
    void jokerIsSpokenForByAnUncoveredMissedDay() {
        PlayerState s = PlayerState.fresh("Bot");
        Streak.onComplete(s, d("2026-11-23"), 30, STEPS);
        // mardi 24 raté : mercredi 25, le joker de la semaine couvre déjà ce jour
        assertFalse(Streak.jokerAvailable(s, d("2026-11-25")));
        assertTrue(Streak.jokerAvailable(s, d("2026-11-24")));
    }

    @Test
    void secondMissedDaySameWeekResets() {
        PlayerState s = PlayerState.fresh("Bot");
        Streak.onComplete(s, d("2026-11-23"), 30, STEPS);
        Streak.onComplete(s, d("2026-11-25"), 30, STEPS);
        assertEquals(1, Streak.onComplete(s, d("2026-11-27"), 30, STEPS));
    }

    @Test
    void twoMissedDaysInTwoWeeksSurvive() {
        PlayerState s = PlayerState.fresh("Bot");
        Streak.onComplete(s, d("2026-11-28"), 30, STEPS);
        // dimanche 29 (semaine 48) et lundi 30 (semaine 49) ratés : un joker par semaine
        assertEquals(2, Streak.onComplete(s, d("2026-12-01"), 30, STEPS));
    }

    @Test
    void displayDropsToZeroWhenBroken() {
        PlayerState s = PlayerState.fresh("Bot");
        Streak.onComplete(s, d("2026-11-23"), 30, STEPS);
        assertEquals(1, Streak.display(s, d("2026-11-24")));
        assertEquals(1, Streak.display(s, d("2026-11-25")));
        assertEquals(0, Streak.display(s, d("2026-11-26")));
    }

    @Test
    void cycleStepsGiveKeys() {
        PlayerState s = PlayerState.fresh("Bot");
        LocalDate day = d("2026-11-01");
        for (int i = 0; i < 37; i++) {
            Streak.onComplete(s, day.plusDays(i), 30, STEPS);
        }
        assertEquals(37, s.streak);
        assertEquals(37, s.pending.stream().filter("jour"::equals).count());
        assertEquals(List.of("palier:7", "palier:14", "palier:21", "palier:30", "palier:7"),
                s.pending.stream().filter(p -> p.startsWith("palier:")).toList());
    }
}
