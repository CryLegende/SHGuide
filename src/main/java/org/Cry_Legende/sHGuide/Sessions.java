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
    private static final long RECENT_MILLIS = 1_000;

    static final class Session {
        PlayerState state;
        Programme.Tick last;
        long checkedAt;
        final Lecteur reader = new Lecteur(System::currentTimeMillis);
        Objectives.Choice objective;
        /** Nouveautés sorties depuis la dernière venue (dialogue de retour). */
        List<Nouveautes.Entree> news = List.of();
        /** Revenu après une absence, et n'a pas encore ouvert Jake. */
        boolean back;
        /** Textes calculés à chaque vérification, lus tels quels par les placeholders. */
        String shortObjective = "";
        String scoreboardLine = "";
        String alert = "";
        int unread;
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
    private int ticks;
    private double due;

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
        Player p = e.getPlayer();
        plugin.onMain(() -> {
            serverLastSeen.remove(p.getUniqueId());
            plugin.accueil().forget(p);
            plugin.annonceBarre().forget(p);
            plugin.fleche().clear(p);
            Session s = sessions.remove(p.getUniqueId());
            if (s != null) {
                s.state.lastSeen = plugin.today().toString();
                save(p.getUniqueId(), s);
            }
        });
    }

    /**
     * À chaque tick : chaque joueur est vérifié une fois par « verification-secondes », sans jamais dépasser
     * « verification-par-tick » joueurs (au-delà, chacun attend son tour).
     */
    void tick() {
        int count = Bukkit.getOnlinePlayers().size();
        if (count > 0) {
            due = Math.min(plugin.settings().checkPerTick(), due + count / (20.0 * plugin.settings().checkSeconds()));
            if (due >= 1) {
                List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
                for (; due >= 1; due--) {
                    cursor = (cursor + 1) % online.size();
                    refresh(online.get(cursor));
                }
            }
        }
        if (++ticks % 20 != 0) {
            return;
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
        return refresh(p, false);
    }

    /** « now » : relit aussi les placeholders coûteux (placeholders-lents). */
    private Programme.Tick refresh(Player p, boolean now) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null) {
            return null;
        }
        Settings cfg = plugin.settings();
        Messages m = plugin.messages();
        Resolver r = s.reader.with(text -> PlaceholderAPI.setPlaceholders(p, text), cfg.slowPlaceholders(), now);
        LocalDate today = plugin.today();
        boolean canBaseline = System.currentTimeMillis() - s.joinedAt >= cfg.joinDelaySeconds() * 1000L;
        int giftsBefore = s.state.pending.size();
        Programme.Tick t = Programme.update(s.state, cfg, r, today, canBaseline);
        s.last = t;
        s.checkedAt = System.currentTimeMillis();
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
        long time = System.currentTimeMillis();
        Annonces.Active a = plugin.annonces().current(time, cfg.annonceBeforeMinutes(), cfg.annonceGraceMinutes());
        Annonces.Active shown = a == null ? null : new Annonces.Active(Annonces.label(a, time,
                m.text("objectif.annonce-bientot"), m.text("objectif.annonce-commence")), a.click(), a.startMillis());
        Map<String, String> tokens = new HashMap<>(Objectives.tokens(t.tasks(), !s.state.pending.isEmpty(), shown,
                cfg.nearRatio(), m.text("objectif.presque")));
        tokens.put("joueur", p.getName());
        AccueilConfig ac = cfg.accueil();
        AccueilConfig.Step step = Accueil.tutorialActive(s.state, ac) ? Accueil.currentStep(s.state, ac) : null;
        tokens.put("tuto", step == null ? "non" : "oui");
        tokens.put("tuto_texte", step == null ? "" : step.text());
        tokens.put("tuto_clic", step == null ? "" : step.click());
        tokens.put("tuto_description", step == null ? "" : String.join("\n", step.lore()));
        tokens.put("tuto_n", step == null ? "" : Integer.toString(Accueil.stepNumber(s.state, ac)));
        tokens.put("tuto_total", Integer.toString(ac.tutorial().steps().size()));
        s.objective = Objectives.pick(cfg.objectives(), tokens, r);
        view(p, s);
        // après le calcul : les placeholders lus par les commandes des moments montrent déjà le nouvel état
        plugin.moments().fire(p, Moments.after(s.state, giftsBefore, t, today));
        return t;
    }

    /** Pour un écran : tout est relu, sauf si la dernière vérification date de moins d'une seconde (d'un écran à l'autre). */
    Programme.Tick recent(Player p) {
        Session s = sessions.get(p.getUniqueId());
        return s != null && s.last != null && System.currentTimeMillis() - s.checkedAt < RECENT_MILLIS ? s.last : refresh(p, true);
    }

    /** Recalcule ce que lisent les placeholders : objectif court, ligne du scoreboard, alerte au-dessus de Jake. */
    void view(Player p, Session s) {
        Settings cfg = plugin.settings();
        Messages m = plugin.messages();
        LocalDate today = plugin.today();
        s.shortObjective = s.objective == null ? m.text("objectif.aucun") : s.objective.shortText(cfg.objectiveMax());
        s.unread = plugin.nouveautes().unread(s.state, today, cfg.newsRecentDays()).size();
        if (s.last == null) {
            return;
        }
        int total = s.last.tasks().size();
        long done = s.last.tasks().stream().filter(Programme.TaskView::done).count();
        int serie = Streak.display(s.state, today);
        AccueilConfig ac = cfg.accueil();
        Map<String, String> tk = new HashMap<>();
        tk.put("joueur", p.getName());
        tk.put("faites", Long.toString(done));
        tk.put("total", Integer.toString(total));
        tk.put("serie", Integer.toString(serie));
        tk.put("cadeaux", Integer.toString(s.state.pending.size()));
        tk.put("nouveautes", Integer.toString(s.unread));
        tk.put("tuto_n", Integer.toString(Accueil.stepNumber(s.state, ac)));
        tk.put("tuto_total", Integer.toString(ac.tutorial().steps().size()));
        Texte.Contexte c = new Texte.Contexte(tk, Map.of(), Map.of(), text -> text, plugin.menus().bar());
        String line = Accueil.tutorialActive(s.state, ac) ? "scoreboard.tutoriel"
                : !s.state.pending.isEmpty() ? "scoreboard.cadeau" : serie > 0 ? "scoreboard.serie" : "scoreboard.taches";
        s.scoreboardLine = Texte.line(m.text(line), c);
        String alert = !s.state.pending.isEmpty() ? "alerte.cadeau" : s.unread > 0 ? "alerte.nouveautes"
                : !today.toString().equals(s.state.alertSeen) ? "alerte.taches" : "";
        s.alert = alert.isEmpty() ? "" : Texte.line(m.text(alert), c);
    }

    /** Lignes du retour guidé : nouveautés depuis la dernière venue, cadeau qui attend, objectif (messages.yml, retour.lignes). */
    List<String> returnText(Player p, Session s) {
        Settings cfg = plugin.settings();
        int shown = Math.min(cfg.newsMax(), s.news.size());
        List<String> list = new ArrayList<>();
        for (Nouveautes.Entree n : s.news.subList(0, shown)) {
            list.add(plugin.messages().text("retour.nouveaute", "titre", n.title()));
        }
        Map<String, String> tk = new HashMap<>();
        tk.put("joueur", p.getName());
        tk.put("nouveautes", Integer.toString(s.news.size()));
        tk.put("autres", Integer.toString(s.news.size() - shown));
        tk.put("cadeaux", Integer.toString(s.state.pending.size()));
        Objectives.Choice o = s.objective;
        tk.put("objectif", o == null ? plugin.messages().text("objectif.aucun") : o.todo().isEmpty() ? o.text() : o.todo());
        return Texte.lines(plugin.messages().lines("retour.lignes"), new Texte.Contexte(tk, Map.of(),
                Map.of("nouveautes_liste", list), text -> PlaceholderAPI.setPlaceholders(p, text), plugin.menus().bar()));
    }

    /** Écrit l'état du joueur sans attendre (cadeaux retirés : un arrêt brutal ne les redonne pas). */
    void saveNow(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s != null) {
            save(p.getUniqueId(), s);
        }
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
