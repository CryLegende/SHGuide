package org.Cry_Legende.sHGuide;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Flèche vers une cible ou le long d'un chemin : un item display posé autour du joueur, du côté où aller, à hauteur fixe
 * au-dessus du sol, et la distance au-dessus. Tourner la tête, sauter ou se baisser ne la bouge pas, dépasser un point du
 * chemin ne la retourne pas. Elle n'existe que chez ce joueur (paquets) et glisse d'une position à l'autre (interpolation du
 * client) ; rien n'est envoyé tant qu'elle ne change pas.
 *
 * Le fil principal ne fait que relever la position des joueurs et sonder les murs, pour un nombre limité de flèches par
 * tick ; le calcul et les paquets se font sur le fil de la flèche, seul à connaître ce que voit chaque client.
 *
 * Une seule flèche par joueur : une cible de la section « fleches » passe avant l'étape du tutoriel.
 */
final class Fleche implements Listener {

    /** Tour de cap qui fait sonder les murs à nouveau, en radians. */
    private static final double PROBE_TURN = 0.17;
    /** Un joueur immobile fait sonder les murs au plus tard après ce nombre de ticks (porte ouverte, bloc posé). */
    private static final int PROBE_AT_LEAST = 40;

    /**
     * Cible en plus du tutoriel : « cible » (monde x y z, ou un chemin monde x y z x y z …), « nom » et « texte » avec
     * placeholders ; un texte remplace le nom et la distance. « fin » : texte laissé quand la cible disparaît. « arrive » et
     * « distance-min » valent ceux de accueil.fleche s'ils sont absents (-1).
     */
    record Source(String id, String target, String name, String text, String end, double arrived, double minDistance) {
    }

    /** Points à suivre : x y z de chacun, un seul pour une cible fixe. */
    private record Target(World world, double[] points, String name, String text, double arrived, double minDistance) {

        double x() {
            return points[points.length - 3];
        }

        double y() {
            return points[points.length - 2];
        }

        double z() {
            return points[points.length - 1];
        }
    }

    /** Adieu : la flèche s'efface, son texte reste un moment. */
    private static final class Farewell {
        final String text;
        int left;
        boolean faded;

        Farewell(String text, int left) {
            this.text = text;
            this.left = left;
        }
    }

    /** Ce que le fil principal donne pour un tick ; ni cible ni adieu : la flèche se cache. */
    private record Step(Arrow arrow, Target target, Farewell farewell, double[] impulse, double x, double y, double z, boolean standing, double lead,
                        double distance, double low, double high, int epoch, int ticks, int glide, AccueilConfig.Arrow cfg) {
    }

    private static final class Arrow {
        final UUID id;
        final Player player;
        final int item = FlechePaquets.newEntityId();
        final int label = FlechePaquets.newEntityId();
        final int slot;
        /** Monde changé ou réapparition : le client a pu oublier la flèche. */
        final AtomicInteger epoch = new AtomicInteger();
        volatile Target tutorial;
        volatile Target other;
        volatile Source source;
        volatile Farewell farewell;
        volatile double[] impulse;
        volatile boolean gone;
        // Publié par le fil de la flèche
        volatile boolean shown;
        volatile boolean placed;
        volatile double ax;
        volatile double ay;
        volatile double az;
        volatile double heading;
        // Fil principal
        int lastStep = Integer.MIN_VALUE;
        int probedAt = Integer.MIN_VALUE;
        double probedX;
        double probedY;
        double probedZ;
        double probedHeading;
        double low;
        double high;
        // Fil de la flèche
        String readText = "";
        World readWorld;
        double[] readPoints;
        final Suivi follow = new Suivi();
        final Chemin path = new Chemin();
        double[] pathPoints;
        int shownEpoch;
        int shownGlide;
        float shownScale;
        double sx;
        double sy;
        double sz;
        Quaternionf sentTurn;
        String lastLabel = "";
        long labelKey = Long.MIN_VALUE;
        Target labelTarget;

        Arrow(Player player, int slot) {
            this.id = player.getUniqueId();
            this.player = player;
            this.slot = slot;
        }

        Target target() {
            Target o = other;
            return o != null ? o : tutorial;
        }
    }

    private final SHGuide plugin;
    private final boolean available;
    private final Map<UUID, Arrow> arrows = new ConcurrentHashMap<>();
    private final AtomicInteger slots = new AtomicInteger();
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SHGuide-Fleche");
        t.setDaemon(true);
        return t;
    });
    private int now;
    private int reads;

    Fleche(SHGuide plugin) {
        this.plugin = plugin;
        available = Bukkit.getPluginManager().isPluginEnabled("packetevents");
        if (!available) {
            plugin.getLogger().warning("packetevents absent : pas de flèche.");
            return;
        }
        worker.scheduleWithFixedDelay(() -> {
            try {
                readSources();
            } catch (RuntimeException | LinkageError e) {
                plugin.getLogger().log(Level.WARNING, "Lecture des cibles de la flèche impossible", e);
            }
        }, 1000, 50, TimeUnit.MILLISECONDS);
    }

    /** Cible de l'étape du tutoriel ; null l'enlève. */
    void setTarget(Player p, Location target, String name) {
        Target t = target == null || !plugin.settings().accueil().arrow().enabled() ? null
                : new Target(target.getWorld(), new double[]{target.getX(), target.getY(), target.getZ()}, name == null ? "" : name, "", -1, -1);
        set(p, t, false);
    }

    private void set(Player p, Target t, boolean other) {
        Arrow a = t == null || !available ? arrows.get(p.getUniqueId())
                : arrows.computeIfAbsent(p.getUniqueId(), k -> new Arrow(p, slots.incrementAndGet()));
        if (a == null) {
            return;
        }
        if (other) {
            a.other = t;
        } else {
            a.tutorial = t;
        }
        if (t != null) {
            a.farewell = null;
        } else if (a.target() == null && a.farewell == null) {
            remove(a);
        }
    }

    void clear(Player p) {
        Arrow a = arrows.get(p.getUniqueId());
        if (a != null) {
            remove(a);
        }
    }

    private void remove(Arrow a) {
        a.gone = true;
        arrows.remove(a.id, a);
        run(() -> hide(a));
    }

    /** Arrêt du plugin : les flèches sont retirées des clients avant de rendre la main. */
    void close() {
        arrows.values().forEach(this::remove);
        worker.shutdown();
        try {
            worker.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void run(Runnable r) {
        if (worker.isShutdown()) {
            return;
        }
        worker.execute(() -> {
            try {
                r.run();
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Flèche impossible", e);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent e) {
        touch(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        touch(e.getPlayer());
    }

    private void touch(Player p) {
        Arrow a = arrows.get(p.getUniqueId());
        if (a != null) {
            a.epoch.incrementAndGet();
        }
    }

    /** Élan envoyé au joueur (double saut, plaque, coup) : la flèche part avec lui. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent e) {
        Arrow a = arrows.get(e.getPlayer().getUniqueId());
        if (a != null && a.shown) {
            Vector v = e.getVelocity();
            a.impulse = new double[]{v.getX(), v.getY(), v.getZ()};
        }
    }

    /**
     * Relit les cibles de la section « fleches », la première lisible est gardée : souvent pour un joueur qui en a déjà
     * une, plus rarement pour les autres, une part des joueurs à la fois. Toutes les 50 ms, sur le fil de la flèche.
     */
    private void readSources() {
        AccueilConfig.Arrow cfg = plugin.settings().accueil().arrow();
        List<Source> sources = cfg.enabled() ? plugin.settings().arrowSources() : List.of();
        FlecheReglages r = cfg.tuning();
        reads++;
        if (sources.isEmpty() && arrows.isEmpty()) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            Arrow a = arrows.get(p.getUniqueId());
            int period = a != null && a.other != null ? r.readActiveTicks() : r.readTicks();
            if ((p.getEntityId() + reads) % period == 0) {
                read(p, a, sources, r);
            }
        }
    }

    private void read(Player p, Arrow a, List<Source> sources, FlecheReglages r) {
        for (Source s : sources) {
            String text = filled(p, s.target());
            if (text.isBlank()) {
                continue;
            }
            World world;
            double[] points;
            if (a != null && text.equals(a.readText)) {
                world = a.readWorld;
                points = a.readPoints;
            } else {
                points = points(text);
                world = points == null ? null : Bukkit.getWorld(text.trim().split("\\s+", 2)[0]);
            }
            if (world == null) {
                continue;
            }
            String name = filled(p, s.name());
            String over = filled(p, s.text());
            Target old = a == null ? null : a.other;
            if (old == null || old.points() != points || old.world() != world || !old.name().equals(name) || !old.text().equals(over)) {
                set(p, new Target(world, points, name, over, s.arrived(), s.minDistance()), true);
            }
            Arrow now = arrows.get(p.getUniqueId());
            if (now != null) {
                now.source = s;
                now.readText = text;
                now.readWorld = world;
                now.readPoints = points;
            }
            return;
        }
        if (a != null && a.other != null) {
            Source s = a.source;
            String end = s == null || !a.shown || a.tutorial != null || r.endTicks() == 0 ? "" : filled(p, s.end());
            a.readText = "";
            a.farewell = end.isBlank() ? null : new Farewell(end, r.endTicks());
            set(p, null, true);
        }
    }

    private static String filled(Player p, String text) {
        return text.indexOf('%') < 0 ? text : PlaceholderAPI.setPlaceholders(p, text);
    }

    /** « monde x y z » ; null si vide, monde inconnu ou nombres illisibles. */
    static Location place(String text) {
        double[] v = points(text);
        World w = v == null ? null : Bukkit.getWorld(text.trim().split("\\s+", 2)[0]);
        return w == null ? null : new Location(w, v[0], v[1], v[2]);
    }

    /** Nombres de « monde x y z x y z … » ; null si vide ou illisible. */
    static double[] points(String text) {
        String[] parts = text.trim().split("\\s+");
        int n = (parts.length - 1) / 3;
        if (n == 0) {
            return null;
        }
        double[] out = new double[3 * n];
        try {
            for (int i = 0; i < out.length; i++) {
                out[i] = Double.parseDouble(parts[i + 1]);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return out;
    }

    /** À chaque tick : « par-tick-max » flèches au plus, les autres attendent leur tour. */
    void tick() {
        if (arrows.isEmpty()) {
            return;
        }
        now++;
        AccueilConfig.Arrow cfg = plugin.settings().accueil().arrow();
        int count = arrows.size();
        int stride = Math.max(1, (count + cfg.tuning().perTick() - 1) / cfg.tuning().perTick());
        List<Step> steps = new ArrayList<>(count / stride + 1);
        for (Arrow a : arrows.values()) {
            if (stride > 1 && (a.slot + now) % stride != 0) {
                continue;
            }
            if (!a.player.isOnline()) {
                remove(a);
                continue;
            }
            Step s = step(a, cfg, stride);
            if (s != null) {
                steps.add(s);
            }
        }
        if (!steps.isEmpty()) {
            run(() -> {
                for (Step s : steps) {
                    advance(s);
                }
            });
        }
    }

    /** Relevé d'un joueur ; null s'il n'y a rien à faire. */
    private Step step(Arrow a, AccueilConfig.Arrow cfg, int stride) {
        Player p = a.player;
        Target target = a.target();
        Farewell farewell = target == null ? a.farewell : null;
        FlecheReglages r = cfg.tuning();
        boolean hide = target == null && farewell == null || !cfg.enabled();
        Location pl = null;
        double dist = 0;
        if (!hide) {
            pl = p.getLocation();
            hide = p.isDead() || p.isInsideVehicle() || target != null && target.world() != pl.getWorld();
            if (!hide && target != null) {
                dist = Math.sqrt(square(pl.getX() - target.x()) + square(pl.getY() - target.y()) + square(pl.getZ() - target.z()));
                hide = dist < (target.arrived() >= 0 ? target.arrived() : cfg.arrivedDistance())
                        || dist < (target.minDistance() >= 0 ? target.minDistance() : cfg.minDistance()) && !a.shown;
            }
        }
        if (hide) {
            a.lastStep = Integer.MIN_VALUE;
            if (farewell != null) {
                remove(a);
                return null;
            }
            return a.shown ? new Step(a, null, null, null, 0, 0, 0, false, 0, 0, 0, 0, 0, 1, 0, cfg) : null;
        }
        boolean standing = p.isOnGround() || p.isFlying() || p.isClimbing() || p.isInWater() || p.isGliding();
        int ticks = a.lastStep == Integer.MIN_VALUE ? 1 : Math.max(1, Math.min(10, now - a.lastStep));
        a.lastStep = now;
        double[] impulse = a.impulse;
        a.impulse = null;
        probe(a, pl, target, cfg);
        return new Step(a, target, farewell, impulse, pl.getX(), pl.getY(), pl.getZ(), standing, Math.min(r.leadMax(), r.glide() - 1 + r.lead() + p.getPing() / 50.0),
                dist, a.low, a.high, a.epoch.get(), ticks, r.glide() + stride - 1, cfg);
    }

    private static double square(double v) {
        return v * v;
    }

    /** Place libre devant le joueur, du côté de la cible : sondée quand il a bougé ou tourné, jamais à chaque tick. */
    private void probe(Arrow a, Location pl, Target target, AccueilConfig.Arrow cfg) {
        FlecheReglages r = cfg.tuning();
        boolean placed = a.placed && a.shown;
        if (!placed && target == null) {
            return;
        }
        double x = placed ? a.ax : pl.getX();
        double y = placed ? a.ay : pl.getY();
        double z = placed ? a.az : pl.getZ();
        double heading;
        if (placed) {
            heading = a.heading;
        } else {
            int next = target.points().length > 3 ? 3 : 0;
            heading = Math.atan2(target.points()[next + 2] - z, target.points()[next] - x);
        }
        boolean first = a.probedAt == Integer.MIN_VALUE || !placed;
        int since = first ? 0 : now - a.probedAt;
        if (!first) {
            double dx = x - a.probedX;
            double dy = y - a.probedY;
            double dz = z - a.probedZ;
            boolean changed = dx * dx + dy * dy + dz * dz > r.wallMove() * r.wallMove()
                    || Math.abs(Math.IEEEremainder(heading - a.probedHeading, 2 * Math.PI)) > PROBE_TURN;
            if (changed ? since < r.wallTicks() : since < PROBE_AT_LEAST) {
                return;
            }
        }
        a.probedAt = now;
        a.probedX = x;
        a.probedY = y;
        a.probedZ = z;
        a.probedHeading = heading;
        Vector out = new Vector(Math.cos(heading), 0, Math.sin(heading));
        double reach = cfg.ahead() + r.speedAheadMax();
        a.low = free(pl.getWorld(), x, y + cfg.height(), z, out, reach, r);
        a.high = a.low >= cfg.ahead() ? a.low : free(pl.getWorld(), x, y + cfg.height() + r.stepLift(), z, out, reach, r);
    }

    private static double free(World world, double x, double y, double z, Vector out, double ahead, FlecheReglages r) {
        RayTraceResult hit = world.rayTraceBlocks(new Location(world, x, y, z), out, ahead + r.wallGap(), FluidCollisionMode.NEVER, true);
        return hit == null ? ahead : Math.hypot(hit.getHitPosition().getX() - x, hit.getHitPosition().getZ() - z) - r.wallGap();
    }

    /** Fil de la flèche. */
    private void advance(Step s) {
        Arrow a = s.arrow();
        if (a.gone || s.target() == null && s.farewell() == null) {
            hide(a);
            return;
        }
        if (a.shown && a.shownEpoch != s.epoch()) {
            hide(a);
        }
        FlecheReglages r = s.cfg().tuning();
        if (s.farewell() != null && (!a.shown || (s.farewell().left -= s.ticks()) <= 0)) {
            if (a.farewell == s.farewell() && a.target() == null) {
                remove(a);
            }
            return;
        }
        boolean fresh = !a.shown || !a.follow.ready();
        Suivi f = a.follow;
        if (s.impulse() != null) {
            f.impulse(s.impulse()[0], s.impulse()[1], s.impulse()[2]);
        }
        f.follow(s.x(), s.y(), s.z(), s.standing(), s.lead(), s.ticks(), r);
        if (s.target() != null) {
            if (a.pathPoints != s.target().points()) {
                a.pathPoints = s.target().points();
                a.path.set(a.pathPoints);
            }
            a.path.follow(f.x, s.y(), f.z, f.ground, f.speed(), r);
            f.turn(a.path.aimX, a.path.aimZ, s.ticks(), r);
        }
        // Un mur entre le joueur et la flèche la rapproche, une marche la relève, la vitesse l'éloigne
        double wanted = s.cfg().ahead() + Math.max(0, Math.min(r.speedAheadMax(), f.forward() * r.speedAhead()));
        if (s.low() >= wanted) {
            f.place(wanted, 0, fresh, s.ticks(), r);
        } else if (s.high() > s.low()) {
            f.place(Math.max(r.wallMin(), Math.min(wanted, s.high())), r.stepLift(), fresh, s.ticks(), r);
        } else {
            f.place(Math.max(r.wallMin(), s.low()), 0, fresh, s.ticks(), r);
        }
        a.ax = f.x;
        a.ay = f.y;
        a.az = f.z;
        a.heading = f.heading;
        a.placed = true;
        draw(a, s, r);
    }

    private void draw(Arrow a, Step s, FlecheReglages r) {
        Suivi f = a.follow;
        AccueilConfig.Arrow cfg = s.cfg();
        double height = cfg.height() + f.lift;
        double ox = Math.cos(f.heading) * f.radius;
        double oz = Math.sin(f.heading) * f.radius;
        double x = f.x + ox;
        double y = f.y + height;
        double z = f.z + oz;
        Quaternionf turn = a.sentTurn;
        if (s.target() != null) {
            double flat = Math.max(0.01, a.path.run);
            double up = a.path.aimY - a.path.fromY - (a.path.path() ? 0 : height);
            Vector3f direction = new Vector3f((float) (Math.cos(f.heading) * flat), (float) up, (float) (Math.sin(f.heading) * flat));
            Vector3f toEyes = new Vector3f((float) -ox, (float) (r.eyes() + f.above - height), (float) -oz);
            turn = Orientation.of(direction, toEyes, cfg.yawOffset(), r);
        }
        String label = s.target() == null ? s.farewell().text : label(a, s, r);
        Player p = a.player;
        if (!a.shown) {
            Material mat = Material.matchMaterial(cfg.item());
            FlechePaquets.spawnItem(p, a.item, x, y, z, mat == null || !mat.isItem() ? Material.SPECTRAL_ARROW : mat, turn, cfg.scale(),
                    cfg.glowColor(), s.glide());
            FlechePaquets.spawnLabel(p, a.label, x, y + cfg.labelAbove(), z, Messages.chat(label), cfg.labelScale(), s.glide());
            a.shown = true;
            a.shownEpoch = s.epoch();
            a.shownGlide = s.glide();
            a.shownScale = cfg.scale();
            a.sentTurn = turn;
            a.lastLabel = label;
            a.sx = x;
            a.sy = y;
            a.sz = z;
            return;
        }
        if (s.farewell() != null && !s.farewell().faded) {
            // La flèche s'efface, son texte reste
            s.farewell().faded = true;
            a.shownScale = 0;
            FlechePaquets.scale(p, a.item, 0, r.endFade());
        } else if (s.target() != null && a.shownScale == 0) {
            a.shownScale = cfg.scale();
            FlechePaquets.scale(p, a.item, cfg.scale(), s.glide());
        }
        if (a.shownGlide != s.glide()) {
            a.shownGlide = s.glide();
            FlechePaquets.glide(p, a.item, a.label, s.glide());
        }
        double dx = x - a.sx;
        double dy = y - a.sy;
        double dz = z - a.sz;
        if (dx * dx + dy * dy + dz * dz > r.sendMove() * r.sendMove()) {
            a.sx = x;
            a.sy = y;
            a.sz = z;
            FlechePaquets.move(p, a.item, x, y, z);
            FlechePaquets.move(p, a.label, x, y + cfg.labelAbove(), z);
        }
        if (Math.abs(a.sentTurn.dot(turn)) < Math.cos(Math.toRadians(r.sendTurn()) / 2)) {
            a.sentTurn = turn;
            FlechePaquets.turn(p, a.item, turn);
        }
        if (!label.equals(a.lastLabel)) {
            a.lastLabel = label;
            FlechePaquets.text(p, a.label, Messages.chat(label));
        }
    }

    /** Texte au-dessus de la flèche, refait seulement quand la cible, la distance ou le signe de hauteur change. */
    private String label(Arrow a, Step s, FlecheReglages r) {
        double dy = a.path.rise;
        double far = Math.hypot(dy, a.path.riseRun);
        int mark = Math.abs(dy) >= r.markHeight() && Math.abs(dy) / Math.max(0.01, far) >= Math.sin(Math.toRadians(r.markSlope())) ? dy > 0 ? 1 : 2 : 0;
        long key = Math.round(s.distance()) * 4 + mark;
        if (a.shown && key == a.labelKey && a.labelTarget == s.target()) {
            return a.lastLabel;
        }
        a.labelKey = key;
        a.labelTarget = s.target();
        String height = mark == 0 ? "" : plugin.messages().text(mark == 1 ? "accueil.fleche-haut" : "accueil.fleche-bas");
        String distance = Long.toString(Math.round(s.distance()));
        return s.target().text().isEmpty()
                ? plugin.messages().text("accueil.fleche", "nom", s.target().name(), "distance", distance, "hauteur", height)
                : Messages.replace(s.target().text(), "distance", distance, "hauteur", height);
    }

    /** Toujours par un retrait : le client garde sinon la flèche figée. */
    private void hide(Arrow a) {
        if (a.shown) {
            a.shown = false;
            FlechePaquets.destroy(a.player, a.item, a.label);
        }
        a.placed = false;
        a.pathPoints = null;
        a.follow.reset();
    }
}
