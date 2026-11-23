package org.Cry_Legende.sHGuide;

import java.util.Map;

/** Remplace les {jetons} d'un texte ; un jeton inconnu reste tel quel. */
final class Tokens {

    private Tokens() {
    }

    static String apply(String text, Map<String, String> tokens) {
        if (text == null) {
            return "";
        }
        if (text.indexOf('{') < 0) {
            return text;
        }
        String out = text;
        for (Map.Entry<String, String> e : tokens.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }
}
