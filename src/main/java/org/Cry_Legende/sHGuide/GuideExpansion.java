package org.Cry_Legende.sHGuide;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.time.LocalDate;
import java.util.Locale;

/** %shguide_*% : lit seulement ce que la dernière vérification du joueur a calculé. */
final class GuideExpansion extends PlaceholderExpansion {

    private final SHGuide plugin;

    GuideExpansion(SHGuide plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "shguide";
    }

    @Override
    public String getAuthor() {
        return "Cry_Legende";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return "";
        }
        Sessions.Session s = plugin.sessions().get(player.getUniqueId());
        if (s == null || s.last == null) {
            return "";
        }
        LocalDate today = plugin.today();
        String key = params.toLowerCase(Locale.ROOT);
        switch (key) {
            case "objectif":
                return s.shortObjective;
            case "objectif_nom":
                return s.objective == null ? plugin.messages().text("objectif.aucun") : s.objective.text();
            case "objectif_faire":
                return s.objective == null ? plugin.messages().text("objectif.aucun")
                        : s.objective.todo().isEmpty() ? s.objective.text() : s.objective.todo();
            case "ligne":
                return s.scoreboardLine;
            case "objectif_detail":
                return s.objective == null || s.objective.lore().isEmpty() ? "" : s.objective.lore().get(0);
            case "programme":
                return Long.toString(s.last.tasks().stream().filter(Programme.TaskView::done).count());
            case "programme_total":
                return Integer.toString(s.last.tasks().size());
            case "serie":
                return Integer.toString(Streak.display(s.state, today));
            case "record":
                return Integer.toString(s.state.best);
            case "joker":
                return Streak.jokerAvailable(s.state, today) ? "oui" : "non";
            case "recompense_prete":
                return s.state.pending.isEmpty() ? "non" : "oui";
            case "cadeaux":
                return Integer.toString(s.state.pending.size());
            case "alerte":
                return s.alert.isEmpty() ? "non" : "oui";
            case "alerte_texte":
                return s.alert;
            case "fin_jour":
                return Jetons.untilMidnight(plugin.settings(), plugin.messages());
            case "nouveautes_nombre":
                return Integer.toString(s.news.size());
            case "nouveautes_non_lues":
                return Integer.toString(s.unread);
            case "retour":
                return player instanceof org.bukkit.entity.Player p ? String.join("\n", plugin.sessions().returnText(p, s)) : "";
            case "accueil_temps": {
                AccueilConfig ac = plugin.settings().accueil();
                return Accueil.stayActive(s.state, ac, today)
                        ? Accueil.clock(ac.stay().minutes() * 60 - s.state.staySeconds) : "";
            }
            case "tutoriel":
                return Accueil.tutorialActive(s.state, plugin.settings().accueil()) ? "oui" : "non";
            case "tutoriel_fini":
                return s.state.tutoFinished ? "oui" : "non";
            case "tutoriel_etape":
                return Integer.toString(Accueil.stepNumber(s.state, plugin.settings().accueil()));
            case "tutoriel_total":
                return Integer.toString(plugin.settings().accueil().tutorial().steps().size());
            case "tutoriel_texte": {
                AccueilConfig.Step st = Accueil.tutorialActive(s.state, plugin.settings().accueil())
                        ? Accueil.currentStep(s.state, plugin.settings().accueil()) : null;
                return st == null ? "" : st.text();
            }
            case "menu_vu":
                return today.toString().equals(s.state.alertSeen) ? "oui" : "non";
            case "nouveau":
                return s.state.firstDay != null ? "oui" : "non";
            default:
                break;
        }
        if (key.startsWith("nouveaute_")) {
            try {
                int n = Integer.parseInt(key.substring("nouveaute_".length()));
                return n >= 1 && n <= s.news.size() ? s.news.get(n - 1).title() : "";
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
