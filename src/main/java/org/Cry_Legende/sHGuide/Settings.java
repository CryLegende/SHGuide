package org.Cry_Legende.sHGuide;

import org.bukkit.configuration.ConfigurationSection;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** config.yml lu une fois par chargement ; tout est immuable ensuite. */
record Settings(ZoneId zone, int checkSeconds, int checkPerTick, Map<String, Integer> slowPlaceholders, int joinDelaySeconds, double nearRatio, String palierPlaceholder,
                List<TaskDef> tasks, List<TaskDef> rotating, List<String> dailyReward, String dailyRewardText,
                List<DailyVariant> dailyVariants, int cycle, Map<Integer, List<String>> steps, Map<Integer, String> gains,
                List<ObjectiveDef> objectives, int objectiveMax,
                int returnDays, long returnDelayTicks, String dialogCondition, List<String> dialogCommands,
                int newsMax, int newsRecentDays, DateTimeFormatter newsDate, int annonceBeforeMinutes, int annonceGraceMinutes,
                Map<String, String> sounds, DbConfig db,
                AccueilConfig accueil, AnnonceBarre.Config annonceBar, List<Fleche.Source> arrowSources,
                Map<String, List<String>> moments, int momentsPerSecond) {

    /**
     * Tâche du programme : « compteurs » (écart depuis le début du jour), « compte » (valeurs égales à egal, écart
     * depuis le début du jour) ou « valeur » (nombre lu tel quel, sans départ). « reward » : commandes lancées une fois,
     * quand la tâche est faite (vide si elle n'a pas de récompense à elle).
     */
    record TaskDef(String id, String text, String icon, String click, List<String> counters,
                   List<String> countValues, String countEquals, String value, List<Integer> goals,
                   List<String> description, List<String> conditions, boolean fallbackRotating, List<String> reward) {

        boolean isCount() {
            return !countValues.isEmpty();
        }

        boolean isValue() {
            return !value.isEmpty();
        }

        int goal(int palier) {
            return goals.size() == 1 ? goals.get(0) : goals.get(Math.max(1, Math.min(5, palier)) - 1);
        }
    }

    /**
     * Objectif du moment : « text » son nom, « todo » ce qu'il faut faire (facultatif). Avec « loop », {i} est essayé de
     * « loopFrom » à « loopTo » ; « sort » départage ceux qui marchent (le plus petit texte), sinon le premier gagne.
     */
    record ObjectiveDef(String id, List<String> conditions, String text, String todo, List<String> lore, String click,
                        String icon, boolean loop, int loopFrom, int loopTo, String sort) {
    }

    /** Récompense en plus de « recompense », différente selon le jour (programme.recompenses-du-jour). */
    record DailyVariant(String id, String text, List<String> commands) {
    }

    /** Récompense du jour en plus : la même pour tout le serveur, dans l'ordre de recompenses-ordre ; null si aucune. */
    DailyVariant variantFor(LocalDate day) {
        return dailyVariants.isEmpty() ? null
                : dailyVariants.get((int) Math.floorMod(day.toEpochDay(), (long) dailyVariants.size()));
    }

    DailyVariant variantById(String id) {
        for (DailyVariant v : dailyVariants) {
            if (v.id().equals(id)) {
                return v;
            }
        }
        return null;
    }

    record DbConfig(String host, int port, String database, String user, String password) {
    }

    TaskDef rotatingFor(LocalDate day) {
        return rotating.isEmpty() ? null : rotating.get((int) Math.floorMod(day.toEpochDay(), (long) rotating.size()));
    }

    /** Tâche tournante qui suit celle du jour : elle remplace une tâche dont la condition « si » est fausse. */
    TaskDef alternateFor(LocalDate day) {
        if (rotating.size() < 2) {
            return null;
        }
        return rotating.get((int) Math.floorMod(day.toEpochDay() + 1, (long) rotating.size()));
    }

    TaskDef rotatingById(String id) {
        for (TaskDef t : rotating) {
            if (t.id().equals(id)) {
                return t;
            }
        }
        return null;
    }

    List<TaskDef> tasksFor(LocalDate day) {
        List<TaskDef> out = new ArrayList<>(tasks);
        TaskDef r = rotatingFor(day);
        if (r != null) {
            out.add(r);
        }
        return out;
    }

    Set<Integer> stepDays() {
        return steps.keySet();
    }

    /** Ce que donne le palier de ce jour, en texte ; vide si ce jour n'est pas un palier. */
    String gain(int day) {
        return gains.getOrDefault(day, "");
    }

    static Settings read(ConfigurationSection c) {
        return read(c, null);
    }

    /**
     * @param momentErrors non null : une section moments invalide est ignorée (aucun moment ne part) et son erreur est
     *                     ajoutée ici, au lieu de refuser le fichier ; null : elle refuse le fichier
     */
    static Settings read(ConfigurationSection c, List<String> momentErrors) {
        ConfigurationSection prog = need(c, "programme");
        List<TaskDef> tasks = tasks(prog, "programme.taches", "taches");
        List<TaskDef> rotating = tasks(prog, "programme.tournantes", "tournantes");
        if (tasks.isEmpty() && rotating.isEmpty()) {
            throw new IllegalArgumentException("programme : aucune tâche");
        }

        ConfigurationSection serie = need(c, "serie");
        int cycle = Math.max(1, serie.getInt("cycle", 30));
        Map<Integer, List<String>> steps = new TreeMap<>();
        ConfigurationSection paliers = serie.getConfigurationSection("paliers");
        if (paliers != null) {
            for (String k : paliers.getKeys(false)) {
                try {
                    steps.put(Integer.parseInt(k), List.copyOf(paliers.getStringList(k)));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("serie.paliers." + k + " : un numéro de jour est attendu");
                }
            }
        }

        Map<Integer, String> gains = new TreeMap<>();
        ConfigurationSection gainTexts = serie.getConfigurationSection("gains");
        if (gainTexts != null) {
            for (String k : gainTexts.getKeys(false)) {
                try {
                    gains.put(Integer.parseInt(k), gainTexts.getString(k, ""));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("serie.gains." + k + " : un numéro de jour est attendu");
                }
            }
        }

        List<ObjectiveDef> objectives = new ArrayList<>();
        ConfigurationSection obj = need(c, "objectifs");
        for (String id : obj.getKeys(false)) {
            ConfigurationSection o = obj.getConfigurationSection(id);
            if (o == null) {
                throw new IllegalArgumentException("objectifs." + id + " : section attendue");
            }
            List<String> si = o.getStringList("si");
            for (int i = 0; i < si.size(); i++) {
                if (!Condition.valid(si.get(i))) {
                    throw new IllegalArgumentException("objectifs." + id + ".si[" + i
                            + "] : opérateur manquant (== != >= <= > < ~)");
                }
            }
            ConfigurationSection loop = o.getConfigurationSection("boucle");
            objectives.add(new ObjectiveDef(id, List.copyOf(si), o.getString("texte", ""), o.getString("faire", ""),
                    List.copyOf(o.getStringList("description")), o.getString("clic", ""), o.getString("icone", "PAPER"),
                    loop != null, loop == null ? 0 : Math.max(0, loop.getInt("de", 1)), loop == null ? 0 : loop.getInt("a", 1),
                    loop == null ? "" : loop.getString("tri", "")));
        }

        ConfigurationSection ret = need(c, "retour");
        Map<String, String> sounds = new LinkedHashMap<>();
        ConfigurationSection so = c.getConfigurationSection("sons");
        if (so != null) {
            for (String k : so.getKeys(false)) {
                sounds.put(k, so.getString(k, ""));
            }
        }

        Map<String, Integer> slow = new LinkedHashMap<>();
        int slowSeconds = c.getInt("placeholders-lents.secondes", 30);
        for (String placeholder : c.getStringList("placeholders-lents.liste")) {
            slow.put(placeholder.trim(), slowSeconds);
        }

        Map<String, List<String>> moments = Map.of();
        int momentsPerSecond = 10;
        try {
            moments = moments(c.get("moments"));
            momentsPerSecond = momentsPerSecond(c.get("moments-par-seconde"));
        } catch (IllegalArgumentException e) {
            if (momentErrors == null) {
                throw e;
            }
            moments = Map.of();
            momentsPerSecond = 10;
            momentErrors.add(e.getMessage());
        }

        ConfigurationSection db = need(c, "base-de-donnees");
        return new Settings(ZoneId.of(c.getString("fuseau", "Europe/Paris")),
                Math.max(1, c.getInt("verification-secondes", 10)), Math.max(1, c.getInt("verification-par-tick", 1)), Map.copyOf(slow),
                Math.max(0, c.getInt("depart-apres-connexion-secondes", 30)),
                c.getDouble("seuil-presque", 0.8),
                c.getString("palier", "1"), List.copyOf(tasks), List.copyOf(rotating),
                List.copyOf(prog.getStringList("recompense")), prog.getString("recompense-texte", ""), variants(prog), cycle,
                steps, gains, List.copyOf(objectives), Math.max(1, c.getInt("objectif-longueur-max", 30)),
                Math.max(1, ret.getInt("jours", 3)), Math.max(0, ret.getLong("delai-ticks", 60)),
                ret.getString("dialogue-si", ""), List.copyOf(ret.getStringList("dialogue")),
                Math.max(0, ret.getInt("nouveautes-max", 3)), Math.max(1, c.getInt("nouveautes-recentes-jours", 30)),
                newsDate(c), Math.max(0, c.getInt("annonce-avant-minutes", 15)),
                Math.max(0, c.getInt("annonce-apres-debut-minutes", 5)), sounds,
                new DbConfig(db.getString("hote", "127.0.0.1"), db.getInt("port", 3306), db.getString("base", "minecraft"),
                        db.getString("utilisateur", ""), db.getString("mot-de-passe", "")),
                AccueilConfig.read(c.getConfigurationSection("accueil")),
                AnnonceBarre.Config.read(c.getConfigurationSection("annonce-barre")),
                arrowSources(c.getConfigurationSection("fleches")), moments, momentsPerSecond);
    }

    /** « moments-par-seconde » : joueurs pour qui le moment « annonce » part au plus à chaque seconde (10 sans la clé). */
    private static int momentsPerSecond(Object raw) {
        if (raw == null) {
            return 10;
        }
        if (!(raw instanceof Integer n) || n < 1) {
            throw new IllegalArgumentException("moments-par-seconde : un nombre entier d'au moins 1 est attendu");
        }
        return n;
    }

    /** Section « moments » : une liste de commandes par moment connu ; une clé inconnue ou une valeur qui n'est pas une liste est refusée. */
    private static Map<String, List<String>> moments(Object raw) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (raw == null) {
            return out;
        }
        if (!(raw instanceof ConfigurationSection sec)) {
            throw new IllegalArgumentException("moments : une section est attendue (un moment par clé : " + String.join(", ", Moments.NAMES) + ")");
        }
        for (String name : sec.getKeys(false)) {
            if (!Moments.NAMES.contains(name)) {
                throw new IllegalArgumentException("moments." + name + " : moment inconnu (" + String.join(", ", Moments.NAMES) + ")");
            }
            Object value = sec.get(name);
            if (value == null) {
                continue;
            }
            out.put(name, commands(value, "moments." + name));
        }
        return Map.copyOf(out);
    }

    /** Une liste de commandes écrites en texte (moments, récompense d'une tâche) ; « path » nomme la clé dans l'erreur. */
    private static List<String> commands(Object value, String path) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(path + " : une liste de commandes est attendue");
        }
        List<String> commands = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof String command)) {
                throw new IllegalArgumentException(path + "[" + i + "] : une commande écrite en texte est attendue");
            }
            commands.add(command);
        }
        return List.copyOf(commands);
    }

    private static DateTimeFormatter newsDate(ConfigurationSection c) {
        String pattern = c.getString("nouveautes-date", "dd/MM");
        try {
            return DateTimeFormatter.ofPattern(pattern);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("nouveautes-date : format « " + pattern + " » illisible (dd/MM)");
        }
    }

    private static List<Fleche.Source> arrowSources(ConfigurationSection sec) {
        List<Fleche.Source> out = new ArrayList<>();
        if (sec == null) {
            return out;
        }
        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s == null || s.getString("cible", "").isBlank()) {
                throw new IllegalArgumentException("fleches." + id + ".cible : « monde x y z » ou un placeholder attendu");
            }
            out.add(new Fleche.Source(id, s.getString("cible"), s.getString("nom", ""), s.getString("texte", ""), s.getString("fin", ""),
                    s.getDouble("arrive", -1), s.getDouble("distance-min", -1)));
        }
        return List.copyOf(out);
    }

    private static List<DailyVariant> variants(ConfigurationSection prog) {
        ConfigurationSection sec = prog.getConfigurationSection("recompenses-du-jour");
        if (sec == null) {
            return List.of();
        }
        Map<String, DailyVariant> byId = new LinkedHashMap<>();
        for (String id : sec.getKeys(false)) {
            ConfigurationSection v = sec.getConfigurationSection(id);
            if (v == null) {
                throw new IllegalArgumentException("programme.recompenses-du-jour." + id + " : section attendue");
            }
            byId.put(id, new DailyVariant(id, v.getString("texte", id), List.copyOf(v.getStringList("commandes"))));
        }
        List<String> order = prog.getStringList("recompenses-ordre");
        if (order.isEmpty()) {
            return List.copyOf(byId.values());
        }
        List<DailyVariant> out = new ArrayList<>();
        for (String id : order) {
            DailyVariant v = byId.get(id);
            if (v == null) {
                throw new IllegalArgumentException("programme.recompenses-ordre : « " + id + " » absent de recompenses-du-jour");
            }
            out.add(v);
        }
        return List.copyOf(out);
    }

    private static List<TaskDef> tasks(ConfigurationSection prog, String path, String key) {
        List<TaskDef> out = new ArrayList<>();
        ConfigurationSection sec = prog.getConfigurationSection(key);
        if (sec == null) {
            return out;
        }
        for (String id : sec.getKeys(false)) {
            ConfigurationSection t = sec.getConfigurationSection(id);
            if (t == null) {
                throw new IllegalArgumentException(path + "." + id + " : section attendue");
            }
            List<Integer> goals = t.getIntegerList("objectif");
            if (goals.size() != 1 && goals.size() != 5) {
                throw new IllegalArgumentException(path + "." + id + ".objectif : une valeur ou cinq (paliers 1 à 5)");
            }
            ConfigurationSection compte = t.getConfigurationSection("compte");
            List<String> counters = t.getStringList("compteurs");
            List<String> values = compte == null ? List.of() : compte.getStringList("valeurs");
            String value = t.getString("valeur", "");
            int kinds = (counters.isEmpty() ? 0 : 1) + (values.isEmpty() ? 0 : 1) + (value.isEmpty() ? 0 : 1);
            if (kinds != 1) {
                throw new IllegalArgumentException(path + "." + id + " : « compteurs », « compte » ou « valeur », un seul des trois");
            }
            List<String> conditions = t.getStringList("si");
            for (String cond : conditions) {
                if (!Condition.valid(cond)) {
                    throw new IllegalArgumentException(path + "." + id + ".si : condition illisible « " + cond + " »");
                }
            }
            String sinon = t.getString("sinon", "");
            if (!sinon.isEmpty() && !sinon.equals("tournante")) {
                throw new IllegalArgumentException(path + "." + id + ".sinon : seule la valeur « tournante » existe");
            }
            boolean fallbackRotating = sinon.equals("tournante");
            Object rewardRaw = t.get("recompense");
            List<String> reward = rewardRaw == null ? List.of() : commands(rewardRaw, path + "." + id + ".recompense");
            for (int g : goals) {
                if (g < 1) {
                    throw new IllegalArgumentException(path + "." + id + ".objectif : 1 au moins");
                }
            }
            out.add(new TaskDef(id, t.getString("texte", id), t.getString("icone", "PAPER"), t.getString("clic", ""),
                    List.copyOf(counters), List.copyOf(values), compte == null ? "" : compte.getString("egal", ""),
                    value, List.copyOf(goals), List.copyOf(t.getStringList("description")), List.copyOf(conditions),
                    fallbackRotating, reward));
        }
        return out;
    }

    private static ConfigurationSection need(ConfigurationSection c, String key) {
        ConfigurationSection s = c.getConfigurationSection(key);
        if (s == null) {
            throw new IllegalArgumentException(key + " : section manquante");
        }
        return s;
    }
}
