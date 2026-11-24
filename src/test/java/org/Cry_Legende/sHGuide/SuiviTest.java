package org.Cry_Legende.sHGuide;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuiviTest {

    private static final FlecheReglages R = FlecheReglages.DEFAULT;

    /** Hauteurs d'un saut du jeu, tick par tick. */
    private static final double[] JUMP = {0.42, 0.75, 1.0, 1.17, 1.25, 1.24, 1.15, 0.98, 0.73, 0.4, 0.0};

    @Test
    void unSautNeLaDeplacePas() {
        Suivi s = new Suivi();
        s.follow(0, 64, 0, true, 2, R);
        for (double h : JUMP) {
            s.follow(0, 64 + h, 0, h == 0, 2, R);
            assertEquals(64, s.y, 1e-9);
        }
    }

    @Test
    void unEscalierLaFaitMonterEnGlissant() {
        Suivi s = new Suivi();
        s.follow(0, 64, 0, true, 2, R);
        double before = s.y;
        for (int i = 1; i <= 6; i++) {
            s.follow(0, 65, 0, true, 2, R);
            assertTrue(s.y > before && s.y - before < 0.4, "monte sans à-coup, " + (s.y - before));
            before = s.y;
        }
        for (int i = 0; i < 20; i++) {
            s.follow(0, 65, 0, true, 2, R);
        }
        assertEquals(65, s.y, 1e-9);
    }

    @Test
    void uneChuteOuUnVolLaFaitSuivre() {
        Suivi s = new Suivi();
        s.follow(0, 64, 0, true, 2, R);
        for (int i = 0; i < 40; i++) {
            s.follow(0, 64 - Math.min(5, i * 0.5), 0, false, 2, R);
        }
        assertEquals(59 + 1.2, s.y, 0.01);
        for (int i = 0; i < 30; i++) {
            s.follow(0, 59, 0, true, 2, R);
        }
        assertEquals(59, s.y, 1e-9);
    }

    @Test
    void marcheReguliereDevanceeEtArretSansRebond() {
        Suivi s = new Suivi();
        s.follow(0, 64, 0, true, 3, R);
        double px = 0;
        for (int i = 0; i < 30; i++) {
            px += 0.28;
            s.follow(px, 64, 0, true, 3, R);
        }
        assertEquals(px + 0.28 * 3, s.x, 0.01);
        double before = s.x;
        for (int i = 0; i < 25; i++) {
            s.follow(px, 64, 0, true, 3, R);
            assertTrue(s.x <= before + 1e-9 && s.x >= px - 1e-9, "revient vers le joueur sans le dépasser");
            before = s.x;
        }
        assertEquals(px, s.x, 0.001);
    }

    @Test
    void uneTeleportationNeTrainePas() {
        Suivi s = new Suivi();
        s.follow(0, 64, 0, true, 3, R);
        s.follow(100, 80, 50, true, 3, R);
        assertEquals(100, s.x, 1e-9);
        assertEquals(80, s.y, 1e-9);
        assertEquals(50, s.z, 1e-9);
    }

    @Test
    void leCapTourneVersLaCibleEtResteQuandElleEstTropProche() {
        Suivi s = new Suivi();
        s.follow(0, 64, 0, true, 0, R);
        s.turn(10, 0, R);
        assertEquals(0, s.heading, 1e-9);
        for (int i = 0; i < 40; i++) {
            s.turn(0, 10, R);
        }
        assertEquals(Math.PI / 2, s.heading, 2e-3);
        double kept = s.heading;
        s.turn(-0.5, 0.3, R);
        assertEquals(kept, s.heading, 1e-9);
    }

    @Test
    void leCapPrendLeCheminLePlusCourt() {
        Suivi s = new Suivi();
        s.follow(0, 64, 0, true, 0, R);
        s.turn(-10, 1, R);
        double h = s.heading;
        s.turn(-10, -1, R);
        assertTrue(Math.abs(s.heading - h) < 0.2);
    }
}
