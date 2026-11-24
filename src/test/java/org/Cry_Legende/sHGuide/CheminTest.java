package org.Cry_Legende.sHGuide;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheminTest {

    private static final FlecheReglages R = FlecheReglages.DEFAULT;

    private static Chemin chemin(double... points) {
        Chemin c = new Chemin();
        c.set(points);
        return c;
    }

    @Test
    void unPointSeulResteLaCible() {
        Chemin c = chemin(10, 70, 0);
        c.follow(0, 64, 0, 64, 0, R);
        assertEquals(10, c.aimX);
        assertEquals(10, c.run, 1e-9);
        assertEquals(6, c.rise, 1e-9);
        c.follow(20, 64, 0, 64, 0, R);
        assertEquals(10, c.aimX, "une cible dépassée est bien derrière");
    }

    @Test
    void depasserUnPointNeLaRetournePas() {
        Chemin c = chemin(0, 64, 0, 6, 64, 0, 40, 64, 0);
        for (double x = 0; x < 30; x += 0.1) {
            c.follow(x, 64, 0, 64, 1, R);
            assertTrue(c.aimX - x > 2.9, "toujours nettement devant, à " + x + " montre " + c.aimX);
        }
    }

    @Test
    void unTournantCoupeNeLaRetournePas() {
        // Angle droit, coupé en volant par-dessus le coin
        Chemin c = chemin(0, 64, 0, 20, 64, 0, 20, 64, 40);
        for (int i = 0; i <= 60; i++) {
            double t = i / 60.0;
            double x = 10 + 14 * t;
            double z = 22 * t;
            c.follow(x, 64 + 5 * Math.sin(Math.PI * t), z, 64, 1, R);
            assertTrue((c.aimX - x) * 14 + (c.aimZ - z) * 22 > 0, "devant, à " + x + " " + z + " montre " + c.aimX + " " + c.aimZ);
        }
    }

    @Test
    void unSautNeChangeNiLaDirectionNiLaPente() {
        Chemin c = chemin(0, 64, 0, 30, 64, 0);
        c.follow(5, 64, 0, 64, 0.3, R);
        double aim = c.aimX;
        for (double h = 0; h <= 5.2; h += 0.4) {
            c.follow(5, 64 + h, 0, 64, 0.3, R);
            assertEquals(aim, c.aimX, 1e-9);
            assertEquals(0, c.aimY - c.fromY, 1e-9);
        }
    }

    @Test
    void longueLigneDroiteMontreLeProchainTournant() {
        Chemin c = chemin(0, 64, 0, 30, 64, 0, 30, 64, 30);
        c.follow(2, 64, 0, 64, 0.28, R);
        assertEquals(30, c.aimX, 1e-9);
        assertEquals(0, c.aimZ, 1e-9, "au coin, pas à travers le mur");
        for (double x = 2; x <= 28.5; x += 0.28) {
            c.follow(x, 64, 0, 64, 0.28, R);
        }
        assertTrue(c.aimZ > 1, "le coin est proche : la suite, " + c.aimZ);
    }

    @Test
    void escalierEnColimacon() {
        // Deux tours l'un au-dessus de l'autre : on reste sur le sien
        double[] p = new double[3 * 33];
        for (int i = 0; i < 33; i++) {
            p[3 * i] = 3 * Math.cos(i * Math.PI / 8);
            p[3 * i + 1] = 64 + i * 0.5;
            p[3 * i + 2] = 3 * Math.sin(i * Math.PI / 8);
        }
        Chemin c = chemin(p);
        double before = Double.MAX_VALUE;
        for (int i = 0; i < 24; i++) {
            c.follow(p[3 * i], p[3 * i + 1], p[3 * i + 2], p[3 * i + 1], 0.2, R);
            assertEquals(p[3 * i + 1], c.fromY, 1.2, "sur le bon tour, marche " + i);
            assertTrue(c.left() < before);
            before = c.left();
        }
    }

    @Test
    void echelleMonteeEtChute() {
        Chemin c = chemin(0, 64, 0, 5, 64, 0, 5, 74, 0, 12, 74, 0);
        c.follow(5, 64, 0, 64, 0, R);
        assertTrue(c.aimY > 66 && c.run < 0.5, "au pied de l'échelle : vers le haut");
        c.follow(5, 70, 0, 70, 0, R);
        assertTrue(c.aimY >= 73 && c.run < 0.5, "dans l'échelle, " + c.aimY);
        c.follow(5, 73.5, 0, 73.5, 0, R);
        assertTrue(c.aimX > 7, "en haut : la suite, " + c.aimX);
        Chemin down = chemin(0, 90, 0, 5, 90, 0, 6, 50, 0, 20, 50, 0);
        down.follow(4.5, 90, 0, 90, 0.28, R);
        assertTrue(down.aimY < 90, "au bord : vers le bas");
        assertTrue(down.rise < -10, "nettement plus bas");
    }

    @Test
    void doubleSautMontreDAbordLeHautPuisLArrivee() {
        // Élan au bord (y 83), sommet, arrivée 33 blocs plus bas
        Chemin c = chemin(0, 83, 0, 6, 83, 0, 12, 86, 0, 18, 50, 0, 30, 50, 0);
        c.follow(5.5, 83, 0, 83, 0.28, R);
        assertTrue(c.aimY > 83, "au bord : vers le large, pas vers le bas, " + c.aimY);
        c.follow(16, 70, 0, 83, 0.6, R);
        assertTrue(c.aimX >= 17, "en chute : l'arrivée, " + c.aimX);
    }

    @Test
    void textesDUneCible() {
        assertEquals(3, Fleche.points("spawn 1 64 -2.5").length);
        assertEquals(9, Fleche.points("spawn 1 64 -2.5 2 64 0 3 64 1").length);
        assertEquals(3, Fleche.points("spawn 1 64 -2.5 0 0").length, "un reste est ignoré, comme avant");
        assertNull(Fleche.points("spawn 1 64"));
        assertNull(Fleche.points("spawn a b c"));
        assertNull(Fleche.points(""));
    }
}
