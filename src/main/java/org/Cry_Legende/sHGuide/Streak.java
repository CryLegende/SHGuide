package org.Cry_Legende.sHGuide;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Série de jours complets. Un jour raté par semaine (lundi à dimanche) est pardonné grâce au joker de cette
 * semaine ; un deuxième jour raté dans la même semaine remet la série à 1 au prochain jour complet.
 */
final class Streak {

    private Streak() {
    }

    static String week(LocalDate d) {
        return d.get(IsoFields.WEEK_BASED_YEAR) + "-W" + String.format("%02d", d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    /** Jour complet : met la série à jour et ajoute ses récompenses à récupérer. Un même jour ne compte qu'une fois. */
    static int onComplete(PlayerState s, LocalDate day, int cycle, Set<Integer> steps) {
        String today = day.toString();
        if (today.equals(s.lastComplete)) {
            return s.streak;
        }
        if (s.lastComplete == null) {
            s.streak = 1;
        } else {
            List<LocalDate> missed = missed(LocalDate.parse(s.lastComplete), day);
            if (missed.isEmpty()) {
                s.streak++;
            } else if (coverable(s, missed)) {
                for (LocalDate m : missed) {
                    s.jokerWeeks.add(week(m));
                }
                s.streak++;
            } else {
                s.streak = 1;
            }
        }
        s.best = Math.max(s.best, s.streak);
        s.lastComplete = today;
        s.pending.add("jour");
        int position = ((s.streak - 1) % cycle) + 1;
        if (steps.contains(position)) {
            s.pending.add("palier:" + position);
        }
        prune(s, day);
        return s.streak;
    }

    /** Série affichée aujourd'hui : 0 si les jours ratés depuis le dernier jour complet l'ont déjà cassée. */
    static int display(PlayerState s, LocalDate today) {
        if (s.lastComplete == null) {
            return 0;
        }
        LocalDate last = LocalDate.parse(s.lastComplete);
        if (!last.isBefore(today.minusDays(1))) {
            return s.streak;
        }
        // aujourd'hui peut encore être complété : seuls les jours entre le dernier jour complet et aujourd'hui comptent
        return coverable(s, missed(last, today)) ? s.streak : 0;
    }

    static boolean jokerAvailable(PlayerState s, LocalDate today) {
        return !s.jokerWeeks.contains(week(today));
    }

    static List<LocalDate> missed(LocalDate last, LocalDate day) {
        List<LocalDate> out = new ArrayList<>();
        for (LocalDate d = last.plusDays(1); d.isBefore(day); d = d.plusDays(1)) {
            out.add(d);
        }
        return out;
    }

    private static boolean coverable(PlayerState s, List<LocalDate> missed) {
        Map<String, Integer> perWeek = new HashMap<>();
        for (LocalDate m : missed) {
            perWeek.merge(week(m), 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> e : perWeek.entrySet()) {
            if (e.getValue() > 1 || s.jokerWeeks.contains(e.getKey())) {
                return false;
            }
        }
        return true;
    }

    /** Garde les jokers des deux dernières semaines seulement. */
    private static void prune(PlayerState s, LocalDate day) {
        String thisWeek = week(day);
        String lastWeek = week(day.with(DayOfWeek.MONDAY).minusDays(1));
        s.jokerWeeks.removeIf(w -> !w.equals(thisWeek) && !w.equals(lastWeek));
    }
}
