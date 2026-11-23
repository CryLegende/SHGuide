package org.Cry_Legende.sHGuide;

import com.google.gson.Gson;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/** Joueurs connectés : chargement, vérification régulière, retours visibles (messages, sons) et sauvegardes. */
final class Sessions implements Listener {

    private static final Gson GSON = new Gson();
    private static final long SAVE_EVERY_MILLIS = 60_000;

    static final class Session {
        PlayerState state;
        Programme.Tick last;
        Objectives.Choice objective;
        List<Settings.News> news = List.of();
        boolean dirty;
        long lastSave;
        long joinedAt = System.currentTimeMillis();
    }

    private final SHGuide plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    /** Dernière venue gardée par le serveur, lue avant que la connexion ne la remplace. */
    private final Map<UUID, Long> serverLastSeen = new ConcurrentHashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> new Thread(r, "SHGuide-SQL"));
    private int cursor;

    Sessions(SHGuide plugin) {
        this.plugin = plugin;
    }

    Session get(UUID id) {
        return sessions.get(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            serverLastSeen.put(e.getUniqueId(), Bukkit.getOfflinePlayer(e.getUniqueId()).getLastSeen());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        load(e.getPlayer());
    }

    /** Charge le guide du joueur hors du fil principal, puis ouvre sa session. Aussi appelée au démarrage. */
    void load(Player p) {
        UUID id = p.getUniqueId();
        String name = p.getName();
        // Faux pendant toute la première session, même après un rechargement du plugin.
        boolean firstVisit = !p.hasPlayedBefore();
        io.execute(() -> {
            PlayerState stored;
            try {
                stored = plugin.store().load(id);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Lecture du guide de " + name + " impossible", ex);
                return;
            }
            PlayerState state = stored == null ? PlayerState.fresh(name) : stored;
            state.name = name;
            Long seen = serverLastSeen.remove(id);
            if (stored == null && seen != null) {
                Retour.seedLastSeen(state, seen, plugin.settings().zone());
            }
            if (stored == null && firstVisit && plugin.settings().accueil().enabled()) {
                state.firstDay = plugin.today().toString();
            }
            plugin.sync(() -> {
                if (!p.isOnline()) {
                    return;
                }
                Session session = new Session();
                session.state = state;
                sessions.put(id, session);
                boolean back = plugin.retour().onSessionReady(p, session);
                plugin.accueil().onSessionReady(p, session, back);
                refresh(p);
            });
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        serverLastSeen.remove(e.getPlayer().getUniqueId());
        plugin.accueil().forget(e.getPlayer());
        plugin.annonceBarre().forget(e.getPlayer());
        plugin.fleche().clear(e.getPlayer());
        Session s = sessions.remove(e.getPlayer().getUniqueId());
        if (s != null) {
            s.state.lastSeen = plugin.today().toString();
            save(e.getPlayer().getUniqueId(), s);
        }
    }

    /** Appelée toutes les secondes : chaque joueur est vérifié une fois par « verification-secondes ». */
    void tick() {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (!online.isEmpty()) {
            int per = Math.max(1, (int) Math.ceil(online.size() / (double) plugin.settings().checkSeconds()));
            for (int n = 0; n < per && n < online.size(); n++) {
                cursor = (cursor + 1) % online.size();
                refresh(online.get(cursor));
            }
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Session> en : sessions.entrySet()) {
            Session s = en.getValue();
            if (s.dirty && now - s.lastSave > SAVE_EVERY_MILLIS) {
                save(en.getKey(), s);
            }
        }
    }

    /** Met à jour le programme et l'objectif du joueur maintenant ; null si son guide n'est pas chargé. */
    Programme.Tick refresh(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null) {
            return null;
        }
        Settings cfg = plugin.settings();
        Messages m = plugin.messages();
        Resolver r = text -> PlaceholderAPI.setPlaceholders(p, text);
        LocalDate today = plugin.today();
        boolean canBaseline = System.currentTimeMillis() - s.joinedAt >= cfg.joinDelaySeconds() * 1000L;
        Programme.Tick t = Programme.update(s.state, cfg, r, today, canBaseline);
        s.last = t;
        if (t.dirty()) {
            s.dirty = true;
        }
        if (!t.newlyCompleted()) {
            int left = (int) t.tasks().stream().filter(v -> !v.done()).count();
            for (Programme.TaskView v : t.newlyDone()) {
                String reste = left == 1 ? m.text("chat.reste-une") : m.text("chat.reste-plusieurs", "n", Integer.toString(left));
                m.send(p, "tache-faite", "tache", v.text(), "reste", reste);
                plugin.sound(p, "tache");
            }
        } else {
            m.send(p, "programme-complet");
            plugin.sound(p, "programme");
        }
        long now = System.currentTimeMillis();
        Annonces.Active a = plugin.annonces().current(now, cfg.annonceBeforeMinutes(), cfg.annonceGraceMinutes());
        Annonces.Active shown = a == null ? null : new Annonces.Active(Annonces.label(a, now,
                m.text("objectif.annonce-bientot"), m.text("objectif.annonce-commence")), a.click(), a.startMillis());
        Map<String, String> tokens = new HashMap<>(Objectives.tokens(t.tasks(), !s.state.pending.isEmpty(), shown,
                cfg.nearRatio(), m.text("objectif.presque")));
        tokens.put("joueur", p.getName());
        AccueilConfig ac = cfg.accueil();
        AccueilConfig.Step step = Accueil.tutorialActive(s.state, ac) ? Accueil.currentStep(s.state, ac) : null;
        tokens.put("tuto", step == null ? "non" : "oui");
        tokens.put("tuto_texte", step == null ? "" : step.text());
        tokens.put("tuto_clic", step == null ? "" : step.click());
        tokens.put("tuto_description", step == null || step.lore().isEmpty() ? "" : step.lore().get(0));
        tokens.put("tuto_n", step == null ? "" : Integer.toString(Accueil.stepNumber(s.state, ac)));
        tokens.put("tuto_total", Integer.toString(ac.tutorial().steps().size()));
        s.objective = Objectives.pick(cfg.objectives(), tokens, r);
        return t;
    }

    void markDirty(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s != null) {
            s.dirty = true;
        }
    }

    /** Copie l'état (le fil principal continue de le modifier) et l'écrit hors du fil principal, dans l'ordre. */
    private void save(UUID id, Session s) {
        s.dirty = false;
        s.lastSave = System.currentTimeMillis();
        PlayerState copy = GSON.fromJson(GSON.toJson(s.state), PlayerState.class).normalize();
        io.execute(() -> {
            try {
                plugin.store().save(id, copy);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Sauvegarde du guide de " + copy.name + " impossible", ex);
            }
        });
    }

    /** Arrêt : tout est écrit avant de fermer la base. */
    void saveAll() {
        for (Map.Entry<UUID, Session> en : sessions.entrySet()) {
            en.getValue().state.lastSeen = plugin.today().toString();
            save(en.getKey(), en.getValue());
        }
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Sauvegardes du guide pas terminées après 10 s.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
