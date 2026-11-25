package org.Cry_Legende.sHGuide;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Lit les placeholders d'un joueur en gardant un moment ceux que la config dit coûteux (placeholders-lents : secondes).
 * Un par joueur connecté, fil principal.
 */
final class Lecteur {

    private record Valeur(String text, long readAt) {
    }

    private final Map<String, Valeur> kept = new HashMap<>();
    private final LongSupplier clock;

    Lecteur(LongSupplier clock) {
        this.clock = clock;
    }

    /** « now » : relit aussi les placeholders coûteux (le joueur ouvre un écran). */
    Resolver with(Resolver source, Map<String, Integer> slow, boolean now) {
        if (slow.isEmpty()) {
            return source;
        }
        return text -> {
            Integer seconds = slow.get(text);
            if (seconds == null) {
                return source.resolve(text);
            }
            long time = clock.getAsLong();
            Valeur v = kept.get(text);
            if (v != null && !now && time - v.readAt() < seconds * 1000L) {
                return v.text();
            }
            String read = source.resolve(text);
            kept.put(text, new Valeur(read, time));
            return read;
        };
    }
}
