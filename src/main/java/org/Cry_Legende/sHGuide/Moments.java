package org.Cry_Legende.sHGuide;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.logging.Level;

/**
 * Moments du guide (section « moments » de config.yml) : à chacun, la liste de commandes du moment est lancée pour le
 * joueur concerné, sur le fil principal. Le plugin ne sait pas ce que font ces commandes. Une liste vide ou absente ne
 * fait rien. La récompense propre à une tâche (« recompense » de la tâche) part de la même façon, quand la tâche est faite.
 */
final class Moments {

    static final String TACHE_FAITE = "tache-faite";
    static final String PROGRAMME_COMPLET = "programme-complet";
    static final String PALIER = "palier";
    static final String CADEAU_PRET = "cadeau-pret";
    static final String CADEAU_PRIS = "cadeau-pris";
    static final String ETAPE_FAITE = "etape-faite";
    static final String TUTORIEL_FINI = "tutoriel-fini";
    static final String RETOUR = "retour";
    static final String ANNONCE = "annonce";

    /** Les moments que le plugin connaît, dans l'ordre de config.yml. */
    static final List<String> NAMES = List.of(TACHE_FAITE, PROGRAMME_COMPLET, PALIER, CADEAU_PRET, CADEAU_PRIS,
            ETAPE_FAITE, TUTORIEL_FINI, RETOUR, ANNONCE);

    /** Jetons faits de texte libre : privés du caractère % avant PlaceholderAPI, pour qu'un texte ne devienne jamais un placeholder. */
    static final Set<String> TEXT_TOKENS = Set.of("tache", "nom", "etape");

    /** Un moment qui vient d'arriver, avec ses jetons. */
    record Event(String name, Map<String, String> tokens) {
    }

    private final SHGuide plugin;

    Moments(SHGuide plugin) {
        this.plugin = plugin;
    }

    /** Vrai si le moment a des commandes dans la config actuelle. */
    boolean active(String moment) {
        return !plugin.settings().moments().getOrDefault(moment, List.of()).isEmpty();
    }

    void fire(Player p, String moment, Map<String, String> tokens) {
        List<String> commands = plugin.settings().moments().getOrDefault(moment, List.of());
        if (commands.isEmpty()) {
            return;
        }
        Map<String, String> all = new HashMap<>(tokens);
        all.put("joueur", p.getName());
        plugin.onMain(() -> run(p, "Moment « " + moment + " »", commands, all));
    }

    void fire(Player p, List<Event> events) {
        for (Event e : events) {
            fire(p, e.name(), e.tokens());
        }
    }

    /**
     * La récompense propre à chacune de ces tâches (celles que « rewarded » a retenues) : ses commandes partent pour le
     * joueur, sur le fil principal, {joueur} et les placeholders lus pour lui.
     */
    void reward(Player p, List<Programme.TaskView> tasks) {
        for (Programme.TaskView v : tasks) {
            List<String> commands = v.def().reward();
            Map<String, String> tokens = Map.of("joueur", p.getName());
            plugin.onMain(() -> run(p, "Récompense de la tâche « " + v.def().id() + " »", commands, tokens));
        }
    }

    private void run(Player p, String what, List<String> commands, Map<String, String> tokens) {
        if (!p.isOnline()) {
            return;
        }
        List<String> ready;
        try {
            ready = expand(commands, tokens, text -> PlaceholderAPI.setPlaceholders(p, text));
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, what + " : commandes illisibles pour " + p.getName(), e);
            return;
        }
        for (String command : ready) {
            plugin.dispatch(p, command);
        }
    }

    /**
     * Les commandes d'un moment, prêtes à partir : les {jetons} sont remplacés d'abord (ceux de texte libre sans le
     * caractère %), les placeholders (PlaceholderAPI, lus pour le joueur) ensuite, et les lignes vides disparaissent.
     */
    static List<String> expand(List<String> commands, Map<String, String> tokens, UnaryOperator<String> placeholders) {
        Map<String, String> safe = tokens;
        for (String key : TEXT_TOKENS) {
            String value = tokens.get(key);
            if (value != null && value.indexOf('%') >= 0) {
                if (safe == tokens) {
                    safe = new HashMap<>(tokens);
                }
                safe.put(key, value.replace("%", ""));
            }
        }
        List<String> out = new ArrayList<>(commands.size());
        for (String raw : commands) {
            String command = placeholders.apply(Tokens.apply(raw, safe)).trim();
            if (!command.isEmpty()) {
                out.add(command);
            }
        }
        return out;
    }

    /**
     * Les tâches que cette vérification vient de faire et qui ont une récompense à elles, dans l'ordre du programme, la
     * dernière tâche du jour comprise (contrairement au moment « tache-faite »). Une tâche n'est « venue d'être faite »
     * qu'une fois par jour : elle entre dans {@code Tick.newlyDone()} au moment où elle rejoint {@code PlayerState.done},
     * qui ne se vide qu'à minuit.
     */
    static List<Programme.TaskView> rewarded(Programme.Tick t) {
        return t.newlyDone().stream().filter(v -> !v.def().reward().isEmpty()).toList();
    }

    /**
     * Ce qui vient d'arriver à la fin d'une vérification du programme du joueur : « giftsBefore » est le nombre de
     * cadeaux qui attendaient avant elle, ceux qui sont venus après sont les cadeaux arrivés.
     */
    static List<Event> after(PlayerState s, int giftsBefore, Programme.Tick t, LocalDate today) {
        List<String> arrived = s.pending.size() > giftsBefore ? List.copyOf(s.pending.subList(giftsBefore, s.pending.size())) : List.of();
        return after(t, arrived, t.newlyCompleted() ? Streak.display(s, today) : 0, s.pending.size());
    }

    /**
     * Ce qui vient d'arriver à la fin d'une vérification du programme : tâches faites (sauf la dernière), programme
     * complet, palier de série atteint et cadeaux arrivés. Rien d'autre que ce qui a changé n'est cité.
     *
     * @param arrived identifiants des cadeaux ajoutés par cette vérification (« jour », « palier:7 »...)
     * @param serie   série du joueur, lue seulement quand le programme vient d'être complété
     * @param pending cadeaux qui attendent maintenant
     */
    static List<Event> after(Programme.Tick t, List<String> arrived, int serie, int pending) {
        if (!t.newlyCompleted() && t.newlyDone().isEmpty() && arrived.isEmpty()) {
            return List.of();
        }
        List<Event> out = new ArrayList<>();
        if (t.newlyCompleted()) {
            out.add(new Event(PROGRAMME_COMPLET, Map.of("serie", Integer.toString(serie))));
        } else {
            int total = t.tasks().size();
            int done = (int) t.tasks().stream().filter(Programme.TaskView::done).count();
            for (Programme.TaskView v : t.newlyDone()) {
                out.add(new Event(TACHE_FAITE, Map.of("tache", v.text(), "faites", Integer.toString(done),
                        "total", Integer.toString(total), "restantes", Integer.toString(total - done))));
            }
        }
        for (String id : arrived) {
            if (id.startsWith("palier:")) {
                out.add(new Event(PALIER, Map.of("palier", Integer.toString(Recompenses.step(id)))));
            }
        }
        if (!arrived.isEmpty()) {
            out.add(new Event(CADEAU_PRET, Map.of("cadeaux", Integer.toString(pending))));
        }
        return out;
    }
}
