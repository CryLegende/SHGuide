package org.Cry_Legende.sHGuide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Textes de menus.yml : jetons {nom}, accords {nombre|tâche|tâches}, barres {barre:nom:&b}, lignes « [si A == B] texte »
 * montrées seulement si la condition est vraie, blocs (une ligne faite seulement de {bloc} devient les lignes du bloc) et
 * placeholders PlaceholderAPI (un résultat sur plusieurs lignes donne plusieurs lignes, une ligne qu'ils laissent vide
 * disparaît). Un jeton inconnu reste écrit tel quel, pour se voir.
 */
final class Texte {

    private static final Pattern TOKEN = Pattern.compile("\\{([a-z0-9_-]+)(?:\\|([^|{}]*)\\|([^|{}]*))?}");
    private static final Pattern BAR = Pattern.compile("\\{barre:([a-z0-9_-]+):([^{}]*)}");
    private static final Pattern ONLY_TOKEN = Pattern.compile("\\{([a-z0-9_-]+)}");
    private static final Pattern IF = Pattern.compile("\\[si ([^]]+)] ?(.*)", Pattern.DOTALL);
    private static final Pattern COLORS = Pattern.compile("(?i)[&§](#[0-9a-f]{6}|x([&§][0-9a-f]){6}|[0-9a-fk-or])");

    private Texte() {
    }

    /** Dessin des barres de progression : « cells » cases, « full » et « empty » pour une case. */
    record Bar(int cells, String full, String empty) {
    }

    /** Tout ce qu'un texte peut lire : jetons, parts (0 à 1) des barres, blocs de lignes et placeholders du joueur. */
    record Contexte(Map<String, String> tokens, Map<String, Double> parts, Map<String, List<String>> blocks,
                    Resolver resolver, Bar bar) {

        Contexte with(Map<String, String> moreTokens, Map<String, Double> moreParts, Map<String, List<String>> moreBlocks) {
            return new Contexte(merge(tokens, moreTokens), merge(parts, moreParts), merge(blocks, moreBlocks), resolver, bar);
        }

        private static <V> Map<String, V> merge(Map<String, V> a, Map<String, V> b) {
            if (b.isEmpty()) {
                return a;
            }
            Map<String, V> out = new HashMap<>(a);
            out.putAll(b);
            return out;
        }
    }

    /** Une ligne, jetons et placeholders remplacés. */
    static String line(String text, Contexte c) {
        if (text == null) {
            return "";
        }
        String out = text;
        if (out.indexOf('{') >= 0) {
            out = BAR.matcher(out).replaceAll(m -> Matcher.quoteReplacement(bar(c, m.group(1), m.group(2))));
            out = TOKEN.matcher(out).replaceAll(m -> Matcher.quoteReplacement(token(c, m)));
        }
        return out.indexOf('%') >= 0 ? c.resolver().resolve(out) : out;
    }

    /** Jetons et accords seulement, sans placeholders. */
    static String simple(String text, Map<String, String> tokens) {
        return line(text, new Contexte(tokens, Map.of(), Map.of(), t -> t, new Bar(1, "", "")));
    }

    /** Les lignes d'un lore : conditions, blocs, retours à la ligne, sans lignes vides en double. */
    static List<String> lines(List<String> template, Contexte c) {
        List<String> out = new ArrayList<>();
        for (String raw : template) {
            add(out, raw, c, true);
        }
        return tidy(out);
    }

    private static void add(List<String> out, String raw, Contexte c, boolean blocks) {
        String line = raw;
        Matcher cond = IF.matcher(line);
        if (cond.matches()) {
            if (!test(cond.group(1), c)) {
                return;
            }
            line = cond.group(2);
        }
        Matcher only = ONLY_TOKEN.matcher(line.trim());
        if (blocks && only.matches() && c.blocks().containsKey(only.group(1))) {
            for (String inner : c.blocks().get(only.group(1))) {
                add(out, inner, c, false);
            }
            return;
        }
        String text = line(line, c);
        if (line.indexOf('%') >= 0 && strip(text).isBlank()) {
            return;
        }
        for (String part : text.split("\n", -1)) {
            out.add(part);
        }
    }

    /** Vrai si toutes les conditions (« A opérateur B ») sont vraies ; une liste vide est vraie. */
    static boolean all(List<String> conditions, Contexte c) {
        for (String cond : conditions) {
            if (!test(cond, c)) {
                return false;
            }
        }
        return true;
    }

    static boolean test(String condition, Contexte c) {
        return Condition.test(condition, text -> line(text, c));
    }

    private static String token(Contexte c, java.util.regex.MatchResult m) {
        String value = c.tokens().get(m.group(1));
        if (value == null) {
            return m.group();
        }
        return m.group(2) == null ? value : plural(value, m.group(2), m.group(3));
    }

    /** « 2 » → « 2 tâches », « 1 » → « 1 tâche », « 0 » → « 0 tâche ». */
    static String plural(String value, String one, String many) {
        OptionalDouble n = Num.parse(value);
        if (n.isEmpty()) {
            return value + " " + many;
        }
        return Num.format(n.getAsDouble()) + " " + (Math.abs(n.getAsDouble()) < 2 ? one : many);
    }

    private static String bar(Contexte c, String name, String color) {
        double part = Math.max(0, Math.min(1, c.parts().getOrDefault(name, 0.0)));
        int cells = Math.max(1, c.bar().cells());
        int full = (int) Math.floor(part * cells + 1e-9);
        return color + c.bar().full().repeat(full) + (full < cells ? c.bar().empty().repeat(cells - full) : "");
    }

    /** Retire les lignes vides en double et en fin de lore, pour qu'une ligne absente ne laisse pas de trou. */
    static List<String> tidy(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String l : lines) {
            boolean blank = l.isBlank();
            if (blank && !out.isEmpty() && out.get(out.size() - 1).isBlank()) {
                continue;
            }
            out.add(blank ? "" : l);
        }
        while (!out.isEmpty() && out.get(out.size() - 1).isBlank()) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    /** Habillage d'une phrase simple : lignes de « width » caractères au plus, entre « start » et « end ». */
    record Phrase(int width, String start, String next, String end) {
    }

    /** Une phrase écrite d'un trait devient les lignes d'un lore, coupées entre les mots et de longueurs proches. */
    static List<String> sentence(String text, Phrase style) {
        String plain = text.trim().replaceAll("\\s+", " ");
        if (plain.isEmpty()) {
            return List.of();
        }
        List<String> rows = wrap(plain, Math.max(1, style.width()));
        for (int width = style.width() - 1; width > 0; width--) {
            List<String> tighter = wrap(plain, width);
            if (tighter.size() > rows.size()) {
                break;
            }
            rows = tighter;
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            out.add((i == 0 ? style.start() : style.next()) + rows.get(i) + (i == rows.size() - 1 ? style.end() : ""));
        }
        return out;
    }

    private static List<String> wrap(String text, int width) {
        List<String> rows = new ArrayList<>();
        StringBuilder row = new StringBuilder();
        for (String word : text.split(" ")) {
            if (!row.isEmpty() && strip(row + " " + word).length() > width) {
                rows.add(row.toString());
                row.setLength(0);
            }
            row.append(row.isEmpty() ? "" : " ").append(word);
        }
        rows.add(row.toString());
        return rows;
    }

    /** Le texte sans ses couleurs. */
    static String strip(String text) {
        return text == null ? "" : COLORS.matcher(text).replaceAll("");
    }
}
