package org.Cry_Legende.sHGuide;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Objectif du moment : premier objectif de la config dont toutes les conditions sont vraies. */
final class Objectives {

    private Objectives() {
    }

    record Choice(String id, String text, List<String> lore, String click, String icon) {
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
            boolean loop = d.loopFrom() != 0;
            int from = loop ? d.loopFrom() : 0;
            int to = loop ? d.loopTo() : 0;
            for (int i = from; i <= to; i++) {
                Map<String, String> tk = new HashMap<>(tokens);
                if (loop) {
                    tk.put("i", Integer.toString(i));
                }
                Resolver withTokens = text -> r.resolve(Tokens.apply(text, tk));
                boolean ok = true;
                for (String cond : d.conditions()) {
                    if (!Condition.test(cond, withTokens)) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    return new Choice(d.id(), withTokens.resolve(d.text()),
                            d.lore().stream().map(withTokens::resolve).toList(),
                            withTokens.resolve(d.click()).trim(), d.icon());
                }
            }
        }
        return null;
    }

    private static String yes(boolean b) {
        return b ? "oui" : "non";
    }
}
