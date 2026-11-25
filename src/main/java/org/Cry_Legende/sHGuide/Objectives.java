package org.Cry_Legende.sHGuide;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Objectif du moment : premier objectif de la config dont toutes les conditions sont vraies. */
final class Objectives {

    private Objectives() {
    }

    /** « text » : son nom ; « todo » : ce qu'il faut faire, vide si la config ne le dit pas. */
    record Choice(String id, String text, String todo, List<String> lore, String click, String icon) {

        /** Pour le scoreboard : ce qu'il faut faire si cela tient en « max » caractères, sinon son nom. */
        String shortText(int max) {
            return !todo.isEmpty() && Texte.strip(todo).length() <= max ? todo : text;
        }
    }

    /** Jetons que le guide calcule lui-même : annonce, récompense, tâche presque finie, prochaine tâche. */
    static Map<String, String> tokens(List<Programme.TaskView> tasks, boolean rewardReady, Annonces.Active annonce,
                                      double nearRatio, String nearTemplate) {
        Map<String, String> t = new HashMap<>();
        t.put("annonce", yes(annonce != null));
        t.put("annonce_texte", annonce == null ? "" : annonce.text());
        t.put("annonce_clic", annonce == null ? "" : annonce.click());
        t.put("recompense_prete", yes(rewardReady));
        Programme.TaskView near = null;
        Programme.TaskView next = null;
        for (Programme.TaskView v : tasks) {
            if (v.done()) {
                continue;
            }
            if (next == null) {
                next = v;
            }
            if (near == null && v.goal() > 1 && v.ratio() >= nearRatio) {
                near = v;
            }
        }
        t.put("tache_proche", yes(near != null));
        t.put("tache_proche_texte", near == null ? "" : Tokens.apply(nearTemplate,
                Map.of("reste", Num.format(near.left()), "tache", near.text())));
        t.put("tache_proche_clic", near == null ? "" : near.def().click());
        t.put("tache_suivante", yes(next != null));
        t.put("tache_suivante_texte", next == null ? "" : next.text());
        t.put("tache_suivante_clic", next == null ? "" : next.def().click());
        return t;
    }

    static Choice pick(List<Settings.ObjectiveDef> defs, Map<String, String> tokens, Resolver r) {
        for (Settings.ObjectiveDef d : defs) {
            Resolver best = null;
            String bestKey = null;
            for (int i = d.loopFrom(); i <= d.loopTo(); i++) {
                Map<String, String> tk = new HashMap<>(tokens);
                if (d.loop()) {
                    tk.put("i", Integer.toString(i));
                }
                Resolver withTokens = text -> r.resolve(Tokens.apply(text, tk));
                if (!matches(d, withTokens)) {
                    continue;
                }
                if (d.sort().isEmpty()) {
                    best = withTokens;
                    break;
                }
                String key = withTokens.resolve(d.sort());
                if (bestKey == null || key.compareTo(bestKey) < 0) {
                    bestKey = key;
                    best = withTokens;
                }
            }
            if (best != null) {
                return new Choice(d.id(), best.resolve(d.text()), todo(best.resolve(d.todo())),
                        d.lore().stream().map(best::resolve).toList(), best.resolve(d.click()).trim(), d.icon());
            }
        }
        return null;
    }

    private static boolean matches(Settings.ObjectiveDef d, Resolver r) {
        for (String cond : d.conditions()) {
            if (!Condition.test(cond, r)) {
                return false;
            }
        }
        return true;
    }

    /** « / » est ce que QuestCreator répond quand il n'a rien à dire ; un placeholder resté écrit n'a pas été lu. */
    private static String todo(String text) {
        String t = text.trim();
        return t.equals("/") || t.indexOf('%') >= 0 ? "" : t;
    }

    private static String yes(boolean b) {
        return b ? "oui" : "non";
    }
}
