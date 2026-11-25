package org.Cry_Legende.sHGuide;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SerieTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-11-25");
    private static final Set<Integer> STEPS = Set.of(7, 14, 21, 30);

    private static Serie serie(int streak, boolean doneToday) {
        PlayerState s = PlayerState.fresh("Cry");
        s.streak = streak;
        s.lastComplete = streak == 0 ? null : (doneToday ? TODAY : TODAY.minusDays(1)).toString();
        return Serie.of(s, TODAY, 30, STEPS);
    }

    private static List<Serie.Jour> track(Serie s, int cells) {
        List<Integer> days = s.cells(cells);
        return java.util.stream.IntStream.range(0, days.size())
                .mapToObj(i -> s.day(days.get(i), i == 0 ? s.start() : days.get(i - 1))).toList();
    }

    @Test
    void noStreakStartsTheFirstWeek() {
        Serie s = serie(0, false);
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7), s.cells(7));
        assertEquals(7, s.left());
        assertEquals(Serie.Jour.AUJOURDHUI, track(s, 7).get(0));
        assertTrue(track(s, 7).subList(1, 7).stream().allMatch(j -> j == Serie.Jour.A_VENIR));
    }

    @Test
    void fiveDaysThenToday() {
        List<Serie.Jour> t = track(serie(5, false), 7);
        assertEquals(List.of(Serie.Jour.FAIT, Serie.Jour.FAIT, Serie.Jour.FAIT, Serie.Jour.FAIT, Serie.Jour.FAIT,
                Serie.Jour.AUJOURDHUI, Serie.Jour.A_VENIR), t);
        assertEquals(2, serie(5, false).left());
    }

    @Test
    void sixthDayDoneLeavesTomorrowForTheStep() {
        List<Serie.Jour> t = track(serie(6, true), 7);
        assertEquals(Serie.Jour.FAIT, t.get(5));
        assertEquals(Serie.Jour.A_VENIR, t.get(6));
    }

    @Test
    void reachingAStepKeepsItsWeekUntilTomorrow() {
        Serie done = serie(7, true);
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7), done.cells(7));
        assertTrue(track(done, 7).stream().allMatch(j -> j == Serie.Jour.FAIT));
        Serie next = serie(7, false);
        assertEquals(List.of(8, 9, 10, 11, 12, 13, 14), next.cells(7));
        assertEquals(Serie.Jour.AUJOURDHUI, track(next, 7).get(0));
    }

    @Test
    void longSegmentIsSpreadOverTheCells() {
        Serie s = serie(24, false);
        assertEquals(21, s.start());
        assertEquals(30, s.end());
        List<Integer> days = s.cells(7);
        assertEquals(7, days.size());
        assertEquals(30, days.get(6));
        assertEquals(1, track(s, 7).stream().filter(j -> j == Serie.Jour.AUJOURDHUI).count());
    }

    @Test
    void cycleStartsAgainTheDayAfter() {
        assertEquals(List.of(22, 23, 24, 25, 26, 27, 28, 29, 30), serie(30, true).cells(9));
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7), serie(30, false).cells(7));
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7), serie(31, true).cells(7));
    }

    @Test
    void brokenStreakIsZero() {
        PlayerState s = PlayerState.fresh("Cry");
        s.streak = 12;
        s.lastComplete = "2026-11-10";
        assertEquals(0, Serie.of(s, TODAY, 30, STEPS).streak());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7), Serie.of(s, TODAY, 30, STEPS).cells(7));
    }

    @Test
    void stepsStates() {
        Serie s = serie(8, true);
        assertEquals(Serie.Palier.ATTEINT, s.step(7, Set.of()));
        assertEquals(Serie.Palier.A_PRENDRE, s.step(7, Set.of("palier:7")));
        assertEquals(Serie.Palier.SUIVANT, s.step(14, Set.of()));
        assertEquals(Serie.Palier.LOIN, s.step(21, Set.of()));
    }
}
