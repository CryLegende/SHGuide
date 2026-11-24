package org.Cry_Legende.sHGuide;

import org.bukkit.configuration.ConfigurationSection;

/** Section « accueil.fleche.reglages » : mouvement, inclinaison et envois de la flèche. Tout a une valeur par défaut. */
record FlecheReglages(int glide, double leadMax, double jumpAbove, double jumpBelow, double teleport, double headingMin,
                      double easeHeight, double easeSpeed, double easeHeading, double easePlace,
                      double wallGap, double wallMin, double stepLift, double eyes,
                      double markHeight, double markSlope, double flatUntil, double uprightFrom, double fullTilt, double endOn,
                      double sendMove, double sendTurn, int readTicks, int readActiveTicks, int perTick, int wallTicks, double wallMove) {

    static final FlecheReglages DEFAULT = read(null);

    static FlecheReglages read(ConfigurationSection c) {
        return new FlecheReglages((int) at(c, "glissement-ticks", 3, 0, 20), at(c, "avance-max-ticks", 5, 0, 20),
                at(c, "saut-haut", 1.6, 0, 10), at(c, "saut-bas", 1.2, 0, 10), at(c, "teleportation", 2.5, 0.5, 50), at(c, "cap-min", 1, 0, 10),
                at(c, "douceur-hauteur", 0.35, 0.01, 1), at(c, "douceur-vitesse", 0.4, 0.01, 1), at(c, "douceur-cap", 0.4, 0.01, 1),
                at(c, "douceur-place", 0.3, 0.01, 1), at(c, "mur-ecart", 0.3, 0, 2), at(c, "mur-min", 0.8, 0.1, 5), at(c, "marche-releve", 0.8, 0, 3),
                at(c, "hauteur-yeux", 1.62, 0, 4), at(c, "signe-hauteur", 6, 0, 500), at(c, "signe-pente", 20, 0, 90),
                at(c, "plat-jusqua", 40, 0, 89), at(c, "vertical-des", 60, 1, 90), at(c, "face-inclinaison", 30, 1, 90),
                at(c, "lecture-angle", 42, 0, 80), at(c, "envoi-blocs", 0.01, 0, 1), at(c, "envoi-degres", 1, 0, 45),
                (int) at(c, "lecture-ticks", 10, 1, 200), (int) at(c, "lecture-active-ticks", 4, 1, 200),
                (int) at(c, "par-tick-max", 60, 1, 1000), (int) at(c, "mur-ticks", 4, 1, 40), at(c, "mur-deplacement", 0.25, 0, 5));
    }

    private static double at(ConfigurationSection c, String key, double fallback, double min, double max) {
        double v = c == null ? fallback : c.getDouble(key, fallback);
        if (v < min || v > max) {
            throw new IllegalArgumentException("accueil.fleche.reglages." + key + " : de " + min + " à " + max);
        }
        return v;
    }
}
