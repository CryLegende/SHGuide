package org.Cry_Legende.sHGuide;

import org.bukkit.configuration.ConfigurationSection;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** config.yml lu une fois par chargement ; tout est immuable ensuite. */
record Settings(ZoneId zone, int checkSeconds, double nearRatio, String palierPlaceholder,
                List<TaskDef> tasks, List<TaskDef> rotating, List<String> dailyReward,
                int cycle, Map<Integer, List<String>> steps, List<ObjectiveDef> objectives,
                int returnDays, long returnDelayTicks, String dialogCondition, List<String> dialogCommands,
                List<News> news, int newsMax, int annonceGraceMinutes,
                Map<String, Integer> slots, List<Integer> taskSlots, String islandCommand, String settingsCommand,
                Map<String, String> icons, List<HelpEntry> help, Map<String, String> sounds, DbConfig db) {

    /** Tâche du programme : « compteurs » (écart depuis le début du jour) ou « compte » (valeurs égales à egal). */
    record TaskDef(String id, String text, String icon, String click, List<String> counters,
                   List<String> countValues, String countEquals, List<Integer> goals) {

        boolean isCount() {
            return !countValues.isEmpty();
        }

        int goal(int palier) {
            return goals.size() == 1 ? goals.get(0) : goals.get(Math.max(1, Math.min(5, palier)) - 1);
        }
    }

    record ObjectiveDef(String id, List<String> conditions, String text, List<String> lore, String click,
                        String icon, int loopFrom, int loopTo) {
    }

    record HelpEntry(String id, String icon, String name, List<String> lore, List<String> commands, boolean close) {
    }

    record News(LocalDate date, String text) {
    }

    record DbConfig(String host, int port, String database, String user, String password) {
    }

    TaskDef rotatingFor(LocalDate day) {
        return rotating.isEmpty() ? null : rotating.get((int) Math.floorMod(day.toEpochDay(), (long) rotating.size()));
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

    static Settings read(ConfigurationSection c) {
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
            objectives.add(new ObjectiveDef(id, List.copyOf(si), o.getString("texte", ""),
                    List.copyOf(o.getStringList("description")), o.getString("clic", ""), o.getString("icone", "PAPER"),
                    loop == null ? 0 : Math.max(1, loop.getInt("de", 1)), loop == null ? 0 : loop.getInt("a", 1)));
        }

        ConfigurationSection ret = need(c, "retour");
        List<News> news = new ArrayList<>();
        for (Map<?, ?> m : ret.getMapList("nouveautes")) {
            Object date = m.get("date");
            Object text = m.get("texte");
            if (date == null || text == null) {
                throw new IllegalArgumentException("retour.nouveautes : date et texte attendus");
            }
            // SnakeYAML lit une date sans guillemets comme un Date à minuit UTC
            LocalDate day;
            try {
                day = date instanceof Date d ? d.toInstant().atZone(ZoneOffset.UTC).toLocalDate()
                        : LocalDate.parse(date.toString().trim());
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("retour.nouveautes : date « " + date + " » illisible (2026-11-23)");
            }
            news.add(new News(day, text.toString()));
        }

        ConfigurationSection menu = need(c, "menu");
        Map<String, Integer> slots = new LinkedHashMap<>();
        ConfigurationSection cases = need(menu, "cases");
        for (String k : cases.getKeys(false)) {
            slots.put(k, cases.getInt(k));
        }
        List<Integer> taskSlots = menu.getIntegerList("taches");
        int dailyCount = tasks.size() + (rotating.isEmpty() ? 0 : 1);
        if (taskSlots.size() < dailyCount) {
            throw new IllegalArgumentException("menu.taches : " + dailyCount + " cases attendues pour les tâches du jour");
        }
        Map<String, String> icons = new LinkedHashMap<>();
        ConfigurationSection ic = need(menu, "icones");
        for (String k : ic.getKeys(false)) {
            icons.put(k, ic.getString(k));
        }

        List<HelpEntry> help = new ArrayList<>();
        ConfigurationSection aide = c.getConfigurationSection("aide");
        if (aide != null) {
            for (String id : aide.getKeys(false)) {
                ConfigurationSection a = aide.getConfigurationSection(id);
                if (a == null) {
                    throw new IllegalArgumentException("aide." + id + " : section attendue");
                }
                help.add(new HelpEntry(id, a.getString("icone", "PAPER"), a.getString("nom", id),
                        List.copyOf(a.getStringList("description")), List.copyOf(a.getStringList("commandes")),
                        a.getBoolean("fermer", true)));
            }
        }
        if (help.size() > 10) {
            throw new IllegalArgumentException("aide : dix questions au plus");
        }
        Map<String, String> sounds = new LinkedHashMap<>();
        ConfigurationSection so = c.getConfigurationSection("sons");
        if (so != null) {
            for (String k : so.getKeys(false)) {
                sounds.put(k, so.getString(k, ""));
            }
        }

        ConfigurationSection db = need(c, "base-de-donnees");
        return new Settings(ZoneId.of(c.getString("fuseau", "Europe/Paris")),
                Math.max(1, c.getInt("verification-secondes", 10)), c.getDouble("seuil-presque", 0.8),
                c.getString("palier", "1"), List.copyOf(tasks), List.copyOf(rotating),
                List.copyOf(prog.getStringList("recompense")), cycle, steps, List.copyOf(objectives),
                Math.max(1, ret.getInt("jours", 3)), Math.max(0, ret.getLong("delai-ticks", 60)),
                ret.getString("dialogue-si", ""), List.copyOf(ret.getStringList("dialogue")), List.copyOf(news),
                Math.max(0, ret.getInt("nouveautes-max", 2)), Math.max(0, c.getInt("annonce-apres-debut-minutes", 5)),
                slots, List.copyOf(taskSlots), menu.getString("ile-commande", "is"),
                menu.getString("reglages-commande", "jake reglages"), icons, List.copyOf(help), sounds,
                new DbConfig(db.getString("hote", "127.0.0.1"), db.getInt("port", 3306), db.getString("base", "minecraft"),
                        db.getString("utilisateur", ""), db.getString("mot-de-passe", "")));
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
            if (counters.isEmpty() == values.isEmpty()) {
                throw new IllegalArgumentException(path + "." + id + " : « compteurs » ou « compte », un seul des deux");
            }
            out.add(new TaskDef(id, t.getString("texte", id), t.getString("icone", "PAPER"), t.getString("clic", ""),
                    List.copyOf(counters), List.copyOf(values), compte == null ? "" : compte.getString("egal", ""),
                    List.copyOf(goals)));
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
