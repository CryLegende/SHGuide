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
    void unSautNeLaDeplacePasMemeAvecUnGrosPing() {
        for (double lead : new double[]{2, 3.5, 5, 8}) {
            Suivi s = new Suivi();
            s.follow(0, 64, 0, true, lead, R);
            for (int n = 0; n < 4; n++) {
                for (double h : JUMP) {
                    s.follow(0, 64 + h, 0, h == 0, lead, R);
                    assertEquals(64, s.y, 1e-9, "avance " + lead);
                }
            }
        }
    }

    @Test
    void unSautAuxPaquetsIrreguliersNeLaDeplacePas() {
        Suivi s = new Suivi();
        s.follow(0, 64, 0, true, 3, R);
        for (int i = 0; i < JUMP.length; i++) {
            // Deux paquets d'un coup, puis rien
            double h = JUMP[Math.min(JUMP.length - 1, i % 2 == 0 ? i + 1 : i)];
            s.follow(0, 64 + h, 0, h == 0, 3, R);
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
        assertEquals(59, s.y, 0.5, "à sa hauteur");
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
            assertTrue(s.x <= before + 0.05 && s.x >= px - 0.15, "revient vers le joueur presque sans le dépasser, " + (s.x - px));
            before = s.x;
        }
        assertEquals(px, s.x, 0.001);
    }

    /** Positions à plat d'un double saut vers +X : course à 0,28, puis élan de 1 bloc par tick que l'air freine. */
    private static double[] doubleJump(int at, int n) {
        double[] out = new double[n];
        double v = 0.28;
        for (int i = 1; i < n; i++) {
            v = i == at ? 1 : i > at ? Math.max(0.28, v * 0.91 + 0.0255) : 0.28;
            out[i] = out[i - 1] + v;
        }
        return out;
    }

    /** Pire retard de la flèche sur la place réelle du joueur quand son client la dessine (3 ticks plus tard). */
    private static double worstLag(boolean told) {
        double[] p = doubleJump(30, 70);
        Suivi s = new Suivi();
        double worst = 0;
        for (int i = 0; i + 3 < p.length; i++) {
            // Le serveur envoie l'élan, et ne voit le joueur bouger que 2 ticks plus tard
            if (told && i == 28) {
                s.impulse(1, 0.8, 0, R);
            }
            s.follow(p[i], 64, 0, i < 28 || i > 55, 3, R);
            if (i > 5) {
                worst = Math.max(worst, p[i + 3] - s.x);
            }
        }
        return worst;
    }

    @Test
    void unElanAnnonceNeLaLaissePasDerriere() {
        assertTrue(worstLag(false) > 1.5, "sans annonce, elle est rattrapée, " + worstLag(false));
        assertTrue(worstLag(true) < 0.9, "annoncé, elle part avec lui, " + worstLag(true));
    }

    @Test
    void unPetitCoupNEstPasUnElan() {
        Suivi s = new Suivi();
        s.follow(0, 64, 0, true, 3, R);
        s.impulse(0, 0.4, 0, R);
        s.follow(0, 64, 0, true, 3, R);
        assertEquals(0, s.x, 1e-9);
    }

    @Test
    void unDoubleSautLaFaitMonterAvecLui() {
        Suivi s = new Suivi();
        double y = 64;
        double vy = 0.8;
        for (int i = 0; i < 10; i++) {
            s.follow(0, 64, 0, true, 3, R);
        }
        for (int i = 0; i < 10; i++) {
            y += vy;
            vy = (vy - 0.08) * 0.98;
            s.follow(0, y, 0, false, 3, R);
        }
        assertTrue(y - s.y < 2, "jamais loin sous les pieds, " + (y - s.y));
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
