package org.Cry_Legende.sHGuide;

import java.util.OptionalDouble;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Condition « A opérateur B ». Les deux côtés passent par les placeholders du joueur, sauf l'expression régulière
 * de ~ (un % y serait abîmé). Deux nombres se comparent comme des nombres, sinon comme du texte sans la casse.
 */
final class Condition {

    private static final String[] OPS = {" == ", " != ", " >= ", " <= ", " > ", " < ", " ~ "};

    private Condition() {
    }

    static boolean valid(String expr) {
        if (expr == null) {
            return false;
        }
        for (String op : OPS) {
            if (expr.contains(op)) {
                return true;
            }
        }
        return false;
    }

    static boolean test(String expr, Resolver r) {
        if (expr == null) {
            return false;
        }
        for (String op : OPS) {
            int at = expr.indexOf(op);
            if (at < 0) {
                continue;
            }
            String left = r.resolve(expr.substring(0, at)).trim();
            String right = expr.substring(at + op.length());
            if (op.equals(" ~ ")) {
                try {
                    return Pattern.compile(right.trim()).matcher(left).find();
                } catch (PatternSyntaxException e) {
                    return false;
                }
            }
            right = r.resolve(right).trim();
            OptionalDouble a = Num.parse(left);
            OptionalDouble b = Num.parse(right);
            boolean numbers = a.isPresent() && b.isPresent();
            return switch (op) {
                case " == " -> numbers ? a.getAsDouble() == b.getAsDouble() : left.equalsIgnoreCase(right);
                case " != " -> numbers ? a.getAsDouble() != b.getAsDouble() : !left.equalsIgnoreCase(right);
                case " >= " -> numbers && a.getAsDouble() >= b.getAsDouble();
                case " <= " -> numbers && a.getAsDouble() <= b.getAsDouble();
                case " > " -> numbers && a.getAsDouble() > b.getAsDouble();
                default -> numbers && a.getAsDouble() < b.getAsDouble();
            };
        }
        return false;
    }
}
