package org.Cry_Legende.sHGuide;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/** Accueil d'un joueur revenu après « retour.jours » jours ou plus : dialogue, ou chat pour les anciennes versions. */
final class Retour {

    private final SHGuide plugin;

    Retour(SHGuide plugin) {
        this.plugin = plugin;
    }

    static boolean isReturn(String lastSeen, LocalDate today, int days) {
        return lastSeen != null && daysAway(lastSeen, today) >= days;
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

    /** @return vrai si le joueur revient après une absence (dialogue de retour prévu). */
    boolean onSessionReady(Player p, Sessions.Session s) {
        Settings cfg = plugin.settings();
        LocalDate today = plugin.today();
        String previous = s.state.lastSeen;
        boolean back = isReturn(previous, today, cfg.returnDays());
        s.back = back;
        s.news = back ? plugin.nouveautes().since(previous, today) : java.util.List.of();
        if (!today.toString().equals(previous)) {
            s.state.lastSeen = today.toString();
            s.dirty = true;
        }
        if (!back) {
            return false;
        }
        long days = daysAway(previous, today);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            plugin.sessions().refresh(p);
            show(p, s, cfg);
            plugin.moments().fire(p, Moments.RETOUR, Map.of("jours", Long.toString(days)));
        }, cfg.returnDelayTicks());
        return true;
    }

    /** Jours entre la dernière venue et aujourd'hui. */
    static long daysAway(String lastSeen, LocalDate today) {
        return ChronoUnit.DAYS.between(LocalDate.parse(lastSeen), today);
    }

    /** Le dialogue de retour, ou les lignes du chat quand il n'est pas possible. */
    private void show(Player p, Sessions.Session s, Settings cfg) {
        Resolver r = text -> PlaceholderAPI.setPlaceholders(p, text);
        if (!cfg.dialogCondition().isEmpty() && Condition.test(cfg.dialogCondition(), r)) {
            plugin.runCommands(p, cfg.dialogCommands());
            return;
        }
        Messages m = plugin.messages();
        for (String line : m.lines("retour.chat", "joueur", p.getName())) {
            p.sendMessage(Messages.chat(line));
        }
        for (String line : plugin.sessions().returnText(p, s)) {
            p.sendMessage(Messages.chat(line));
        }
        boolean gift = !s.state.pending.isEmpty();
        m.sendClickable(p, gift ? "retour.bouton-cadeau" : s.news.isEmpty() ? "retour.bouton" : "retour.bouton-nouveautes",
                gift ? "guide recompenses" : s.news.isEmpty() ? "guide" : "guide nouveautes");
    }
}
