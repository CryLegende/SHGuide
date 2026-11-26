package org.Cry_Legende.sHGuide;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cadeaux en attente d'un joueur : « jour:&lt;id&gt; » (cadeau du jour, « jour » avant la 1.2), « palier:7 » (palier de la
 * série), « accueil:rester » et « accueil:demain » (nouveaux joueurs). Chacun se prend seul, ou tous ensemble.
 */
final class Recompenses {

    private Recompenses() {
    }

    static boolean isDaily(String id) {
        return id.equals("jour") || id.startsWith("jour:");
    }

    /** Ceux que « what » désigne : tout, jour, palier, palier:7, bienvenue, demain. */
    static List<String> pick(List<String> pending, String what) {
        String w = what.toLowerCase(Locale.ROOT).trim();
        return pending.stream().filter(id -> switch (w) {
            case "tout" -> true;
            case "jour" -> isDaily(id);
            case "palier" -> id.startsWith("palier:");
            case "bienvenue" -> id.equals("accueil:rester");
            case "demain" -> id.equals("accueil:demain");
            default -> id.equals(w);
        }).toList();
    }

    /** « jour » (avant la 1.2) = la récompense d'aujourd'hui ; « jour:&lt;id&gt; » = celle du jour où les tâches ont été finies. */
    static Settings.DailyVariant variant(Settings cfg, String id, java.time.LocalDate today) {
        return id.startsWith("jour:") ? cfg.variantById(id.substring(5)) : cfg.variantFor(today);
    }

    /** Ce que donnent ces cadeaux, une ligne par lot, les lots communs regroupés (« 2 fois 25 exp »). */
    static List<String> texts(Settings cfg, Messages m, List<String> ids, java.time.LocalDate today) {
        List<String> out = new ArrayList<>();
        int days = 0;
        for (String id : ids) {
            if (isDaily(id)) {
                days++;
                Settings.DailyVariant v = variant(cfg, id, today);
                if (v != null) {
                    out.add(v.text());
                }
            } else if (id.startsWith("palier:")) {
                out.add(cfg.gain(step(id)));
            } else if (id.equals("accueil:rester")) {
                out.add(cfg.accueil().stay().text());
            } else if (id.equals("accueil:demain")) {
                out.add(cfg.accueil().tomorrow().text());
            }
        }
        if (days > 0 && !cfg.dailyRewardText().isBlank()) {
            out.add(days == 1 ? cfg.dailyRewardText()
                    : m.text("recompense-plusieurs", "n", Integer.toString(days), "texte", cfg.dailyRewardText()));
        }
        out.removeIf(String::isBlank);
        return out;
    }

    static int step(String id) {
        try {
            return Integer.parseInt(id.substring("palier:".length()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Sorte d'un cadeau pour les moments : jour, palier, bienvenue ou demain ; vide si l'identifiant est inconnu. */
    static String sorte(String id) {
        if (isDaily(id)) {
            return "jour";
        }
        if (id.startsWith("palier:")) {
            return "palier";
        }
        return switch (id) {
            case "accueil:rester" -> "bienvenue";
            case "accueil:demain" -> "demain";
            default -> "";
        };
    }

    /** Donne les cadeaux désignés ; faux (et un message) s'il n'y a rien à prendre. */
    static boolean claim(SHGuide plugin, Player p, String what) {
        Sessions.Session s = plugin.sessions().get(p.getUniqueId());
        List<String> picked = s == null ? List.of() : pick(s.state.pending, what);
        if (picked.isEmpty()) {
            plugin.messages().send(p, "rien-a-prendre");
            plugin.sound(p, "refus");
            return false;
        }
        // retirés et enregistrés avant d'être donnés : un clic répété ne redonne rien
        for (String id : picked) {
            s.state.pending.remove(id);
        }
        plugin.sessions().saveNow(p);
        Settings cfg = plugin.settings();
        List<String> gains = texts(cfg, plugin.messages(), picked, plugin.today());
        AccueilConfig ac = cfg.accueil();
        boolean daily = false;
        for (String id : picked) {
            if (id.equals("accueil:rester") || id.equals("accueil:demain")) {
                plugin.runCommands(p, id.equals("accueil:rester") ? ac.stay().commands() : ac.tomorrow().commands());
            } else if (isDaily(id)) {
                daily = true;
                plugin.runCommands(p, cfg.dailyReward());
                Settings.DailyVariant v = variant(cfg, id, plugin.today());
                if (v != null) {
                    plugin.runCommands(p, v.commands());
                }
            } else if (id.startsWith("palier:")) {
                plugin.runCommands(p, cfg.steps().getOrDefault(step(id), List.of()));
                plugin.messages().send(p, "recompense-palier", "jour", Integer.toString(step(id)), "gain", cfg.gain(step(id)));
            }
        }
        Messages m = plugin.messages();
        boolean onlyStep = picked.stream().allMatch(id -> id.startsWith("palier:"));
        if (!onlyStep) {
            String gain = gains.isEmpty() ? "" : m.text("chat.recompense-gain", "texte", String.join(m.text("recompense-separateur"), gains));
            int serie = Streak.display(s.state, plugin.today());
            m.send(p, daily ? "recompense" : "recompense-accueil", "gain", gain,
                    "serie", Texte.plural(Integer.toString(serie), m.text("mots.jour"), m.text("mots.jours")));
        }
        plugin.sound(p, "recompense");
        plugin.sessions().refresh(p);
        for (String id : picked) {
            String sorte = sorte(id);
            if (!sorte.isEmpty()) {
                plugin.moments().fire(p, Moments.CADEAU_PRIS, Map.of("sorte", sorte));
            }
        }
        return true;
    }
}
