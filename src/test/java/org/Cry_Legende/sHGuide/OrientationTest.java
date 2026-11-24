package org.Cry_Legende.sHGuide;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrientationTest {

    /** Flèche à 2,2 blocs du joueur du côté de la cible, 0,7 au-dessus du sol : yeux vus de la flèche pour un cap donné. */
    private static Vector3f eyes(double yaw) {
        double y = Math.toRadians(yaw);
        return new Vector3f((float) (-Math.sin(y) * 2.2), 0.92f, (float) (-Math.cos(y) * 2.2));
    }

    private static Vector3f dir(double yaw, double pitch) {
        double y = Math.toRadians(yaw);
        double p = Math.toRadians(pitch);
        return new Vector3f((float) (Math.sin(y) * Math.cos(p)), (float) Math.sin(p), (float) (Math.cos(y) * Math.cos(p)));
    }

    private static Vector3f tip(double yaw, double pitch) {
        return Orientation.of(dir(yaw, pitch), eyes(yaw), 0).transform(new Vector3f(Orientation.TIP));
    }

    @Test
    void leCapEstCeluiDeLaCible() {
        for (int yaw = 0; yaw < 360; yaw += 10) {
            for (int pitch = -38; pitch <= 38; pitch += 4) {
                Vector3f t = tip(yaw, pitch);
                Vector3f d = dir(yaw, 0);
                double flat = Math.hypot(t.x, t.z);
                assertTrue((t.x * d.x + t.z * d.z) / flat > 0.9999, "cap " + yaw + " pente " + pitch + " " + t);
            }
        }
    }

    @Test
    void releveeJusteAssezTantQueLaPenteEstFaible() {
        Vector3f flat = tip(0, 0);
        assertTrue(flat.y > 0.2 && flat.y < 0.5, "relevée, " + flat);
        for (int pitch = -38; pitch <= 38; pitch += 4) {
            assertTrue(tip(0, pitch).distance(flat) < 1e-3, "la même de -38° à 38°");
        }
    }

    @Test
    void verticaleQuandLaCibleEstAuDessusOuAuDessous() {
        for (int yaw = 0; yaw < 360; yaw += 30) {
            assertTrue(tip(yaw, 65).distance(new Vector3f(0, 1, 0)) < 1e-3);
            assertTrue(tip(yaw, -80).distance(new Vector3f(0, -1, 0)) < 1e-3);
        }
    }

    @Test
    void toujoursLisible() {
        for (int yaw = 0; yaw < 360; yaw += 5) {
            Vector3f e = eyes(yaw).normalize();
            for (int pitch = -90; pitch <= 90; pitch++) {
                Quaternionf q = Orientation.of(dir(yaw, pitch), eyes(yaw), 0);
                Vector3f t = q.transform(new Vector3f(Orientation.TIP));
                Vector3f n = q.transform(new Vector3f(Orientation.FACE));
                String where = " yaw " + yaw + " pente " + pitch;
                assertEquals(0, n.dot(t), 1e-3, "face perpendiculaire" + where);
                assertTrue(Math.toDegrees(Math.acos(Math.min(1, Math.abs(t.dot(e))))) > 41.5, "vue de bout" + where);
                assertTrue(n.dot(e) > 0.3, "vue par la tranche" + where + " " + n.dot(e));
            }
        }
    }

    @Test
    void tourneLeDessinDansSonPlan() {
        Quaternionf plain = Orientation.of(dir(0, 0), eyes(0), 0);
        Quaternionf turned = Orientation.of(dir(0, 0), eyes(0), 90);
        assertEquals(0, plain.transform(new Vector3f(Orientation.TIP)).dot(turned.transform(new Vector3f(Orientation.TIP))), 1e-3);
        assertEquals(1, plain.transform(new Vector3f(Orientation.FACE)).dot(turned.transform(new Vector3f(Orientation.FACE))), 1e-3);
    }
}
