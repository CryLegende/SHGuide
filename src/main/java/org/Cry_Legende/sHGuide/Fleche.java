package org.Cry_Legende.sHGuide;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Flèche vers une cible : un item display posé autour du joueur, du côté de la cible, à hauteur fixe au-dessus du sol, et
 * la distance au-dessus. Tourner la tête, sauter ou se baisser ne la bouge pas. Les deux entités ne sont montrées qu'à ce
 * joueur, ne sont jamais sauvegardées et glissent d'une position à l'autre (interpolation du client) ; rien n'est envoyé
 * tant que la flèche ne change pas.
 *
 * Une seule flèche par joueur : une cible de la section « fleches » passe avant l'étape du tutoriel.
 */
final class Fleche {

    /**
     * Cible en plus du tutoriel : « cible » (monde x y z), « nom » et « texte » avec placeholders ; un texte remplace le
     * nom et la distance. « arrive » et « distance-min » valent ceux de accueil.fleche s'ils sont absents (-1).
     */
    record Source(String id, String target, String name, String text, double arrived, double minDistance) {
    }

    private record Target(Location place, String name, String text, double arrived, double minDistance) {
    }

    private static final class Arrow {
        final Suivi follow = new Suivi();
        Target tutorial;
        Target other;
        ItemDisplay item;
        TextDisplay label;
        Location sent;
        Quaternionf sentTurn;
        String lastLabel = "";

        Target target() {
            return other != null ? other : tutorial;
        }
    }

    private final SHGuide plugin;
    private final Map<UUID, Arrow> arrows = new HashMap<>();
    private int reads;

    Fleche(SHGuide plugin) {
        this.plugin = plugin;
    }

    /** Cible de l'étape du tutoriel ; null l'enlève. */
    void setTarget(Player p, Location target, String name) {
        Target t = target == null || !plugin.settings().accueil().arrow().enabled() ? null
                : new Target(target, name == null ? "" : name, "", -1, -1);
        set(p, t, false);
    }

    private void set(Player p, Target t, boolean other) {
        Arrow a = t == null ? arrows.get(p.getUniqueId()) : arrows.computeIfAbsent(p.getUniqueId(), k -> new Arrow());
        if (a == null) {
            return;
        }
        if (other) {
            a.other = t;
        } else {
            a.tutorial = t;
        }
        Target now = a.target();
        if (now == null) {
            arrows.remove(p.getUniqueId());
            despawn(a);
        }
    }

    void clear(Player p) {
        Arrow a = arrows.remove(p.getUniqueId());
        if (a != null) {
            despawn(a);
        }
    }

    void clearAll() {
        for (Arrow a : arrows.values()) {
            despawn(a);
        }
        arrows.clear();
    }

    /**
     * Relit les cibles de la section « fleches », la première lisible est gardée : souvent pour un joueur qui en a déjà
     * une, plus rarement pour les autres.
     */
    void readSources() {
        AccueilConfig.Arrow cfg = plugin.settings().accueil().arrow();
        List<Source> sources = cfg.enabled() ? plugin.settings().arrowSources() : List.of();
        boolean all = reads % Math.max(1, cfg.tuning().readTicks()) == 0;
        boolean active = reads % Math.max(1, cfg.tuning().readActiveTicks()) == 0;
        reads++;
        if (!all && !active) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            Arrow a = arrows.get(p.getUniqueId());
            if (!all && (a == null || a.other == null)) {
                continue;
            }
            Target found = null;
            for (Source s : sources) {
                Location place = place(PlaceholderAPI.setPlaceholders(p, s.target()));
                if (place != null) {
                    found = new Target(place, PlaceholderAPI.setPlaceholders(p, s.name()), PlaceholderAPI.setPlaceholders(p, s.text()),
                            s.arrived(), s.minDistance());
                    break;
                }
            }
            set(p, found, true);
        }
    }

    /** « monde x y z » ; null si vide, monde inconnu ou nombres illisibles. */
    static Location place(String text) {
        String[] parts = text.trim().split("\\s+");
        if (parts.length < 4) {
            return null;
        }
        World w = Bukkit.getWorld(parts[0]);
        if (w == null) {
            return null;
        }
        try {
            return new Location(w, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** À chaque tick. */
    void tick() {
        if (arrows.isEmpty()) {
            return;
        }
        AccueilConfig.Arrow cfg = plugin.settings().accueil().arrow();
        Iterator<Map.Entry<UUID, Arrow>> it = arrows.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Arrow> en = it.next();
            Player p = Bukkit.getPlayer(en.getKey());
            Arrow a = en.getValue();
            if (p == null || !cfg.enabled()) {
                despawn(a);
                it.remove();
                continue;
            }
            Target target = a.target();
            Location pl = p.getLocation();
            if (target.place().getWorld() != pl.getWorld() || p.isDead() || p.isInsideVehicle()) {
                despawn(a);
                continue;
            }
            double dist = pl.distance(target.place());
            if (dist < (target.arrived() >= 0 ? target.arrived() : cfg.arrivedDistance())
                    || dist < (target.minDistance() >= 0 ? target.minDistance() : cfg.minDistance()) && a.item == null) {
                despawn(a);
                continue;
            }
            boolean fresh = a.item == null || !a.item.isValid() || !a.follow.ready();
            boolean standing = p.isOnGround() || p.isFlying() || p.isClimbing() || p.isInWater() || p.isGliding();
            FlecheReglages tuning = cfg.tuning();
            a.follow.follow(pl.getX(), pl.getY(), pl.getZ(), standing, lead(p, tuning), tuning);
            a.follow.turn(target.place().getX(), target.place().getZ(), tuning);
            place(pl.getWorld(), a.follow, cfg, fresh);
            draw(p, a, pl, target, dist, cfg);
        }
    }

    /** Ticks d'avance : le temps que le mouvement arrive au serveur et que la flèche revienne au client. */
    private static double lead(Player p, FlecheReglages r) {
        return Math.min(r.leadMax(), r.glide() - 1 + p.getPing() / 50.0);
    }

    /** Un mur entre le joueur et la flèche la rapproche, une marche la relève. */
    private static void place(World world, Suivi f, AccueilConfig.Arrow cfg, boolean now) {
        Vector out = new Vector(Math.cos(f.heading), 0, Math.sin(f.heading));
        FlecheReglages r = cfg.tuning();
        double low = free(world, f, cfg.height(), out, cfg.ahead(), r);
        if (low >= cfg.ahead()) {
            f.place(cfg.ahead(), 0, now, r);
            return;
        }
        double high = free(world, f, cfg.height() + r.stepLift(), out, cfg.ahead(), r);
        if (high > low) {
            f.place(Math.max(r.wallMin(), high), r.stepLift(), now, r);
        } else {
            f.place(Math.max(r.wallMin(), low), 0, now, r);
        }
    }

    private static double free(World world, Suivi f, double height, Vector out, double ahead, FlecheReglages r) {
        RayTraceResult hit = world.rayTraceBlocks(new Location(world, f.x, f.y + height, f.z), out, ahead + r.wallGap(),
                FluidCollisionMode.NEVER, true);
        return hit == null ? ahead : Math.hypot(hit.getHitPosition().getX() - f.x, hit.getHitPosition().getZ() - f.z) - r.wallGap();
    }

    private void draw(Player p, Arrow a, Location pl, Target target, double dist, AccueilConfig.Arrow cfg) {
        Suivi f = a.follow;
        FlecheReglages r = cfg.tuning();
        double height = cfg.height() + f.lift;
        double ox = Math.cos(f.heading) * f.radius;
        double oz = Math.sin(f.heading) * f.radius;
        Location pos = new Location(pl.getWorld(), f.x + ox, f.y + height, f.z + oz);
        double flat = Math.max(0.01, Math.hypot(target.place().getX() - f.x, target.place().getZ() - f.z));
        Vector3f direction = new Vector3f((float) (Math.cos(f.heading) * flat),
                (float) (target.place().getY() - pos.getY()), (float) (Math.sin(f.heading) * flat));
        Vector3f toEyes = new Vector3f((float) -ox, (float) (r.eyes() - height), (float) -oz);
        Quaternionf turn = Orientation.of(direction, toEyes, cfg.yawOffset(), r);
        if (a.item == null || !a.item.isValid()) {
            spawn(p, a, pos, turn, cfg);
        } else {
            if (a.sent.distanceSquared(pos) > r.sendMove() * r.sendMove()) {
                a.sent = pos;
                a.item.teleport(pos);
                a.label.teleport(pos.clone().add(0, cfg.labelAbove(), 0));
            }
            if (Math.abs(a.sentTurn.dot(turn)) < Math.cos(Math.toRadians(r.sendTurn()) / 2)) {
                a.sentTurn = turn;
                a.item.setInterpolationDelay(0);
                a.item.setTransformation(transformation(turn, cfg));
            }
        }
        String label = label(target, pl, dist, r);
        if (!label.equals(a.lastLabel)) {
            a.lastLabel = label;
            a.label.text(Messages.chat(label));
        }
    }

    private String label(Target target, Location pl, double dist, FlecheReglages r) {
        double dy = target.place().getY() - pl.getY();
        String height = "";
        if (Math.abs(dy) >= r.markHeight() && Math.abs(dy) / dist >= Math.sin(Math.toRadians(r.markSlope()))) {
            height = plugin.messages().text(dy > 0 ? "accueil.fleche-haut" : "accueil.fleche-bas");
        }
        String distance = Integer.toString((int) Math.round(dist));
        return target.text().isEmpty() ? plugin.messages().text("accueil.fleche", "nom", target.name(), "distance", distance, "hauteur", height)
                : Messages.replace(target.text(), "distance", distance, "hauteur", height);
    }

    private static Transformation transformation(Quaternionf turn, AccueilConfig.Arrow cfg) {
        return new Transformation(new Vector3f(), turn, new Vector3f(cfg.scale(), cfg.scale(), cfg.scale()), new Quaternionf());
    }

    private void spawn(Player p, Arrow a, Location pos, Quaternionf turn, AccueilConfig.Arrow cfg) {
        remove(a);
        a.sent = pos;
        a.sentTurn = turn;
        Material mat = Material.matchMaterial(cfg.item());
        ItemStack stack = new ItemStack(mat == null || !mat.isItem() ? Material.SPECTRAL_ARROW : mat);
        a.item = pos.getWorld().spawn(pos, ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.setVisibleByDefault(false);
            e.setItemStack(stack);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setBillboard(Display.Billboard.FIXED);
            e.setTransformation(transformation(turn, cfg));
            e.setTeleportDuration(cfg.tuning().glide());
            e.setInterpolationDuration(cfg.tuning().glide());
            e.setBrightness(new Display.Brightness(15, 15));
            e.setShadowRadius(0);
            if (cfg.glowColor() >= 0) {
                e.setGlowing(true);
                e.setGlowColorOverride(Color.fromRGB(cfg.glowColor()));
            }
        });
        a.label = pos.getWorld().spawn(pos.clone().add(0, cfg.labelAbove(), 0), TextDisplay.class, e -> {
            e.setPersistent(false);
            e.setVisibleByDefault(false);
            e.setBillboard(Display.Billboard.CENTER);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            e.setShadowed(true);
            e.setTeleportDuration(cfg.tuning().glide());
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(cfg.labelScale(), cfg.labelScale(), cfg.labelScale()), new Quaternionf()));
        });
        p.showEntity(plugin, a.item);
        p.showEntity(plugin, a.label);
    }

    private static void despawn(Arrow a) {
        remove(a);
        a.follow.reset();
    }

    private static void remove(Arrow a) {
        if (a.item != null) {
            a.item.remove();
            a.item = null;
        }
        if (a.label != null) {
            a.label.remove();
            a.label = null;
        }
        a.lastLabel = "";
    }
}
