package org.Cry_Legende.sHGuide;

/** Remplace les placeholders d'un texte pour un joueur donné (PlaceholderAPI en jeu, valeurs fixes en test). */
@FunctionalInterface
public interface Resolver {
    String resolve(String text);
}
