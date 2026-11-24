package org.Cry_Legende.sHGuide;

import java.util.List;
import java.util.OptionalDouble;

/** Avancée d'une étape du tutoriel, pour sa barre : 0 quand l'étape commence, 1 quand elle est faite. */
final class EtapeProgres {

    private EtapeProgres() {
    }

    /**
     * Avancée brute, de 0 à 1 : « progres » sur « sur » (la plus avancée s'il y en a plusieurs), sinon la part de
     * conditions vraies de « progres-si », sinon celle de « fait-si ». Vide si aucun placeholder n'est un nombre (quête
     * pas encore chargée, ou finie à l'instant).
     */
    static OptionalDouble raw(AccueilConfig.Step st, Resolver r) {
        if (!st.progress().isEmpty()) {
            OptionalDouble best = OptionalDouble.empty();
            for (String text : st.progress()) {
                OptionalDouble v = Num.parse(r.resolve(text));
                if (v.isPresent() && (best.isEmpty() || v.getAsDouble() > best.getAsDouble())) {
                    best = v;
                }
            }
            return best.isEmpty() ? best : OptionalDouble.of(clamp(best.getAsDouble() / st.over()));
        }
        List<String> conditions = st.progressIf().isEmpty() ? st.doneIf() : st.progressIf();
        int ok = 0;
        for (String condition : conditions) {
            if (Condition.test(condition, r)) {
                ok++;
            }
        }
        return OptionalDouble.of(ok / (double) conditions.size());
    }

    /** Part parcourue depuis le début de l'étape, où l'avancée brute valait « base ». */
    static float since(double raw, double base) {
        if (base >= 1) {
            return raw >= 1 ? 1f : 0f;
        }
        return (float) clamp((raw - base) / (1 - base));
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
