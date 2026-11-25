package org.Cry_Legende.sHGuide;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ce que les textes de menus.yml peuvent dire d'un joueur (la liste est en tête du fichier). Construit à l'ouverture d'un
 * écran, à partir de ce que la dernière vérification du joueur a calculé.
 */
final class Jetons {

    private static final List<String> DAYS = List.of("lun", "mar", "mer", "jeu", "ven", "sam", "dim");

    private Jetons() {
    }

    static Texte.Contexte of(SHGuide plugin, Player p, Sessions.Session s, Programme.Tick t) {
        Settings cfg = plugin.settings();
        MenusConfig menus = plugin.menus();
        Messages m = plugin.messages();
        LocalDate today = plugin.today();
        Map<String, String> k = new HashMap<>();
        Map<String, Double> parts = new HashMap<>();
        Map<String, List<String>> blocks = new HashMap<>();
        Resolver papi = text -> PlaceholderAPI.setPlaceholders(p, text);

        k.put("joueur", p.getName());
        k.put("jour", DAYS.get(today.getDayOfWeek().getValue() - 1));
        k.put("fin_jour", untilMidnight(cfg, m));
        k.put("nouveau", yes(s.state.firstDay != null));
        k.put("revenu", yes(s.back));

        int total = t.tasks().size();
        int done = (int) t.tasks().stream().filter(Programme.TaskView::done).count();
        k.put("taches_faites", Integer.toString(done));
        k.put("taches_total", Integer.toString(total));
        k.put("taches_reste", Integer.toString(total - done));
        parts.put("taches", total == 0 ? 0 : (double) done / total);

        Objectives.Choice o = s.objective;
        String none = m.text("objectif.aucun");
        k.put("objectif", o == null ? none : o.text());
        k.put("objectif_faire", o == null ? none : o.todo().isEmpty() ? o.text() : o.todo());
        k.put("objectif_nom", o == null || o.todo().isEmpty() ? "" : o.text());
        k.put("objectif_icone", o == null ? "PAPER" : o.icon());
        k.put("objectif_clic", yes(o != null && !o.click().isEmpty()));
        blocks.put("objectif_description", o == null ? List.of() : o.lore());

        Serie serie = Serie.of(s.state, today, cfg.cycle(), cfg.stepDays());
        k.put("serie", Integer.toString(serie.streak()));
        k.put("record", Integer.toString(s.state.best));
        k.put("joker", yes(Streak.jokerAvailable(s.state, today)));
        k.put("palier_jour", Integer.toString(serie.end()));
        k.put("palier_reste", Integer.toString(serie.left()));
        k.put("palier_gain", cfg.gain(serie.end()));

        List<String> daily = Recompenses.pick(s.state.pending, "jour");
        k.put("cadeaux", Integer.toString(s.state.pending.size()));
        k.put("cadeaux_jour", Integer.toString(daily.size()));
        k.put("jour_etat", !daily.isEmpty() ? "pret" : s.state.completed ? "pris" : "ferme");
        blocks.put("cadeau_pret", list(menus, "cadeau-pret", Recompenses.texts(cfg, m, daily, today)));
        blocks.put("cadeau_ferme", list(menus, "cadeau-ferme", dayTexts(cfg, today)));
        blocks.put("cadeau_demain", list(menus, "cadeau-demain", tomorrowTexts(cfg, today)));
        blocks.put("attente_liste", list(menus, "cadeau-pret", Recompenses.texts(cfg, m, s.state.pending, today)));

        welcome(cfg, s, today, k, parts);

        List<Nouveautes.Entree> unread = plugin.nouveautes().unread(s.state, today, cfg.newsRecentDays());
        List<Nouveautes.Entree> out = plugin.nouveautes().out(today);
        k.put("nouveautes", Integer.toString(unread.size()));
        k.put("nouveautes_total", Integer.toString(out.size()));
        List<String> top = new ArrayList<>();
        List<Nouveautes.Entree> shown = unread.isEmpty() ? out : unread;
        for (Nouveautes.Entree n : shown.subList(0, Math.min(cfg.newsMax(), shown.size()))) {
            top.addAll(list(menus, unread.isEmpty() ? "nouveaute-vue" : "nouveaute-nouvelle", List.of(n.title())));
        }
        blocks.put("nouveautes_liste", top);

        AccueilConfig ac = cfg.accueil();
        boolean tuto = Accueil.tutorialActive(s.state, ac);
        int steps = ac.tutorial().steps().size();
        int stepsDone = (int) ac.tutorial().steps().stream().filter(st -> s.state.tutoDone.contains(st.id())).count();
        AccueilConfig.Step step = tuto ? Accueil.currentStep(s.state, ac) : null;
        k.put("tuto", yes(tuto));
        k.put("tuto_n", Integer.toString(tuto ? Accueil.stepNumber(s.state, ac) : steps));
        k.put("tuto_total", Integer.toString(steps));
        k.put("tuto_faites", Integer.toString(stepsDone));
        k.put("tuto_texte", step == null ? "" : step.text());
        k.put("tuto_cadeau", ac.tutorial().endText());
        k.put("fleche", yes(!s.state.arrowOff));
        parts.put("tuto", steps == 0 ? 0 : (double) stepsDone / steps);

        List<String> tasks = new ArrayList<>();
        for (Programme.TaskView v : t.tasks()) {
            String key = v.done() ? "tache-faite" : v.current() > 0 ? "tache-en-cours" : "tache-a-faire";
            for (String line : menus.lists().getOrDefault(key, List.of())) {
                tasks.add(Tokens.apply(line, Map.of("texte", v.text(), "actuel", Num.format(Math.min(v.current(), v.goal())),
                        "quantite", Num.format(v.goal()), "seul", yes(v.goal() <= 1))));
            }
        }
        blocks.put("taches_liste", tasks);
        return new Texte.Contexte(k, parts, blocks, papi, menus.bar());
    }

    /** Cadeaux des nouveaux joueurs : absent, attente, pret ou pris. */
    private static void welcome(Settings cfg, Sessions.Session s, LocalDate today, Map<String, String> k, Map<String, Double> parts) {
        AccueilConfig ac = cfg.accueil();
        long days = Accueil.daysSinceFirst(s.state, today);
        int seconds = ac.stay().minutes() * 60;
        String stay = s.state.pending.contains("accueil:rester") ? "pret"
                : Accueil.stayActive(s.state, ac, today) ? "attente"
                : days == 0 && s.state.welcome.contains("rester") ? "pris" : "absent";
        String tomorrow = s.state.pending.contains("accueil:demain") ? "pret"
                : ac.enabled() && days == 0 && !ac.tomorrow().commands().isEmpty() ? "attente" : "absent";
        k.put("bienvenue_etat", stay);
        k.put("bienvenue_texte", ac.stay().text());
        k.put("bienvenue_minutes", Integer.toString(ac.stay().minutes()));
        k.put("bienvenue_temps", Accueil.clock(Math.max(0, seconds - s.state.staySeconds)));
        k.put("demain_etat", tomorrow);
        k.put("demain_texte", ac.tomorrow().text());
        parts.put("bienvenue", seconds == 0 ? 0 : (double) s.state.staySeconds / seconds);
    }

    private static List<String> dayTexts(Settings cfg, LocalDate day) {
        List<String> out = new ArrayList<>();
        Settings.DailyVariant v = cfg.variantFor(day);
        if (v != null) {
            out.add(v.text());
        }
        if (!cfg.dailyRewardText().isBlank()) {
            out.add(cfg.dailyRewardText());
        }
        return out;
    }

    private static List<String> tomorrowTexts(Settings cfg, LocalDate today) {
        Settings.DailyVariant v = cfg.variantFor(today.plusDays(1));
        return v == null ? List.of() : List.of(v.text());
    }

    /** Une ligne du modèle « listes.key » par texte. */
    private static List<String> list(MenusConfig menus, String key, List<String> texts) {
        List<String> out = new ArrayList<>();
        for (String text : texts) {
            for (String line : menus.lists().getOrDefault(key, List.of("{texte}"))) {
                out.add(Tokens.apply(line, Map.of("texte", text)));
            }
        }
        return out;
    }

    static String untilMidnight(Settings cfg, Messages m) {
        ZonedDateTime now = ZonedDateTime.now(cfg.zone());
        return duration(Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(cfg.zone())).toMinutes(), m);
    }

    static String duration(long minutes, Messages m) {
        long h = minutes / 60;
        String min = Long.toString(minutes % 60);
        return h > 0 ? m.text("duree.heures", "h", Long.toString(h), "m", min) : m.text("duree.minutes", "m", min);
    }

    static String yes(boolean b) {
        return b ? "oui" : "non";
    }
}
