package org.Cry_Legende.sHGuide;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Section « accueil » de config.yml : cadeaux d'accueil (rester, demain), tutoriel en étapes, arrivée sur l'île et
 * flèche vers l'étape. Seuls les nouveaux joueurs (premier jour connu du guide) en profitent.
 */
record AccueilConfig(boolean enabled, Stay stay, Tomorrow tomorrow, Tutorial tutorial, Arrival arrival, Arrow arrow) {

    /** Cadeau donné après « minutes » de jeu, compté seulement quand « compte-si » est vrai (pas AFK). */
    record Stay(int minutes, String countIf, String text, List<String> commands, BossBar.Color color) {
    }

    /** Cadeau du lendemain : à prendre du jour suivant le premier jour jusqu'à « jours » jours après. */
    record Tomorrow(int days, String text, List<String> commands, String dialogIf, List<String> dialog, long delayTicks,
                    int barSeconds, BossBar.Color color) {
    }

    record Tutorial(List<Step> steps, BossBar.Color color, List<String> end, int checkSeconds) {
    }

    /**
     * Étape du tutoriel : faite quand toutes les conditions de « fait-si » sont vraies. « cible » (monde x y z, avec
     * placeholders) donne la flèche ; « debut » est lancé quand l'étape devient la suivante, « recompense » quand elle
     * est faite.
     */
    record Step(String id, String text, List<String> lore, List<String> doneIf, String click, String icon,
                String target, String targetName, List<String> start, List<String> reward, String rewardText) {
    }

    /** Arrivée d'un nouveau joueur dans le monde des îles : il descend doucement vers son île sous un grand titre. */
    record Arrival(String world, double height, int fallSeconds, double speed, float pitch, String title, String subtitle,
                   int fadeIn, int stay, int fadeOut, String sound, List<Timed> commands) {
    }

    record Timed(long tick, String command) {
    }

    record Arrow(boolean enabled, String item, float scale, double ahead, double height, double minDistance,
                 double arrivedDistance, int glowColor, float yawOffset, float labelScale, double labelAbove) {
    }

    static final AccueilConfig DISABLED = new AccueilConfig(false,
            new Stay(10, "", "", List.of(), BossBar.Color.YELLOW),
            new Tomorrow(3, "", List.of(), "", List.of(), 60, 15, BossBar.Color.BLUE),
            new Tutorial(List.of(), BossBar.Color.GREEN, List.of(), 2),
            null,
            new Arrow(false, "SPECTRAL_ARROW", 0.8f, 2.2, 0.7, 6, 4, -1, 0, 0.6f, 0.35));

    static AccueilConfig read(ConfigurationSection c) {
        if (c == null || !c.getBoolean("actif", true)) {
            return DISABLED;
        }
        ConfigurationSection r = c.getConfigurationSection("rester");
        Stay stay = r == null ? DISABLED.stay() : new Stay(Math.max(0, r.getInt("minutes", 10)),
                r.getString("compte-si", ""), r.getString("texte", ""), List.copyOf(r.getStringList("commandes")),
                color(r.getString("couleur-barre", "YELLOW")));
        ConfigurationSection d = c.getConfigurationSection("demain");
        Tomorrow tomorrow = d == null ? DISABLED.tomorrow() : new Tomorrow(Math.max(1, d.getInt("jours", 3)),
                d.getString("texte", ""), List.copyOf(d.getStringList("commandes")), d.getString("dialogue-si", ""),
                List.copyOf(d.getStringList("dialogue")), Math.max(0, d.getLong("delai-ticks", 60)),
                Math.max(0, d.getInt("rappel-secondes", 15)), color(d.getString("couleur-barre", "BLUE")));
        ConfigurationSection t = c.getConfigurationSection("tutoriel");
        Tutorial tutorial = t == null ? DISABLED.tutorial() : new Tutorial(steps(t.getConfigurationSection("etapes")),
                color(t.getString("couleur-barre", "GREEN")), List.copyOf(t.getStringList("fin")),
                Math.max(1, t.getInt("verification-secondes", 2)));
        ConfigurationSection a = c.getConfigurationSection("arrivee");
        Arrival arrival = a == null || a.getString("monde", "").isEmpty() ? null : new Arrival(a.getString("monde"),
                a.getDouble("hauteur", 18), Math.max(0, a.getInt("chute-secondes", 7)),
                Math.max(0, a.getDouble("vitesse-descente", 3)), (float) a.getDouble("regard-bas", 55),
                a.getString("titre", ""), a.getString("sous-titre", ""), a.getInt("titre-apparition-ticks", 15),
                a.getInt("titre-duree-ticks", 70), a.getInt("titre-disparition-ticks", 20), a.getString("son", ""),
                timed(a.getStringList("commandes")));
        ConfigurationSection f = c.getConfigurationSection("fleche");
        Arrow arrow = f == null ? DISABLED.arrow() : new Arrow(f.getBoolean("actif", true),
                f.getString("objet", "SPECTRAL_ARROW"), (float) f.getDouble("taille", 0.8), f.getDouble("devant", 2.2),
                f.getDouble("hauteur", 0.7), f.getDouble("distance-min", 6), f.getDouble("arrive", 4),
                rgb(f.getString("lueur", "")), (float) f.getDouble("rotation-en-plus", 0),
                (float) f.getDouble("taille-texte", 0.6), f.getDouble("texte-au-dessus", 0.35));
        return new AccueilConfig(true, stay, tomorrow, tutorial, arrival, arrow);
    }

    private static List<Step> steps(ConfigurationSection sec) {
        List<Step> out = new ArrayList<>();
        if (sec == null) {
            return out;
        }
        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            List<String> doneIf = s.getStringList("fait-si");
            for (String cond : doneIf) {
                if (!Condition.valid(cond)) {
                    throw new IllegalArgumentException("accueil.tutoriel.etapes." + id + ".fait-si : condition sans opérateur, " + cond);
                }
            }
            if (doneIf.isEmpty()) {
                throw new IllegalArgumentException("accueil.tutoriel.etapes." + id + " : fait-si manquant");
            }
            out.add(new Step(id, s.getString("texte", id), List.copyOf(s.getStringList("description")),
                    List.copyOf(doneIf), s.getString("clic", ""), s.getString("icone", "PAPER"), s.getString("cible", ""),
                    s.getString("cible-nom", ""), List.copyOf(s.getStringList("debut")),
                    List.copyOf(s.getStringList("recompense")), s.getString("recompense-texte", "")));
        }
        return List.copyOf(out);
    }

    /** « 40 commande » : la commande part 40 ticks après le début de l'arrivée. */
    private static List<Timed> timed(List<String> lines) {
        List<Timed> out = new ArrayList<>();
        for (String line : lines) {
            String l = line.trim();
            int sp = l.indexOf(' ');
            if (sp < 0) {
                throw new IllegalArgumentException("accueil.arrivee.commandes : « ticks commande » attendu, " + line);
            }
            try {
                out.add(new Timed(Math.max(0, Long.parseLong(l.substring(0, sp))), l.substring(sp + 1).trim()));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("accueil.arrivee.commandes : ticks illisibles, " + line);
            }
        }
        return List.copyOf(out);
    }

    private static BossBar.Color color(String name) {
        try {
            return BossBar.Color.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("couleur de barre inconnue : " + name);
        }
    }

    /** « #5CFFB0 » → 0x5CFFB0 ; vide = pas de lueur (-1). */
    private static int rgb(String hex) {
        if (hex == null || hex.isBlank()) {
            return -1;
        }
        try {
            return Integer.parseInt(hex.trim().replace("#", ""), 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("accueil.fleche.lueur : couleur illisible, " + hex);
        }
    }
}
