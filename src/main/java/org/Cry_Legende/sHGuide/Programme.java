package org.Cry_Legende.sHGuide;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Programme du jour d'un joueur : écart de chaque compteur depuis le début de la journée, tâches faites, jour
 * complet. Un compteur pas encore lisible (plugin qui charge) attend sa première valeur pour servir de départ ;
 * un compteur qui redescend prend sa nouvelle valeur comme départ. Une tâche faite le reste jusqu'à minuit.
 */
final class Programme {

    private Programme() {
    }

    record TaskView(Settings.TaskDef def, String text, double current, double goal, boolean done) {

        double ratio() {
            return goal <= 0 ? 1 : Math.max(0, Math.min(1, current / goal));
        }

        double left() {
            return Math.max(0, goal - current);
        }
    }

    record Tick(List<TaskView> tasks, List<TaskView> newlyDone, boolean newlyCompleted, boolean dayChanged,
                boolean dirty) {
    }

    static Tick update(PlayerState s, Settings cfg, Resolver r, LocalDate today) {
        boolean dirty = false;
        boolean dayChanged = false;
        if (!today.toString().equals(s.day)) {
            s.day = today.toString();
            s.palier = 0;
            s.baselines.clear();
            s.done.clear();
            s.completed = false;
            Settings.TaskDef rot = cfg.rotatingFor(today);
            s.rotating = rot == null ? null : rot.id();
            dayChanged = true;
            dirty = true;
        }
        if (s.palier == 0) {
            OptionalDouble p = Num.parse(r.resolve(cfg.palierPlaceholder()));
            s.palier = (int) Math.max(1, Math.min(5, Math.round(p.orElse(1))));
            dirty = true;
        }
        List<TaskView> views = new ArrayList<>();
        List<TaskView> newly = new ArrayList<>();
        for (Settings.TaskDef def : cfg.tasksFor(today)) {
            double goal = def.goal(s.palier);
            double current = 0;
            if (def.isCount()) {
                for (String v : def.countValues()) {
                    if (r.resolve(v).trim().equalsIgnoreCase(def.countEquals())) {
                        current++;
                    }
                }
            } else {
                for (int k = 0; k < def.counters().size(); k++) {
                    OptionalDouble v = Num.parse(r.resolve(def.counters().get(k)));
                    if (v.isEmpty()) {
                        continue;
                    }
                    String key = def.id() + "#" + k;
                    Double base = s.baselines.get(key);
                    if (base == null || v.getAsDouble() < base) {
                        s.baselines.put(key, v.getAsDouble());
                        dirty = true;
                        continue;
                    }
                    current += v.getAsDouble() - base;
                }
            }
            boolean done = s.done.contains(def.id()) || current >= goal;
            String text = Tokens.apply(def.text(), Map.of("objectif", Num.format(goal)));
            TaskView view = new TaskView(def, text, done ? Math.max(current, goal) : current, goal, done);
            if (done && s.done.add(def.id())) {
                newly.add(view);
                dirty = true;
            }
            views.add(view);
        }
        boolean completed = false;
        if (!s.completed && !views.isEmpty() && views.stream().allMatch(TaskView::done)) {
            s.completed = true;
            Streak.onComplete(s, today, cfg.cycle(), cfg.stepDays());
            completed = true;
            dirty = true;
        }
        return new Tick(List.copyOf(views), List.copyOf(newly), completed, dayChanged, dirty);
    }
}
