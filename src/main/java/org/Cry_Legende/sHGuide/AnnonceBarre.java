package org.Cry_Legende.sHGuide;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Barre en haut de l'écran de tout le serveur pour l'event annoncé (/shguide annonce, envoyé par les rappels du bot
 * des évènements) : compte à rebours pendant les « minutes-avant » qui précèdent son début, puis « commence » pendant
 * « annonce-apres-debut-minutes ». Une seule barre, partagée par tous les joueurs.
 */
final class AnnonceBarre {

    record Config(boolean enabled, int beforeMinutes, BossBar.Color color) {

        static Config read(ConfigurationSection c) {
            if (c == null) {
                return new Config(false, 30, BossBar.Color.PURPLE);
            }
            BossBar.Color color;
            try {
                color = BossBar.Color.valueOf(c.getString("couleur", "PURPLE").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("annonce-barre.couleur inconnue : " + c.getString("couleur"));
            }
            return new Config(c.getBoolean("actif", true), Math.max(1, c.getInt("minutes-avant", 30)), color);
        }
    }

    private final SHGuide plugin;
    private BossBar bar;
    private final Set<UUID> shown = new HashSet<>();
    /** Annonce déjà dite à chaque joueur (moment « annonce ») : vidée quand plus aucune annonce ne se voit. */
    private final Map<UUID, String> told = new HashMap<>();

    AnnonceBarre(SHGuide plugin) {
        this.plugin = plugin;
    }

    /** Toutes les secondes. */
    void tick() {
        Settings cfg = plugin.settings();
        Config b = cfg.annonceBar();
        long now = System.currentTimeMillis();
        tell(cfg, b, now);
        Annonces.Active a = b.enabled() ? plugin.annonces().current(now, b.beforeMinutes(), cfg.annonceGraceMinutes()) : null;
        if (a == null) {
            hideAll();
            return;
        }
        long left = a.startMillis() - now;
        Messages m = plugin.messages();
        String text = left > 0
                ? m.text("annonce.barre", "texte", a.text(), "temps", clock(left / 1000))
                : m.text("annonce.barre-commence", "texte", a.text());
        float progress = left > 0 ? Math.max(0f, Math.min(1f, 1f - left / (b.beforeMinutes() * 60_000f))) : 1f;
        if (bar == null) {
            bar = BossBar.bossBar(Messages.chat(text), progress, b.color(), BossBar.Overlay.PROGRESS);
        } else {
            bar.name(Messages.chat(text));
            bar.progress(progress);
            bar.color(b.color());
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (shown.add(p.getUniqueId())) {
                p.showBossBar(bar);
            }
        }
    }

    /**
     * Moment « annonce » : une fois par annonce et par joueur, la première seconde où elle se voit pour lui (barre ou
     * objectif du moment, selon ce qui s'affiche le plus tôt). Un joueur qui se reconnecte pendant l'annonce ne la
     * revoit pas.
     */
    private void tell(Settings cfg, Config b, long now) {
        Annonces.Active seen = plugin.annonces().current(now, Math.max(b.enabled() ? b.beforeMinutes() : 0, cfg.annonceBeforeMinutes()),
                cfg.annonceGraceMinutes());
        if (seen == null || !plugin.moments().active(Moments.ANNONCE)) {
            told.clear();
            return;
        }
        String key = seen.key();
        Map<String, String> tokens = null;
        // une copie : une commande de moment peut faire partir un joueur
        for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            if (key.equals(told.put(p.getUniqueId(), key))) {
                continue;
            }
            if (tokens == null) {
                tokens = Map.of("nom", seen.text(), "minutes", Long.toString(Annonces.minutesLeft(seen, now)));
            }
            plugin.moments().fire(p, Moments.ANNONCE, tokens);
        }
    }

    void forget(Player p) {
        shown.remove(p.getUniqueId());
    }

    void hideAll() {
        if (bar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (shown.contains(p.getUniqueId())) {
                    p.hideBossBar(bar);
                }
            }
        }
        shown.clear();
        bar = null;
    }

    /** 754 s → « 12:34 », 3 900 s → « 1:05:00 ». */
    static String clock(long seconds) {
        long s = Math.max(0, seconds);
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
                : String.format("%d:%02d", s / 60, s % 60);
    }
}
