package org.Cry_Legende.sHGuide;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/** Accueil d'un joueur revenu après « retour.jours » jours ou plus : dialogue, ou chat pour les anciennes versions. */
final class Retour {

    private final SHGuide plugin;

    Retour(SHGuide plugin) {
        this.plugin = plugin;
    }

    static boolean isReturn(String lastSeen, LocalDate today, int days) {
        return lastSeen != null && ChronoUnit.DAYS.between(LocalDate.parse(lastSeen), today) >= days;
    }

    /**
     * Joueur sans ligne dans le guide (premier passage depuis l'arrivée du guide) : sa dernière venue est celle que le
     * serveur a gardée, pour qu'un ancien joueur de retour soit accueilli dès le premier jour.
     */
    static void seedLastSeen(PlayerState s, long lastSeenMillis, java.time.ZoneId zone) {
        if (s.lastSeen == null && lastSeenMillis > 0) {
            s.lastSeen = java.time.Instant.ofEpochMilli(lastSeenMillis).atZone(zone).toLocalDate().toString();
        }
    }

    /** Nouveautés parues après la dernière venue et pas après aujourd'hui, les plus récentes d'abord. */
    static List<Settings.News> newsSince(List<Settings.News> all, String lastSeen, LocalDate today, int max) {
        if (lastSeen == null) {
            return List.of();
        }
        LocalDate seen = LocalDate.parse(lastSeen);
        return all.stream().filter(n -> n.date().isAfter(seen) && !n.date().isAfter(today))
                .sorted(Comparator.comparing(Settings.News::date).reversed()).limit(max).toList();
    }

    void onSessionReady(Player p, Sessions.Session s) {
        Settings cfg = plugin.settings();
        LocalDate today = plugin.today();
        String previous = s.state.lastSeen;
        boolean back = isReturn(previous, today, cfg.returnDays());
        s.news = back ? newsSince(cfg.news(), previous, today, cfg.newsMax()) : List.of();
        if (!today.toString().equals(previous)) {
            s.state.lastSeen = today.toString();
            s.dirty = true;
        }
        if (!back) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            plugin.sessions().refresh(p);
            Resolver r = text -> PlaceholderAPI.setPlaceholders(p, text);
            if (!cfg.dialogCondition().isEmpty() && Condition.test(cfg.dialogCondition(), r)) {
                plugin.runCommands(p, cfg.dialogCommands());
                return;
            }
            Messages m = plugin.messages();
            String objective = s.objective == null ? m.text("objectif.aucun") : s.objective.text();
            for (String line : m.lines("retour.chat", "joueur", p.getName(), "objectif", objective)) {
                p.sendMessage(Messages.chat(line));
            }
            for (Settings.News n : s.news) {
                p.sendMessage(Messages.chat(m.text("retour.nouveaute", "texte", n.text())));
            }
            m.sendClickable(p, "retour.bouton", "guide");
        }, cfg.returnDelayTicks());
    }
}
