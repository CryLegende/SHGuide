package org.Cry_Legende.sHGuide;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

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
     * Moment « annonce » : une fois par annonce et par joueur, la première fois où elle se voit pour lui (barre ou
     * objectif du moment, selon ce qui s'affiche le plus tôt). Un joueur qui se reconnecte pendant l'annonce ne la
     * revoit pas ; un redémarrage ou un rechargement du plugin oublie ce qui a été dit. Elle part pour « moments-par-seconde »
     * joueurs au plus à chaque seconde : les autres attendent leur tour, sans qu'un rappel lance toutes les commandes d'un coup.
     */
    private void tell(Settings cfg, Config b, long now) {
        Annonces.Active seen = plugin.annonces().current(now, Math.max(b.enabled() ? b.beforeMinutes() : 0, cfg.annonceBeforeMinutes()),
                cfg.annonceGraceMinutes());
        if (seen == null || !plugin.moments().active(Moments.ANNONCE)) {
            told.clear();
            return;
        }
        List<Player> next = toTell(Bukkit.getOnlinePlayers(), Player::getUniqueId, told, seen.key(), cfg.momentsPerSecond());
        if (next.isEmpty()) {
            return;
        }
        Map<String, String> tokens = Map.of("nom", seen.text(), "minutes", Long.toString(Annonces.minutesLeft(seen, now)));
        for (Player p : next) {
            plugin.moments().fire(p, Moments.ANNONCE, tokens);
        }
    }

    /**
     * Au plus « limit » joueurs à qui cette annonce n'a pas encore été dite, dans l'ordre : leur clé est écrite dans « told »
     * seulement maintenant, ceux qui n'ont pas eu leur tour restent à dire.
     */
    static <T> List<T> toTell(Iterable<? extends T> online, Function<? super T, UUID> id, Map<UUID, String> told, String key, int limit) {
        List<T> out = new ArrayList<>();
        for (T player : online) {
            if (out.size() >= limit) {
                break;
            }
            if (!key.equals(told.get(id.apply(player)))) {
                told.put(id.apply(player), key);
                out.add(player);
            }
        }
        return out;
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
