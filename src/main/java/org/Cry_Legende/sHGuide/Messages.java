package org.Cry_Legende.sHGuide;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

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

    /** Envoie chat.« key » ; un message vide n'est pas envoyé. clics.« key » : commande lancée quand on clique dessus. */
    public void send(CommandSender to, String key, String... replacements) {
        String text = replace(file.getString("chat." + key), replacements);
        if (text.isEmpty()) {
            return;
        }
        Component line = LEGACY.deserialize(text);
        String command = text("clics." + key);
        if (!command.isBlank() && to instanceof Player) {
            line = line.clickEvent(ClickEvent.runCommand("/" + command));
            String hover = text("clics.survol");
            if (!hover.isBlank()) {
                line = line.hoverEvent(HoverEvent.showText(LEGACY.deserialize(hover)));
            }
        }
        to.sendMessage(line);
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
