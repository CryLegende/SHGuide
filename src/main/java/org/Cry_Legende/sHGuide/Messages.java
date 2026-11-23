package org.Cry_Legende.sHGuide;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Textes de messages.yml. Le fichier du jar sert de valeurs par défaut : une clé absente du fichier du serveur
 * (ajoutée dans une version plus récente) garde le texte du jar, une valeur vide posée exprès reste muette.
 */
public final class Messages {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexCharacter('#').hexColors().build();

    private final YamlConfiguration file;

    Messages(YamlConfiguration file) {
        this.file = file;
    }

    /** Envoie chat.« key » ; un message vide n'est pas envoyé. */
    public void send(CommandSender to, String key, String... replacements) {
        String text = replace(file.getString("chat." + key), replacements);
        if (!text.isEmpty()) {
            to.sendMessage(LEGACY.deserialize(text));
        }
    }

    /** Ligne de messages.yml (chemin complet) qui lance /command au clic ; vide = rien n'est envoyé. */
    public void sendClickable(Player to, String key, String command, String... replacements) {
        String text = replace(file.getString(key), replacements);
        if (!text.isEmpty()) {
            to.sendMessage(LEGACY.deserialize(text).clickEvent(ClickEvent.runCommand("/" + command)));
        }
    }

    public String text(String key, String... replacements) {
        return replace(file.getString(key), replacements);
    }

    public List<String> lines(String key, String... replacements) {
        List<String> lines = new ArrayList<>();
        for (String line : file.getStringList(key)) {
            lines.add(replace(line, replacements));
        }
        return lines;
    }

    /** Bloc de lignes : une liste, ou un texte seul ; vide = aucune ligne. */
    public List<String> block(String key, String... replacements) {
        if (file.isList(key)) {
            return lines(key, replacements);
        }
        String text = text(key, replacements);
        return text.isEmpty() ? List.of() : List.of(text);
    }

    public int number(String key, int fallback) {
        return file.getInt(key, fallback);
    }

    /**
     * Lore d'un bouton : chaque ligne de « key » reçoit les remplacements ; une ligne faite seulement de {bloc}
     * est remplacée par les lignes du bloc (aucune s'il est vide). Les lignes vides en double et en fin de lore
     * disparaissent, pour qu'un bloc absent ne laisse pas de trou.
     */
    public List<String> lore(String key, Map<String, List<String>> blocks, String... replacements) {
        List<String> out = new ArrayList<>();
        for (String line : file.getStringList(key)) {
            String t = line.trim();
            if (t.startsWith("{") && t.endsWith("}") && blocks.containsKey(t.substring(1, t.length() - 1))) {
                for (String b : blocks.get(t.substring(1, t.length() - 1))) {
                    out.add(replace(b, replacements));
                }
            } else {
                out.add(replace(line, replacements));
            }
        }
        return tidy(out);
    }

    static List<String> tidy(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String l : lines) {
            boolean blank = l.isBlank();
            if (blank && (out.isEmpty() ? false : out.get(out.size() - 1).isBlank())) {
                continue;
            }
            out.add(blank ? "" : l);
        }
        while (!out.isEmpty() && out.get(out.size() - 1).isBlank()) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    public static Component chat(String text) {
        return LEGACY.deserialize(text);
    }

    /** Texte d'objet de menu : couleurs lues, sans l'italique que le jeu ajoute. */
    public static Component item(String text) {
        return LEGACY.deserialize(text).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static String replace(String text, String... replacements) {
        if (text == null) {
            return "";
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return text;
    }
}
