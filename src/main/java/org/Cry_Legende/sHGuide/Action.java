package org.Cry_Legende.sHGuide;

import java.util.Locale;

/**
 * Ce que fait un clic, une ligne par action :
 * <ul>
 *   <li>« [menu] taches » ouvre un autre écran ;</li>
 *   <li>« [recuperer] jour » prend un cadeau (tout, jour, palier, palier:7, bienvenue, demain) ;</li>
 *   <li>« [objectif] » lance le clic de l'objectif du moment ;</li>
 *   <li>« [page] 1 » ou « [page] -1 » change de page ;</li>
 *   <li>« [fleche] » montre ou cache la flèche du tutoriel ;</li>
 *   <li>« [fermer] » ferme le menu ;</li>
 *   <li>« [console] ... », « [message] ... », « [son] ... » ;</li>
 *   <li>« [joueur] ... » ou sans préfixe : commande lancée par le joueur.</li>
 * </ul>
 */
record Action(Type type, String arg) {

    enum Type { MENU, RECUPERER, OBJECTIF, PAGE, FLECHE, FERMER, CONSOLE, MESSAGE, SON, JOUEUR }

    /** Null si la ligne est vide ; IllegalArgumentException si le préfixe n'existe pas. */
    static Action parse(String line) {
        String t = line == null ? "" : line.trim();
        if (t.isEmpty()) {
            return null;
        }
        if (!t.startsWith("[")) {
            return new Action(Type.JOUEUR, command(t));
        }
        int end = t.indexOf(']');
        String name = end < 0 ? t : t.substring(1, end).trim().toUpperCase(Locale.ROOT);
        String arg = end < 0 ? "" : t.substring(end + 1).trim();
        Type type;
        try {
            type = Type.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("action inconnue « " + t + " »");
        }
        if (type == Type.PAGE) {
            try {
                Integer.parseInt(arg);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("action « " + t + " » : un nombre est attendu");
            }
        }
        if (arg.isEmpty() && type != Type.OBJECTIF && type != Type.FLECHE && type != Type.FERMER) {
            if (type == Type.JOUEUR || type == Type.CONSOLE) {
                return null;
            }
            throw new IllegalArgumentException("action « " + t + " » incomplète");
        }
        return new Action(type, type == Type.JOUEUR || type == Type.CONSOLE ? command(arg) : arg);
    }

    private static String command(String c) {
        return c.startsWith("/") ? c.substring(1) : c;
    }
}
