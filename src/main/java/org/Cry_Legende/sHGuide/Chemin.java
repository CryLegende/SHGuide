package org.Cry_Legende.sHGuide;

/**
 * Chemin que montre la flèche : des points, entre deux points on va tout droit. Elle avance dessus avec le joueur et montre
 * toujours un endroit nettement devant lui : le prochain changement de direction, ou plus loin quand il s'en approche (et
 * d'autant plus loin qu'il va vite). Dépasser un point ne la retourne donc pas, et un saut ne l'incline pas : la pente est
 * celle du chemin. Un seul point : une cible fixe.
 */
final class Chemin {

    /** Endroit montré. */
    double aimX;
    double aimY;
    double aimZ;
    /** Hauteur du chemin là où est le joueur, et distance à plat de là à l'endroit montré. */
    double fromY;
    double run;
    /** Hauteur que prend le chemin un peu plus loin, et distance à plat jusque-là. */
    double rise;
    double riseRun;

    private double[] p = new double[0];
    private double[] along = new double[0];
    private double s;

    /** Nouveau chemin : x y z de chaque point, du joueur vers l'avant. */
    void set(double[] points) {
        p = points;
        along = new double[points.length / 3];
        for (int i = 1; i < along.length; i++) {
            double dx = x(i) - x(i - 1);
            double dy = y(i) - y(i - 1);
            double dz = z(i) - z(i - 1);
            along[i] = along[i - 1] + Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        s = 0;
    }

    boolean path() {
        return along.length > 1;
    }

    /** Longueur qui reste jusqu'au dernier point. */
    double left() {
        return along.length == 0 ? 0 : along[along.length - 1] - s;
    }

    /** Position du joueur (« groundY » : hauteur de son dernier sol) et sa vitesse à plat, en blocs par tick. */
    void follow(double px, double py, double pz, double groundY, double speed, FlecheReglages r) {
        if (along.length < 2) {
            aimX = p[0];
            aimY = p[1];
            aimZ = p[2];
            fromY = groundY;
            run = riseRun = Math.hypot(aimX - px, aimZ - pz);
            rise = aimY - groundY;
            return;
        }
        s = nearest(px, py, pz, r);
        double look = Math.max(r.pathAhead(), r.pathAhead() + speed * r.pathSpeed());
        double next = along[along.length - 1];
        for (double a : along) {
            if (a > s + 0.05) {
                next = a;
                break;
            }
        }
        double to = Math.max(s + look, next);
        double[] here = at(s);
        double[] aim = at(to);
        double[] far = at(s + r.markAhead());
        aimX = aim[0];
        aimY = aim[1];
        aimZ = aim[2];
        fromY = here[1];
        run = Math.hypot(aimX - here[0], aimZ - here[2]);
        rise = far[1] - here[1];
        riseRun = Math.hypot(far[0] - here[0], far[2] - here[2]);
    }

    /** Avancée sur le chemin au plus près du joueur, sans sauter loin devant ni loin derrière. */
    private double nearest(double px, double py, double pz, FlecheReglages r) {
        double best = s;
        double bestDistance = Double.MAX_VALUE;
        double low = s - r.pathBack();
        double high = s + r.pathWindow();
        for (int i = 0; i + 1 < along.length; i++) {
            if (along[i + 1] < low || along[i] > high) {
                continue;
            }
            double dx = x(i + 1) - x(i);
            double dy = y(i + 1) - y(i);
            double dz = z(i + 1) - z(i);
            double flat = dx * dx + dz * dz;
            boolean steep = flat < 0.25;
            double t = steep ? dy == 0 ? 0 : (py - y(i)) / dy : ((px - x(i)) * dx + (pz - z(i)) * dz) / flat;
            double len = along[i + 1] - along[i];
            double at = Math.max(low, Math.min(high, along[i] + Math.max(0, Math.min(1, t)) * len));
            at = Math.max(along[i], Math.min(along[i + 1], at));
            double part = len < 1e-9 ? 0 : (at - along[i]) / len;
            double qx = x(i) + dx * part;
            double qy = y(i) + dy * part;
            double qz = z(i) + dz * part;
            // La hauteur départage deux passages l'un au-dessus de l'autre, un saut ne compte presque pas
            double d = Math.hypot(px - qx, pz - qz) + Math.min(6, Math.abs(py - qy)) * (steep ? 0.5 : 0.15) + Math.abs(at - s) * 0.02;
            if (d < bestDistance) {
                bestDistance = d;
                best = at;
            }
        }
        return best;
    }

    private double[] at(double where) {
        int last = along.length - 1;
        if (where >= along[last]) {
            return new double[]{x(last), y(last), z(last)};
        }
        int i = 0;
        while (along[i + 1] < where) {
            i++;
        }
        double len = along[i + 1] - along[i];
        double part = len < 1e-9 ? 0 : Math.max(0, (where - along[i]) / len);
        return new double[]{x(i) + (x(i + 1) - x(i)) * part, y(i) + (y(i + 1) - y(i)) * part, z(i) + (z(i + 1) - z(i)) * part};
    }

    private double x(int i) {
        return p[3 * i];
    }

    private double y(int i) {
        return p[3 * i + 1];
    }

    private double z(int i) {
        return p[3 * i + 2];
    }
}
