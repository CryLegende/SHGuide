package org.Cry_Legende.sHGuide;

/**
 * Ancre de la flèche autour du joueur, dans le monde : elle ne dépend ni du regard, ni de la hauteur des yeux. À plat,
 * elle suit le joueur en devançant un peu son mouvement (le serveur le voit en retard) ; en hauteur, elle reste au
 * niveau du dernier sol, un saut ne la déplace pas. Tout glisse d'un tick à l'autre, rien ne saute.
 */
final class Suivi {

    /** Un saut monte de 1,25 bloc : la flèche ne suit qu'au-delà de ces écarts avec le dernier sol. */
    private static final double ABOVE = 1.6;
    private static final double BELOW = 1.2;
    /** Déplacement (blocs en un tick) pris pour une téléportation. */
    private static final double TELEPORT = 2.5;
    /** En dessous de cette distance à plat, le cap n'a plus de sens : le dernier est gardé. */
    private static final double HEADING_MIN = 2;

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

    /** Un tick : position du joueur, « standing » s'il est porté (sol, échelle, eau, vol), ticks d'avance à donner. */
    void follow(double playerX, double playerY, double playerZ, boolean standing, double lead) {
        double dx = playerX - px;
        double dz = playerZ - pz;
        if (!ready || dx * dx + dz * dz > TELEPORT * TELEPORT || Math.abs(playerY - y) > 8) {
            ground = playerY;
            y = playerY;
            vx = vz = 0;
            ready = true;
        } else {
            vx = vx * 0.6 + dx * 0.4;
            vz = vz * 0.6 + dz * 0.4;
        }
        px = playerX;
        pz = playerZ;
        if (standing) {
            ground = playerY;
        }
        double want = Math.max(playerY - ABOVE, Math.min(playerY + BELOW, ground));
        y = approach(y, want, 0.35);
        x = playerX + vx * lead;
        z = playerZ + vz * lead;
    }

    /** Tourne le cap vers la cible, sans à-coup quand elle change. */
    void turn(double targetX, double targetZ) {
        double dx = targetX - px;
        double dz = targetZ - pz;
        if (headed && dx * dx + dz * dz < HEADING_MIN * HEADING_MIN) {
            return;
        }
        double wanted = Math.atan2(dz, dx);
        if (!headed) {
            heading = wanted;
            headed = true;
            return;
        }
        double delta = Math.IEEEremainder(wanted - heading, 2 * Math.PI);
        heading += Math.abs(delta) < 1e-3 ? delta : delta * 0.4;
    }

    /** Éloignement et rehaussement voulus (mur devant, marche devant). */
    void place(double wantedRadius, double wantedLift, boolean now) {
        radius = now ? wantedRadius : approach(radius, wantedRadius, 0.3);
        lift = now ? wantedLift : approach(lift, wantedLift, 0.3);
    }

    private static double approach(double from, double to, double part) {
        return Math.abs(to - from) < 0.004 ? to : from + (to - from) * part;
    }
}
