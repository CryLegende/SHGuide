package org.Cry_Legende.sHGuide;

import org.bukkit.configuration.ConfigurationSection;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * nouveautes.yml : ce qui a changé sur le serveur, une entrée par nouveauté. Une nouveauté est « nouvelle » pour un joueur
 * tant qu'il ne l'a pas vue dans l'écran des nouveautés, qu'elle est sortie (date passée), récente, et sortie après son
 * arrivée. Les plus récentes d'abord ; à date égale, l'ordre du fichier.
 */
record Nouveautes(List<Entree> all) {

    record Entree(String id, LocalDate date, String title, String icon, List<String> text, String command, List<String> click) {
    }

    static Nouveautes read(ConfigurationSection c) {
        List<Entree> out = new ArrayList<>();
        for (String id : c.getKeys(false)) {
            ConfigurationSection s = c.getConfigurationSection(id);
            if (s == null) {
                throw new IllegalArgumentException(id + " : section attendue");
            }
            String title = s.getString("titre", "");
            if (title.isBlank()) {
                throw new IllegalArgumentException(id + ".titre manquant");
            }
            String command = s.getString("commande", "");
            List<String> click = s.isList("clic") ? s.getStringList("clic")
                    : !s.getString("clic", "").isBlank() ? List.of(s.getString("clic"))
                    : command.isBlank() ? List.of() : List.of(command);
            for (String line : click) {
                try {
                    Action.parse(line);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(id + ".clic : " + e.getMessage());
                }
            }
            out.add(new Entree(id, date(id, s.get("date")), title, s.getString("icone", "PAPER"),
                    List.copyOf(s.getStringList("texte")), command, List.copyOf(click)));
        }
        out.sort(Comparator.comparing(Entree::date).reversed());
        return new Nouveautes(List.copyOf(out));
    }

    /** SnakeYAML lit une date sans guillemets comme un Date à minuit UTC. */
    private static LocalDate date(String id, Object date) {
        try {
            return date instanceof Date d ? d.toInstant().atZone(ZoneOffset.UTC).toLocalDate()
                    : LocalDate.parse(String.valueOf(date).trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(id + ".date : « " + date + " » illisible (2026-11-23)");
        }
    }

    /** Celles déjà sorties. */
    List<Entree> out(LocalDate today) {
        return all.stream().filter(n -> !n.date().isAfter(today)).toList();
    }

    /** Celles que le joueur n'a pas encore vues. */
    List<Entree> unread(PlayerState s, LocalDate today, int recentDays) {
        LocalDate first = s.firstDay == null ? null : LocalDate.parse(s.firstDay);
        LocalDate oldest = today.minusDays(recentDays);
        return all.stream().filter(n -> !n.date().isAfter(today) && n.date().isAfter(oldest)
                && (first == null || n.date().isAfter(first)) && !s.newsRead.contains(n.id())).toList();
    }

    /** Celles sorties après « lastSeen » (dialogue de retour) ; aucune si la dernière venue n'est pas connue. */
    List<Entree> since(String lastSeen, LocalDate today) {
        if (lastSeen == null) {
            return List.of();
        }
        LocalDate seen = LocalDate.parse(lastSeen);
        return all.stream().filter(n -> n.date().isAfter(seen) && !n.date().isAfter(today)).toList();
    }

    /** Marque ces nouveautés comme vues ; celles retirées du fichier sont oubliées. Vrai si quelque chose a changé. */
    boolean markRead(PlayerState s, List<Entree> seen) {
        boolean changed = false;
        for (Entree n : seen) {
            changed |= s.newsRead.add(n.id());
        }
        changed |= s.newsRead.removeIf(id -> all.stream().noneMatch(n -> n.id().equals(id)));
        return changed;
    }
}
