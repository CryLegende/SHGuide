package org.Cry_Legende.sHGuide;

import java.util.Map;

/** Event annoncé par /shguide annonce : objectif du moment de tout le serveur jusqu'à un peu après son début. */
final class Annonces {

    record Active(String text, String click, long startMillis) {
    }

    private volatile Active active;

    void set(int minutes, String text, String click, long now) {
        active = new Active(text, click, now + minutes * 60_000L);
    }

    Active current(long now, int graceMinutes) {
        Active a = active;
        return a == null || now > a.startMillis() + graceMinutes * 60_000L ? null : a;
    }

    static String label(Active a, long now, String soonTemplate, String startedTemplate) {
        long left = a.startMillis() - now;
        if (left <= 0) {
            return Tokens.apply(startedTemplate, Map.of("texte", a.text()));
        }
        long minutes = (left + 59_999) / 60_000;
        return Tokens.apply(soonTemplate, Map.of("texte", a.text(), "minutes", Long.toString(minutes)));
    }
}
