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
        return update(s, cfg, r, today, true);
    }

    /**
     * @param canBaseline faux juste après la connexion : un compteur sans départ attend (les plugins chargent encore
     *                    les données du joueur, et un nouveau joueur finit ses quêtes automatiques)
     */
    static Tick update(PlayerState s, Settings cfg, Resolver r, LocalDate today, boolean canBaseline) {
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
        int palier = s.palier;
        if (palier == 0) {
            OptionalDouble p = Num.parse(r.resolve(cfg.palierPlaceholder()));
            palier = (int) Math.max(1, Math.min(5, Math.round(p.orElse(1))));
            // juste après la connexion, le palier sert sans être gardé : l'argent et l'île peuvent encore charger
            if (canBaseline) {
                s.palier = palier;
                dirty = true;
            }
        }
        List<TaskView> views = new ArrayList<>();
        List<TaskView> newly = new ArrayList<>();
        for (Settings.TaskDef def : cfg.tasksFor(today)) {
            double goal = def.goal(palier);
            double current = 0;
            if (def.isValue()) {
                current = Math.max(0, Num.parse(r.resolve(def.value())).orElse(0));
            } else if (def.isCount()) {
                // écart depuis le début du jour, comme un compteur : juste après minuit, le plugin compté peut
                // montrer encore les quêtes d'hier ; sa remise à zéro fait redescendre le départ
                double count = 0;
                for (String v : def.countValues()) {
                    if (r.resolve(v).trim().equalsIgnoreCase(def.countEquals())) {
                        count++;
                    }
                }
                String key = def.id() + "#compte";
                Double base = s.baselines.get(key);
                if (base == null && canBaseline || base != null && count < base) {
                    s.baselines.put(key, count);
                    dirty = true;
                } else if (base != null) {
                    current = count - base;
                }
            } else {
                for (int k = 0; k < def.counters().size(); k++) {
                    OptionalDouble v = Num.parse(r.resolve(def.counters().get(k)));
                    if (v.isEmpty()) {
                        continue;
                    }
                    String key = def.id() + "#" + k;
                    Double base = s.baselines.get(key);
                    if (base == null && !canBaseline) {
                        continue;
                    }
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
