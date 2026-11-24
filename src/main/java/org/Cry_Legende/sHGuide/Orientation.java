package org.Cry_Legende.sHGuide;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Orientation de la flèche, posée entre le joueur et sa cible. Tant que la pente est faible, elle pointe devant, relevée
 * juste assez pour être lue par le joueur qui lui fait face (le texte donne le sens de la hauteur) ; quand la cible est
 * surtout au-dessus ou au-dessous du joueur, elle se redresse ou plonge, sa face tournée vers les yeux.
 */
final class Orientation {

    /** Pentes (en degrés) entre lesquelles la flèche passe d'à plat à verticale. */
    private static final double FLAT_UNTIL = 40;
    private static final double UPRIGHT_FROM = 60;
    /** Inclinaison (en degrés) à partir de laquelle la face de la flèche regarde entièrement les yeux du joueur. */
    private static final float FULL_TILT = 30f;
    /** Angle minimal (en degrés) entre la flèche et la ligne qui la joint aux yeux : assez pour lire sa pointe. */
    private static final double END_ON = 42;
    /** Pointe du dessin (en diagonale) et normale du dessin, telles que le client les pose sans transformation. */
    static final Vector3f TIP = new Vector3f(-1, 1, 0).normalize();
    static final Vector3f FACE = new Vector3f(0, 0, -1);

    private Orientation() {
    }

    /** Rotation qui amène la pointe du dessin vers « direction » ; « turn » tourne le dessin dans son plan (degrés). */
    static Quaternionf of(Vector3f direction, Vector3f toEyes, float turn) {
        Vector3f eyes = new Vector3f(toEyes).normalize();
        Vector3f d = readable(tilt(new Vector3f(direction).normalize()), eyes);
        Vector3f n = face(d, eyes);
        Vector3f tip = new Vector3f(TIP).rotateAxis((float) Math.toRadians(turn), FACE.x, FACE.y, FACE.z);
        Matrix3f from = new Matrix3f(tip, FACE, new Vector3f(FACE).cross(tip));
        Matrix3f to = new Matrix3f(d, n, new Vector3f(n).cross(d));
        return to.mul(from.transpose()).getNormalizedRotation(new Quaternionf());
    }

    /** Direction montrée : le cap de la cible, à plat puis de plus en plus redressé selon la pente. */
    static Vector3f tilt(Vector3f d) {
        double flat = Math.hypot(d.x, d.z);
        if (flat < 1e-4) {
            return new Vector3f(0, Math.signum(d.y), 0);
        }
        double slope = Math.toDegrees(Math.atan2(d.y, flat));
        double shown = Math.toRadians(Math.signum(slope) * 90 * smooth((Math.abs(slope) - FLAT_UNTIL) / (UPRIGHT_FROM - FLAT_UNTIL)));
        double c = Math.cos(shown) / flat;
        return new Vector3f((float) (d.x * c), (float) Math.sin(shown), (float) (d.z * c));
    }

    /** Écarte la direction de la ligne des yeux (dans les deux sens) quand elle en est trop proche. */
    private static Vector3f readable(Vector3f d, Vector3f toEyes) {
        float along = d.dot(toEyes);
        double limit = Math.cos(Math.toRadians(END_ON));
        if (Math.abs(along) <= limit) {
            return d;
        }
        Vector3f aside = perpendicular(new Vector3f(d), toEyes);
        if (aside.lengthSquared() < 1e-6f) {
            aside = perpendicular(new Vector3f(0, -1, 0), toEyes);
        }
        return aside.normalize((float) Math.sin(Math.toRadians(END_ON)))
                .add(new Vector3f(toEyes).mul((float) (Math.signum(along) * limit)));
    }

    /** Normale de la flèche : perpendiculaire à sa direction, vers le haut puis vers les yeux selon l'inclinaison. */
    private static Vector3f face(Vector3f d, Vector3f toEyes) {
        float slope = (float) Math.toDegrees(Math.asin(Math.max(-1f, Math.min(1f, d.y))));
        float w = (float) smooth(Math.abs(slope) / FULL_TILT);
        Vector3f n = perpendicular(new Vector3f(0, 1 - w, 0).add(new Vector3f(toEyes).mul(w)), d);
        if (n.lengthSquared() < 1e-4f) {
            n = perpendicular(new Vector3f(0, 1, 0), d);
        }
        if (n.lengthSquared() < 1e-4f) {
            n = perpendicular(new Vector3f(1, 0, 0), d);
        }
        return n.normalize();
    }

    private static double smooth(double t) {
        double x = Math.max(0, Math.min(1, t));
        return x * x * (3 - 2 * x);
    }

    private static Vector3f perpendicular(Vector3f v, Vector3f d) {
        return v.sub(new Vector3f(d).mul(v.dot(d)));
    }
}
