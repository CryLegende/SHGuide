package org.Cry_Legende.sHGuide;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrientationTest {

    /** Yeux vus de la flèche : 0,9 bloc au-dessus, 1,7 derrière (joueur tourné vers +Z). */
    private static final Vector3f EYES = new Vector3f(0, 0.9f, -1.7f);

    private static Vector3f tip(Vector3f d) {
        return Orientation.of(d, EYES, 0).transform(new Vector3f(Orientation.TIP));
    }

    private static Vector3f dir(double yaw, double pitch) {
        double y = Math.toRadians(yaw);
        double p = Math.toRadians(pitch);
        return new Vector3f((float) (Math.sin(y) * Math.cos(p)), (float) Math.sin(p), (float) (Math.cos(y) * Math.cos(p)));
    }

    private static void near(Vector3f expected, Vector3f actual) {
        assertTrue(expected.distance(actual) < 1e-3, expected + " attendu, " + actual);
    }

    @Test
    void pointeVersLaCibleALaMemeHauteur() {
        for (int yaw = 0; yaw < 360; yaw += 10) {
            near(dir(yaw, 0), tip(dir(yaw, 0)));
        }
    }

    @Test
    void aPlatSousQuaranteDegres() {
        for (int yaw = 0; yaw < 360; yaw += 30) {
            for (int pitch = -40; pitch <= 40; pitch += 10) {
                Quaternionf q = Orientation.of(dir(yaw, pitch), EYES, 0);
                near(dir(yaw, 0), q.transform(new Vector3f(Orientation.TIP)));
                near(new Vector3f(0, 1, 0), q.transform(new Vector3f(Orientation.FACE)));
            }
        }
    }

    @Test
    void verticaleQuandLaCibleEstAuDessusOuAuDessous() {
        for (int yaw = 0; yaw < 360; yaw += 30) {
            near(new Vector3f(0, 1, 0), tip(dir(yaw, 65)));
            near(new Vector3f(0, -1, 0), tip(dir(yaw, -80)));
        }
        near(new Vector3f(0, 1, 0), tip(new Vector3f(0, 5, 0)));
        near(new Vector3f(0, -1, 0), tip(new Vector3f(0, -5, 0)));
    }

    @Test
    void toujoursLisible() {
        Vector3f eyes = new Vector3f(EYES).normalize();
        for (int yaw = 0; yaw < 360; yaw += 5) {
            for (int pitch = -90; pitch <= 90; pitch++) {
                Quaternionf q = Orientation.of(dir(yaw, pitch), EYES, 0);
                Vector3f t = q.transform(new Vector3f(Orientation.TIP));
                Vector3f n = q.transform(new Vector3f(Orientation.FACE));
                String where = " yaw " + yaw + " pente " + pitch;
                assertEquals(0, n.dot(t), 1e-3, "face perpendiculaire" + where);
                assertTrue(Math.toDegrees(Math.acos(Math.min(1, Math.abs(t.dot(eyes))))) > 26.5, "vue de bout" + where);
                assertTrue(n.dot(eyes) > 0.15, "vue par la tranche" + where + " " + n.dot(eyes));
            }
        }
    }

    @Test
    void tourneLeDessinDansSonPlan() {
        Vector3f t = Orientation.of(new Vector3f(0, 0, 1), EYES, 90).transform(new Vector3f(Orientation.TIP));
        assertEquals(0, t.y, 1e-3, "reste à plat");
        assertEquals(0, t.z, 1e-3, "un quart de tour");
    }
}
