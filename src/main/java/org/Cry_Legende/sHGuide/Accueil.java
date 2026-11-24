package org.Cry_Legende.sHGuide;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Accueil des nouveaux joueurs : cadeau pour être resté « minutes » minutes, cadeau du lendemain annoncé dès le premier
 * jour, tutoriel en étapes avec sa barre et sa flèche. Les cadeaux passent par les récompenses de Jake (/jake).
 */
final class Accueil {

    private final SHGuide plugin;
    private final Map<UUID, BossBar> stayBars = new HashMap<>();
    private final Map<UUID, BossBar> tomorrowBars = new HashMap<>();
    private final Map<UUID, Long> tomorrowUntil = new HashMap<>();
    private final Map<UUID, BossBar> tutoBars = new HashMap<>();
    private int ticks;
    private int seconds;

    Accueil(SHGuide plugin) {
        this.plugin = plugin;
    }

    /** Jours depuis le premier jour ; -1 pour un joueur venu avant l'accueil. */
    static long daysSinceFirst(PlayerState s, LocalDate today) {
        return s.firstDay == null ? -1 : ChronoUnit.DAYS.between(LocalDate.parse(s.firstDay), today);
    }

    static boolean stayActive(PlayerState s, AccueilConfig c, LocalDate today) {
        long days = daysSinceFirst(s, today);
        return c.enabled() && days >= 0 && days <= c.tomorrow().days() && !s.welcome.contains("rester")
                && c.stay().minutes() > 0 && !c.stay().commands().isEmpty();
    }

    static boolean tutorialActive(PlayerState s, AccueilConfig c) {
        return c.enabled() && s.firstDay != null && !s.tutoFinished && !c.tutorial().steps().isEmpty();
    }

    /** Première étape pas encore faite ; null si tout est fait. */
    static AccueilConfig.Step currentStep(PlayerState s, AccueilConfig c) {
        for (AccueilConfig.Step st : c.tutorial().steps()) {
            if (!s.tutoDone.contains(st.id())) {
                return st;
            }
        }
        return null;
    }

    /** Numéro (1 à total) de l'étape en cours. */
    static int stepNumber(PlayerState s, AccueilConfig c) {
        List<AccueilConfig.Step> steps = c.tutorial().steps();
        for (int i = 0; i < steps.size(); i++) {
            if (!s.tutoDone.contains(steps.get(i).id())) {
                return i + 1;
            }
        }
        return steps.size();
    }

    static String clock(int totalSeconds) {
        int s = Math.max(0, totalSeconds);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    /** Session prête : le cadeau du lendemain attend le joueur revenu un autre jour. */
    void onSessionReady(Player p, Sessions.Session s, boolean back) {
        AccueilConfig c = plugin.settings().accueil();
        if (!c.enabled() || s.state.firstDay == null) {
            return;
        }
        LocalDate today = plugin.today();
        long days = daysSinceFirst(s.state, today);
        AccueilConfig.Tomorrow t = c.tomorrow();
        if (days == 0 && s.state.welcome.contains("rester") && !t.commands().isEmpty() && t.barSeconds() > 0) {
            tomorrowUntil.put(p.getUniqueId(), System.currentTimeMillis() + t.barSeconds() * 1000L);
        }
        if (days < 1 || days > t.days() || s.state.welcome.contains("demain") || t.commands().isEmpty()) {
            return;
        }
        s.state.welcome.add("demain");
        s.state.pending.add("accueil:demain");
        s.dirty = true;
        if (back) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            plugin.sound(p, "cadeau");
            Resolver r = text -> PlaceholderAPI.setPlaceholders(p, text);
            if (!t.dialogIf().isEmpty() && !t.dialog().isEmpty() && Condition.test(t.dialogIf(), r)) {
                plugin.runCommands(p, t.dialog());
                return;
            }
            Messages m = plugin.messages();
            m.send(p, "accueil-demain-pret", "gain", t.text());
            m.sendClickable(p, "retour.bouton", "guide");
        }, t.delayTicks());
    }

    void forget(Player p) {
        UUID id = p.getUniqueId();
        stayBars.remove(id);
        tomorrowBars.remove(id);
        tomorrowUntil.remove(id);
        tutoBars.remove(id);
    }

    void hideAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            hide(stayBars, p);
            hide(tomorrowBars, p);
            hide(tutoBars, p);
        }
    }

    /** À chaque tick : un vingtième des joueurs, chacun une fois par seconde. */
    void tick() {
        ticks++;
        if (ticks % 20 == 0) {
            seconds++;
        }
        AccueilConfig c = plugin.settings().accueil();
        LocalDate today = plugin.today();
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if ((p.getEntityId() + ticks) % 20 != 0) {
                continue;
            }
            Sessions.Session s = plugin.sessions().get(p.getUniqueId());
            if (s == null || s.state.firstDay == null) {
                continue;
            }
            tickStay(p, s, c, today);
            Long until = tomorrowUntil.get(p.getUniqueId());
            if (until != null && until > now && c.enabled()) {
                show(tomorrowBars, p, plugin.messages().text("accueil.barre-demain", "gain", c.tomorrow().text()),
                        1f, c.tomorrow().color());
            } else {
                tomorrowUntil.remove(p.getUniqueId());
                hide(tomorrowBars, p);
            }
            if (tutorialActive(s.state, c)) {
                if (seconds % c.tutorial().checkSeconds() == 0) {
                    checkTutorial(p, s, c);
                }
            }
            updateTutoBar(p, s, c);
        }
    }

    private void tickStay(Player p, Sessions.Session s, AccueilConfig c, LocalDate today) {
        if (!stayActive(s.state, c, today)) {
            hide(stayBars, p);
            return;
        }
        AccueilConfig.Stay st = c.stay();
        Resolver r = text -> PlaceholderAPI.setPlaceholders(p, text);
        boolean counts = st.countIf().isEmpty() || Condition.test(st.countIf(), r);
        if (counts) {
            s.state.staySeconds++;
            s.dirty = true;
        }
        int total = st.minutes() * 60;
        int left = total - s.state.staySeconds;
        if (left > 0) {
            String key = counts ? "accueil.barre-rester" : "accueil.barre-rester-pause";
            show(stayBars, p, plugin.messages().text(key, "gain", st.text(), "temps", clock(left)),
                    Math.min(1f, s.state.staySeconds / (float) total), st.color());
            return;
        }
        s.state.welcome.add("rester");
        s.state.pending.add("accueil:rester");
        s.dirty = true;
        hide(stayBars, p);
        Messages m = plugin.messages();
        m.send(p, "accueil-rester", "gain", st.text(), "minutes", Integer.toString(st.minutes()));
        m.sendClickable(p, "retour.bouton", "guide");
        plugin.sound(p, "cadeau");
        AccueilConfig.Tomorrow t = c.tomorrow();
        if (!t.commands().isEmpty() && daysSinceFirst(s.state, today) == 0) {
            m.send(p, "accueil-demain-annonce", "gain", t.text());
            if (t.barSeconds() > 0) {
                tomorrowUntil.put(p.getUniqueId(), System.currentTimeMillis() + t.barSeconds() * 1000L);
            }
        }
    }

    /** Fait passer les étapes dont les conditions sont vraies (plusieurs à la suite si le joueur était en avance). */
    void checkTutorial(Player p, Sessions.Session s, AccueilConfig c) {
        Resolver r = text -> PlaceholderAPI.setPlaceholders(p, text);
        Messages m = plugin.messages();
        int total = c.tutorial().steps().size();
        for (int guard = 0; guard <= total; guard++) {
            AccueilConfig.Step st = currentStep(s.state, c);
            if (st == null) {
                finish(p, s, c);
                return;
            }
            if (!st.id().equals(s.state.tutoStarted)) {
                s.state.tutoStarted = st.id();
                s.dirty = true;
                plugin.runCommands(p, st.start());
            }
            boolean done = true;
            for (String cond : st.doneIf()) {
                if (!Condition.test(cond, r)) {
                    done = false;
                    break;
                }
            }
            if (!done) {
                plugin.fleche().setTarget(p, s.state.arrowOff ? null : target(st, r), st.targetName());
                return;
            }
            int n = stepNumber(s.state, c);
            s.state.tutoDone.add(st.id());
            s.dirty = true;
            plugin.runCommands(p, st.reward());
            String gain = st.rewardText().isEmpty() ? "" : m.text("chat.tutoriel-gain", "texte", st.rewardText());
            m.send(p, "tutoriel-etape", "n", Integer.toString(n), "total", Integer.toString(total),
                    "texte", st.text(), "gain", gain);
            plugin.sound(p, "etape");
            AccueilConfig.Step next = currentStep(s.state, c);
            if (next != null) {
                m.send(p, "tutoriel-suivante", "n", Integer.toString(n + 1), "total", Integer.toString(total),
                        "texte", next.text());
            }
            plugin.fleche().setTarget(p, null, "");
        }
    }

    private void finish(Player p, Sessions.Session s, AccueilConfig c) {
        s.state.tutoFinished = true;
        s.dirty = true;
        hide(tutoBars, p);
        plugin.fleche().setTarget(p, null, "");
        plugin.runCommands(p, c.tutorial().end());
        plugin.messages().send(p, "tutoriel-fini");
        plugin.sound(p, "tutoriel-fin");
    }

    private void updateTutoBar(Player p, Sessions.Session s, AccueilConfig c) {
        AccueilConfig.Step st = tutorialActive(s.state, c) ? currentStep(s.state, c) : null;
        if (st == null) {
            hide(tutoBars, p);
            return;
        }
        int total = c.tutorial().steps().size();
        int n = stepNumber(s.state, c);
        show(tutoBars, p, plugin.messages().text("accueil.barre-tutoriel", "n", Integer.toString(n),
                "total", Integer.toString(total), "texte", st.text()), (n - 1) / (float) total, c.tutorial().color());
    }

    /** Cible de l'étape après les placeholders ; null si elle n'en a pas ou si elle est illisible. */
    static Location target(AccueilConfig.Step st, Resolver r) {
        return st.target().isBlank() ? null : Fleche.place(r.resolve(st.target()));
    }

    private void show(Map<UUID, BossBar> bars, Player p, String text, float progress, BossBar.Color color) {
        BossBar bar = bars.get(p.getUniqueId());
        float clamped = Math.max(0f, Math.min(1f, progress));
        if (bar == null) {
            bar = BossBar.bossBar(Messages.chat(text), clamped, color, BossBar.Overlay.PROGRESS);
            bars.put(p.getUniqueId(), bar);
            p.showBossBar(bar);
            return;
        }
        bar.name(Messages.chat(text));
        bar.progress(clamped);
        bar.color(color);
    }

    private static void hide(Map<UUID, BossBar> bars, Player p) {
        BossBar bar = bars.remove(p.getUniqueId());
        if (bar != null) {
            p.hideBossBar(bar);
        }
    }
}
