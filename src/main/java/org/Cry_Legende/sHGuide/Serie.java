package org.Cry_Legende.sHGuide;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Où en est la série dans son cycle, pour l'écran des récompenses : la piste des jours jusqu'au prochain palier et l'état
 * de chaque palier. Le cycle fini, la piste repart de 0 le jour suivant.
 *
 * @param streak    série affichée (0 si elle est cassée)
 * @param position  jours faits dans le cycle en cours (0 à cycle)
 * @param doneToday programme d'aujourd'hui fini
 */
record Serie(int streak, int position, boolean doneToday, int start, int end) {

    enum Palier { A_PRENDRE, ATTEINT, SUIVANT, LOIN }

    enum Jour { FAIT, AUJOURDHUI, A_VENIR }

    static Serie of(PlayerState s, LocalDate today, int cycle, Collection<Integer> steps) {
        int streak = Streak.display(s, today);
        boolean done = streak > 0 && today.toString().equals(s.lastComplete);
        int position = streak == 0 ? 0 : ((streak - 1) % cycle) + 1;
        if (position == cycle && !done) {
            position = 0;
        }
        int start = 0;
        int end = cycle;
        for (int step : steps) {
            boolean reached = done ? step < position : step <= position;
            if (reached) {
                start = Math.max(start, step);
            } else if (step <= cycle) {
                end = Math.min(end, step);
            }
        }
        return new Serie(streak, position, done, start, Math.max(end, start + 1));
    }

    /** Jours qui restent avant le palier « end ». */
    int left() {
        return Math.max(0, end - position);
    }

    /** Le jour montré par chaque case : un par jour, ou des jours régulièrement espacés s'il y a trop de jours. */
    List<Integer> cells(int max) {
        int length = end - start;
        int n = Math.min(length, Math.max(1, max));
        List<Integer> out = new ArrayList<>();
        for (int k = 1; k <= n; k++) {
            out.add(start + (int) Math.round((double) k * length / n));
        }
        return out;
    }

    /** État d'une case de la piste ; « previous » est le jour de la case d'avant (start pour la première). */
    Jour day(int day, int previous) {
        if (day <= position) {
            return Jour.FAIT;
        }
        return !doneToday && position + 1 > previous && position + 1 <= day ? Jour.AUJOURDHUI : Jour.A_VENIR;
    }

    Palier step(int step, Collection<String> pending) {
        if (pending.contains("palier:" + step)) {
            return Palier.A_PRENDRE;
        }
        if (step <= position) {
            return Palier.ATTEINT;
        }
        return step == end ? Palier.SUIVANT : Palier.LOIN;
    }
}
