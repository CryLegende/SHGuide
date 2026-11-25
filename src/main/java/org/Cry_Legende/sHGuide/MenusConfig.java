package org.Cry_Legende.sHGuide;

import org.bukkit.configuration.ConfigurationSection;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * menus.yml : les écrans de Jake. Chaque écran a ses boutons (cases, conditions, icône, textes, clics) et ses zones, des
 * cases remplies par le plugin (tâches du jour, piste et paliers de la série, nouveautés, étapes du tutoriel) avec les
 * modèles écrits dans le fichier. Lu une fois par chargement ; une erreur nomme la clé fautive.
 */
record MenusConfig(Style style, Texte.Bar bar, Map<String, List<String>> lists, Map<String, Ecran> screens, String home,
                   int closeTicks) {

    static final List<String> ZONES = List.of("taches", "piste", "paliers", "nouveautes", "etapes");

    /** Modèles attendus dans chaque zone. */
    private static final Map<String, List<String>> TEMPLATES = Map.of(
            "taches", List.of("faite", "en-cours", "a-faire"),
            "piste", List.of("fait", "aujourdhui", "a-venir", "palier"),
            "paliers", List.of("atteint", "a-prendre", "suivant", "loin"),
            "nouveautes", List.of("nouvelle", "vue"),
            "etapes", List.of("faite", "en-cours", "a-venir"));

    record AccentRule(List<String> conditions, String icon) {
    }

    record Style(String ring, String accent, List<AccentRule> rules) {
    }

    record Phrase(List<String> conditions, String text) {
    }

    /** Un objet à dessiner, textes pas encore remplacés. */
    record Modele(String icon, String name, List<String> lore, String amount, boolean glint, List<String> glintIf, List<String> click) {
    }

    record Bouton(String id, List<Integer> slots, List<String> conditions, Modele item) {
    }

    record Zone(String type, List<Integer> slots, List<Integer> lights, Map<String, Modele> templates) {
    }

    record Ecran(String id, String title, int rows, List<Integer> ring, List<Integer> accent, List<Phrase> phrases,
                 List<Bouton> buttons, Map<String, Zone> zones) {

        int size() {
            return rows * 9;
        }
    }

    /** L'écran demandé, sans tenir compte des accents ni de la casse ; null s'il n'existe pas. */
    Ecran screen(String id) {
        return id == null ? null : screens.get(key(id));
    }

    static String key(String id) {
        return Normalizer.normalize(id, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    static MenusConfig read(ConfigurationSection c) {
        ConfigurationSection menus = c.getConfigurationSection("menus");
        if (menus == null || menus.getKeys(false).isEmpty()) {
            throw new IllegalArgumentException("menus : aucun écran");
        }
        ConfigurationSection cadre = c.getConfigurationSection("cadre");
        List<AccentRule> rules = new ArrayList<>();
        if (cadre != null && cadre.isConfigurationSection("accents")) {
            ConfigurationSection a = cadre.getConfigurationSection("accents");
            for (String id : a.getKeys(false)) {
                ConfigurationSection r = need(a, id, "cadre.accents");
                rules.add(new AccentRule(conditions(r, "si", "cadre.accents." + id), r.getString("objet", "")));
            }
        }
        Style style = new Style(cadre == null ? "BLACK_STAINED_GLASS_PANE" : cadre.getString("contour", "BLACK_STAINED_GLASS_PANE"),
                cadre == null ? "" : cadre.getString("accent", ""), List.copyOf(rules));
        ConfigurationSection b = c.getConfigurationSection("barre");
        Texte.Bar bar = b == null ? new Texte.Bar(10, "■", "&8■")
                : new Texte.Bar(Math.max(1, b.getInt("cases", 10)), b.getString("pleine", "■"), b.getString("vide", "&8■"));
        Map<String, List<String>> lists = new LinkedHashMap<>();
        ConfigurationSection l = c.getConfigurationSection("listes");
        if (l != null) {
            for (String id : l.getKeys(false)) {
                lists.put(id, l.isList(id) ? List.copyOf(l.getStringList(id)) : List.of(l.getString(id, "")));
            }
        }
        Map<String, Ecran> screens = new LinkedHashMap<>();
        for (String id : menus.getKeys(false)) {
            screens.put(key(id), screen(id, need(menus, id, "menus")));
        }
        String home = key(c.getString("accueil", "accueil"));
        if (!screens.containsKey(home)) {
            throw new IllegalArgumentException("accueil : l'écran « " + home + " » n'existe pas dans menus");
        }
        MenusConfig out = new MenusConfig(style, bar, lists, screens, home, Math.max(0, c.getInt("fermeture-ticks", 6)));
        out.checkLinks();
        return out;
    }

    /** Un « [menu] x » doit mener à un écran qui existe. */
    private void checkLinks() {
        for (Ecran e : screens.values()) {
            for (Bouton bt : e.buttons()) {
                checkLinks("menus." + e.id() + ".boutons." + bt.id(), bt.item());
            }
            e.zones().forEach((z, zone) -> zone.templates().forEach((t, m) -> checkLinks("menus." + e.id() + ".zones." + z + "." + t, m)));
        }
    }

    private void checkLinks(String path, Modele m) {
        for (String line : m.click()) {
            if (line.contains("{")) {
                continue;
            }
            Action a;
            try {
                a = Action.parse(line);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(path + ".clic : " + e.getMessage());
            }
            if (a != null && a.type() == Action.Type.MENU && screen(a.arg()) == null) {
                throw new IllegalArgumentException(path + ".clic : l'écran « " + a.arg() + " » n'existe pas");
            }
        }
    }

    private static Ecran screen(String id, ConfigurationSection s) {
        String path = "menus." + id;
        int rows = s.getInt("lignes", 5);
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException(path + ".lignes : de 1 à 6");
        }
        int size = rows * 9;
        List<Integer> ring = s.isList("contour") ? slots(s, "contour", size, path) : defaultRing(rows);
        ring = without(ring, slots(s, "contour-sans", size, path));
        List<Integer> accent = s.isList("accent") ? slots(s, "accent", size, path) : defaultAccent(rows);
        accent = without(accent, slots(s, "accent-sans", size, path));
        List<Phrase> phrases = new ArrayList<>();
        ConfigurationSection ph = s.getConfigurationSection("phrases");
        if (ph != null) {
            for (String k : ph.getKeys(false)) {
                ConfigurationSection p = need(ph, k, path + ".phrases");
                phrases.add(new Phrase(conditions(p, "si", path + ".phrases." + k), p.getString("texte", "")));
            }
        }
        List<Bouton> buttons = new ArrayList<>();
        ConfigurationSection bs = s.getConfigurationSection("boutons");
        if (bs != null) {
            for (String k : bs.getKeys(false)) {
                ConfigurationSection bt = need(bs, k, path + ".boutons");
                String at = path + ".boutons." + k;
                List<Integer> where = slots(bt, "cases", size, at);
                if (bt.isInt("case")) {
                    where = new ArrayList<>(where);
                    where.add(0, checked(bt.getInt("case"), size, at + ".case"));
                }
                if (where.isEmpty()) {
                    throw new IllegalArgumentException(at + " : case ou cases attendu");
                }
                buttons.add(new Bouton(k, List.copyOf(where), conditions(bt, "si", at), item(bt, at)));
            }
        }
        Map<String, Zone> zones = new LinkedHashMap<>();
        ConfigurationSection zs = s.getConfigurationSection("zones");
        if (zs != null) {
            for (String k : zs.getKeys(false)) {
                if (!ZONES.contains(k)) {
                    throw new IllegalArgumentException(path + ".zones." + k + " : zone inconnue (" + String.join(", ", ZONES) + ")");
                }
                zones.put(k, zone(k, need(zs, k, path + ".zones"), size, path + ".zones." + k));
            }
        }
        return new Ecran(key(id), s.getString("titre", id), rows, ring, accent, List.copyOf(phrases), List.copyOf(buttons), zones);
    }

    private static Zone zone(String type, ConfigurationSection z, int size, String path) {
        List<Integer> where = slots(z, "cases", size, path);
        if (where.isEmpty()) {
            throw new IllegalArgumentException(path + ".cases : au moins une case");
        }
        Map<String, Modele> templates = new LinkedHashMap<>();
        for (String k : z.getKeys(false)) {
            if (z.isConfigurationSection(k)) {
                templates.put(k, item(z.getConfigurationSection(k), path + "." + k));
            }
        }
        Set<String> missing = new TreeSet<>(TEMPLATES.get(type));
        missing.removeAll(templates.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(path + " : modèle manquant (" + String.join(", ", missing) + ")");
        }
        return new Zone(type, where, slots(z, "voyants", size, path), templates);
    }

    private static Modele item(ConfigurationSection s, String path) {
        List<String> click = s.isList("clic") ? s.getStringList("clic")
                : s.getString("clic", "").isBlank() ? List.of() : List.of(s.getString("clic"));
        return new Modele(s.getString("icone", "PAPER"), s.getString("nom", " "), List.copyOf(s.getStringList("lore")),
                s.getString("quantite", ""), s.getBoolean("brille", false), conditions(s, "brille-si", path), List.copyOf(click));
    }

    private static List<String> conditions(ConfigurationSection s, String key, String path) {
        List<String> out = s.isList(key) ? s.getStringList(key)
                : s.getString(key, "").isBlank() ? List.of() : List.of(s.getString(key));
        for (String cond : out) {
            if (!Condition.valid(cond)) {
                throw new IllegalArgumentException(path + "." + key + " : opérateur manquant (== != >= <= > < ~) dans « " + cond + " »");
            }
        }
        return List.copyOf(out);
    }

    private static List<Integer> slots(ConfigurationSection s, String key, int size, String path) {
        List<Integer> out = new ArrayList<>();
        for (int i : s.getIntegerList(key)) {
            out.add(checked(i, size, path + "." + key));
        }
        return List.copyOf(out);
    }

    private static int checked(int slot, int size, String path) {
        if (slot < 0 || slot >= size) {
            throw new IllegalArgumentException(path + " : case " + slot + " hors du menu (0 à " + (size - 1) + ")");
        }
        return slot;
    }

    private static List<Integer> without(List<Integer> list, List<Integer> removed) {
        return list.stream().filter(i -> !removed.contains(i)).toList();
    }

    /** Le bord du menu. */
    private static List<Integer> defaultRing(int rows) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < rows * 9; i++) {
            int row = i / 9;
            if (row == 0 || row == rows - 1 || i % 9 == 0 || i % 9 == 8) {
                out.add(i);
            }
        }
        return out;
    }

    /** Les colonnes 2 et 8, entre la première et la dernière ligne. */
    private static List<Integer> defaultAccent(int rows) {
        List<Integer> out = new ArrayList<>();
        for (int row = 1; row < rows - 1; row++) {
            out.add(row * 9 + 1);
            out.add(row * 9 + 7);
        }
        return out;
    }

    private static ConfigurationSection need(ConfigurationSection parent, String key, String path) {
        ConfigurationSection s = parent.getConfigurationSection(key);
        if (s == null) {
            throw new IllegalArgumentException(path + "." + key + " : section attendue");
        }
        return s;
    }
}
