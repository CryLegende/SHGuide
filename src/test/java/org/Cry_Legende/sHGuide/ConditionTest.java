package org.Cry_Legende.sHGuide;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionTest {

    private static Resolver of(Map<String, String> values) {
        return text -> {
            String out = text;
            for (var e : values.entrySet()) {
                out = out.replace(e.getKey(), e.getValue());
            }
            return out;
        };
    }

    @Test
    void numbersAndText() {
        Resolver r = of(Map.of("%p%", "5.0", "%t%", "Terminé", "%m%", "PROGRESS"));
        assertTrue(Condition.test("%p% == 5", r));
        assertTrue(Condition.test("%p% >= 3", r));
        assertFalse(Condition.test("%p% < 3", r));
        assertTrue(Condition.test("%t% == terminé", r));
        assertTrue(Condition.test("%m% != COMPLETED", r));
    }

    @Test
    void regexFindsInLeftValue() {
        Resolver r = of(Map.of("%id%", "p09_09"));
        assertTrue(Condition.test("%id% ~ ^p\\d\\d_09$", r));
        assertFalse(Condition.test("%id% ~ ^p\\d\\d_(0[1-46-8]|10)$", r));
        assertFalse(Condition.test("%id% ~ ([", r));
    }

    @Test
    void comparisonNeedsNumbers() {
        Resolver r = of(Map.of("%x%", "%x%"));
        assertFalse(Condition.test("%x% >= 1", r));
    }

    @Test
    void validNeedsAnOperator() {
        assertTrue(Condition.valid("a == b"));
        assertFalse(Condition.valid("%shguide_alerte%"));
    }
}
