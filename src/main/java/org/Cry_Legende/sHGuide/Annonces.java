package org.Cry_Legende.sHGuide;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Event annoncé par /shguide annonce : il devient l'objectif du moment de tout le serveur dans les
 * « annonce-avant-minutes » qui précèdent son début, et jusqu'à « annonce-apres-debut-minutes » après.
 * Annoncer le même event plusieurs fois (rappels du bot) remplace simplement l'annonce.
 */
final class Annonces {

    record Active(String text, String click, long startMillis) {
    }

    private volatile Active active;

    void set(long startMillis, String text, String click) {
        active = new Active(text, click, startMillis);
    }

    Active current(long now, int beforeMinutes, int graceMinutes) {
        Active a = active;
        if (a == null || now < a.startMillis() - beforeMinutes * 60_000L || now > a.startMillis() + graceMinutes * 60_000L) {
            return null;
        }
        return a;
    }

    /**
     * Début de l'event : « 10 » = dans 10 minutes, « 20:00 » = aujourd'hui à 20:00, ou demain si cette heure est
     * passée depuis plus de « graceMinutes ».
     */
    static OptionalLong startOf(String arg, ZonedDateTime now, int graceMinutes) {
        String a = arg.trim();
        if (a.matches("\\d{1,4}")) {
            return OptionalLong.of(now.toInstant().toEpochMilli() + Long.parseLong(a) * 60_000L);
        }
        try {
            LocalTime time = LocalTime.parse(a.length() == 4 ? "0" + a : a);
            ZonedDateTime start = now.with(time).withSecond(0).withNano(0);
            if (start.plusMinutes(graceMinutes).isBefore(now)) {
                start = start.plusDays(1);
            }
            return OptionalLong.of(start.toInstant().toEpochMilli());
        } catch (DateTimeParseException e) {
            return OptionalLong.empty();
        }
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
