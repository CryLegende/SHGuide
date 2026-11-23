package org.Cry_Legende.sHGuide;

import java.util.OptionalDouble;

/** Nombres lus dans les placeholders (« 12 273 », « 5.0 », « 2,5 ») et nombres affichés aux joueurs (« 1 000 »). */
final class Num {

    private Num() {
    }

    static OptionalDouble parse(String text) {
        if (text == null) {
            return OptionalDouble.empty();
        }
        String t = text.replace(" ", "").replace(" ", "").replace(" ", "").replace(',', '.').trim();
        if (t.isEmpty()) {
            return OptionalDouble.empty();
        }
        try {
            return OptionalDouble.of(Double.parseDouble(t));
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }

    static String format(double value) {
        long n = (long) Math.floor(value);
        String digits = Long.toString(Math.abs(n));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                out.append(' ');
            }
            out.append(digits.charAt(i));
        }
        return (n < 0 ? "-" : "") + out;
    }
}
