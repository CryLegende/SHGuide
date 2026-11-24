package org.Cry_Legende.sHGuide;

/**
 * Ancre de la flèche autour du joueur, dans le monde : elle ne dépend ni du regard, ni de la hauteur des yeux. À plat, elle
 * est là où le joueur sera quand son client la dessinera (le serveur le voit en retard, la flèche glisse) ; un élan donné
 * par le serveur (double saut, plaque) est compté dès son départ, la flèche n'est donc pas rattrapée. En hauteur, elle reste
 * au niveau du dernier sol pendant un saut normal ; plus haut ou plus bas (double saut, chute), elle vole avec lui, à sa
 * hauteur. Rien ne saute.
 */
final class Suivi {

    /** Freinage de l'air du jeu, à plat, et vitesse à partir de laquelle il compte. */
    private static final double DRAG = 0.91;
    private static final double FAST = 0.4;

    double x;
    double y;
    double z;
    /** Dernier sol. */
    double ground;
    /** Hauteur du joueur au-dessus de l'ancre quand il vole, 0 au sol et pendant un saut normal. */
    double above;
    /** Part du vol : 0 l'ancre reste au sol, 1 elle est à la hauteur du joueur. */
    private double flight;
    /** Cap vers la cible, en radians (0 = +X, sens de atan2(z, x)). */
    double heading;
    /** Éloignement du joueur et rehaussement, adoucis. */
    double radius;
    double lift;

    // Position et vitesse estimées
    private double px;
    private double pz;
    private double ex;
    private double ez;
    private double vx;
    private double vz;
    private double lastY;
    // Élan en attente : parti vers le client, pas encore vu par le serveur
    private boolean impulse;
    private int since;
    private double bx;
    private double by;
    private double bz;
    private double ox;
    private double oz;
    private double ix;
    private double iy;
    private double iz;
    private boolean ready;
    private boolean headed;

    void reset() {
        flight = above = 0;
        ready = false;
        headed = false;
        impulse = false;
    }

    boolean ready() {
        return ready;
    }

    /** Vitesse à plat, en blocs par tick. */
    double speed() {
        return Math.hypot(impulse ? ix : vx, impulse ? iz : vz);
    }

    /** Vitesse dans la direction de la flèche. */
    double forward() {
        return (impulse ? ix : vx) * Math.cos(heading) + (impulse ? iz : vz) * Math.sin(heading);
    }

    /** Élan que le serveur vient d'envoyer au joueur ; un élan faible n'est pas compté, le mouvement mesuré suffit. */
    void impulse(double vx, double vy, double vz, FlecheReglages r) {
        if (!ready || Math.hypot(vx - this.vx, vz - this.vz) < r.impulse()) {
            return;
        }
        impulse = true;
        since = 0;
        bx = ex;
        by = lastY;
        bz = ez;
        ox = this.vx;
        oz = this.vz;
        ix = vx;
        iy = vy;
        iz = vz;
    }

    void follow(double playerX, double playerY, double playerZ, boolean standing, double lead, FlecheReglages r) {
        follow(playerX, playerY, playerZ, standing, lead, 1, r);
    }

    /**
     * Position du joueur, « standing » s'il est porté (sol, échelle, eau, vol), ticks d'avance à donner, et ticks passés
     * depuis la dernière fois.
     */
    void follow(double playerX, double playerY, double playerZ, boolean standing, double lead, int ticks, FlecheReglages r) {
        double mx = (playerX - px) / ticks;
        double mz = (playerZ - pz) / ticks;
        double vy = (playerY - lastY) / ticks;
        if (!ready || mx * mx + mz * mz > r.teleport() * r.teleport() || Math.abs(playerY - y) > 8) {
            ground = playerY;
            y = playerY;
            ex = playerX;
            ez = playerZ;
            vx = vz = 0;
            vy = 0;
            impulse = false;
            ready = true;
        } else {
            estimate(playerX, playerZ, standing, ticks, r);
        }
        px = playerX;
        pz = playerZ;
        lastY = playerY;
        if (standing) {
            ground = playerY;
        }
        double rtt = Math.max(0, lead - r.lead() - (r.glide() - 1));
        double soon;
        if (impulse && (since += ticks) <= rtt + 1) {
            double n = since + r.glide() - 1 + r.lead();
            x = bx + ox * rtt + ix * glide(n);
            z = bz + oz * rtt + iz * glide(n);
            soon = by + rise(iy, n);
        } else {
            if (impulse) {
                impulse = false;
                vx = ix * DRAG * DRAG;
                vz = iz * DRAG * DRAG;
            }
            double n = !standing && Math.hypot(vx, vz) > FAST ? glide(lead) : lead;
            x = ex + vx * n;
            z = ez + vz * n;
            soon = standing ? playerY : playerY + rise(vy, lead);
            if (playerY >= ground - 0.1) {
                // Pas encore sous son dernier sol : il s'y posera
                soon = Math.max(soon, ground);
            }
            if (playerY <= ground + r.jumpAbove()) {
                // Sans élan, un saut ne monte pas plus haut qu'un saut
                soon = Math.min(soon, ground + r.jumpAbove());
            }
        }
        boolean flying = !standing && (soon > ground + r.jumpAbove() || soon < ground - r.jumpBelow());
        flight = standing ? 0 : flying ? Math.min(1, flight + ticks / r.flightTicks()) : flight;
        double up = r.jumpAbove() + (r.flightGap() - r.jumpAbove()) * flight;
        double down = r.jumpBelow() + (r.flightGap() - r.jumpBelow()) * flight;
        double want = Math.max(soon - up, Math.min(soon + down, ground));
        y = flight > 0 ? want : approach(y, want, over(Math.abs(want - ground) > 0.01 ? r.easeFlight() : r.easeHeight(), ticks));
        above = flight > 0 ? soon - y : 0;
    }

    /** Position et vitesse à plat, filtrées ; un écart net (départ, arrêt, choc) est pris tout de suite. */
    private void estimate(double playerX, double playerZ, boolean standing, int ticks, FlecheReglages r) {
        if (!standing && Math.hypot(vx, vz) > FAST) {
            double drag = Math.pow(DRAG, ticks);
            vx *= drag;
            vz *= drag;
        }
        ex += vx * ticks;
        ez += vz * ticks;
        double dx = playerX - ex;
        double dz = playerZ - ez;
        boolean sharp = Math.hypot(dx, dz) > r.sharp() * ticks;
        double a = sharp ? 1 : over(r.easePosition(), ticks);
        double b = (sharp ? Math.max(0.8, r.easeSpeed()) : r.easeSpeed()) / ticks;
        ex += a * dx;
        ez += a * dz;
        vx += b * dx;
        vz += b * dz;
    }

    /** Avancée en n ticks d'une vitesse que l'air freine. */
    private static double glide(double n) {
        return (1 - Math.pow(DRAG, n)) / (1 - DRAG);
    }

    /** Montée en n ticks d'une vitesse verticale, sous la pesanteur du jeu. */
    private static double rise(double vy, double n) {
        double sum = 0;
        for (int i = 0; i < n; i++) {
            vy = (vy - 0.08) * 0.98;
            sum += vy * Math.min(1, n - i);
        }
        return sum;
    }

    void turn(double targetX, double targetZ, FlecheReglages r) {
        turn(targetX, targetZ, 1, r);
    }

    /** Tourne le cap vers la cible, sans à-coup quand elle change. */
    void turn(double targetX, double targetZ, int ticks, FlecheReglages r) {
        double dx = targetX - x;
        double dz = targetZ - z;
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

    /** Éloignement et rehaussement voulus (mur devant, marche devant, vitesse). */
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
