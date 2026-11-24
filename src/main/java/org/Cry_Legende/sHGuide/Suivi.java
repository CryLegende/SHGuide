package org.Cry_Legende.sHGuide;

/**
 * Ancre de la flèche autour du joueur, dans le monde : elle ne dépend ni du regard, ni de la hauteur des yeux. À plat,
 * elle suit le joueur en devançant un peu son mouvement (le serveur le voit en retard) ; en hauteur, elle reste au
 * niveau du dernier sol, un saut ne la déplace pas. Tout glisse d'un tick à l'autre, rien ne saute.
 */
final class Suivi {

    double x;
    double y;
    double z;
    /** Cap vers la cible, en radians (0 = +X, sens de atan2(z, x)). */
    double heading;
    /** Éloignement du joueur et rehaussement, adoucis. */
    double radius;
    double lift;

    private double px;
    private double pz;
    private double vx;
    private double vz;
    private double ground;
    private boolean ready;
    private boolean headed;

    void reset() {
        ready = false;
        headed = false;
    }

    boolean ready() {
        return ready;
    }

    void follow(double playerX, double playerY, double playerZ, boolean standing, double lead, FlecheReglages r) {
        follow(playerX, playerY, playerZ, standing, lead, 1, r);
    }

    /**
     * Position du joueur, « standing » s'il est porté (sol, échelle, eau, vol), ticks d'avance à donner, et ticks passés
     * depuis la dernière fois.
     */
    void follow(double playerX, double playerY, double playerZ, boolean standing, double lead, int ticks, FlecheReglages r) {
        double dx = (playerX - px) / ticks;
        double dz = (playerZ - pz) / ticks;
        if (!ready || dx * dx + dz * dz > r.teleport() * r.teleport() || Math.abs(playerY - y) > 8) {
            ground = playerY;
            y = playerY;
            vx = vz = 0;
            ready = true;
        } else {
            vx += (dx - vx) * over(r.easeSpeed(), ticks);
            vz += (dz - vz) * over(r.easeSpeed(), ticks);
        }
        px = playerX;
        pz = playerZ;
        if (standing) {
            ground = playerY;
        }
        double want = Math.max(playerY - r.jumpAbove(), Math.min(playerY + r.jumpBelow(), ground));
        y = approach(y, want, over(r.easeHeight(), ticks));
        x = playerX + vx * lead;
        z = playerZ + vz * lead;
    }

    void turn(double targetX, double targetZ, FlecheReglages r) {
        turn(targetX, targetZ, 1, r);
    }

    /** Tourne le cap vers la cible, sans à-coup quand elle change. */
    void turn(double targetX, double targetZ, int ticks, FlecheReglages r) {
        double dx = targetX - px;
        double dz = targetZ - pz;
        if (headed && dx * dx + dz * dz < r.headingMin() * r.headingMin()) {
            return;
        }
        double wanted = Math.atan2(dz, dx);
        if (!headed) {
            heading = wanted;
            headed = true;
            return;
        }
        double delta = Math.IEEEremainder(wanted - heading, 2 * Math.PI);
        heading += Math.abs(delta) < 1e-3 ? delta : delta * over(r.easeHeading(), ticks);
    }

    /** Éloignement et rehaussement voulus (mur devant, marche devant). */
    void place(double wantedRadius, double wantedLift, boolean now, int ticks, FlecheReglages r) {
        radius = now ? wantedRadius : approach(radius, wantedRadius, over(r.easePlace(), ticks));
        lift = now ? wantedLift : approach(lift, wantedLift, over(r.easePlace(), ticks));
    }

    /** Part du chemin faite en plusieurs ticks, pour une part par tick. */
    static double over(double part, int ticks) {
        return ticks == 1 ? part : 1 - Math.pow(1 - part, ticks);
    }

    private static double approach(double from, double to, double part) {
        return Math.abs(to - from) < 0.004 ? to : from + (to - from) * part;
    }
}
