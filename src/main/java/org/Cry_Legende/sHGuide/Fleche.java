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

    /** Durée des glissements du client, en ticks. */
    private static final int GLIDE = 3;
    /** Hauteur des yeux debout : la flèche n'est pas tournée autrement quand le joueur se baisse. */
    private static final double EYES = 1.62;
    /** Écart gardé avec un mur, éloignement minimal quand il est tout près, rehaussement devant une marche. */
    private static final double WALL_GAP = 0.3;
    private static final double WALL_MIN = 0.8;
    private static final double STEP_LIFT = 0.8;
    /** Le sens de la hauteur s'affiche à partir de cet écart (blocs) et de cette pente (sinus de 20°). */
    private static final double HEIGHT_MIN = 6;
    private static final double SLOPE_MIN = 0.34;
    /** Plus petits changements envoyés : 1 cm, et environ 1 degré. */
    private static final double MOVE_MIN = 0.0001;
    private static final float TURN_MIN = 0.99996f;

    /** Cible en plus du tutoriel : « cible » (monde x y z) et « nom », avec placeholders. */
    record Source(String id, String target, String name) {
    }

    private record Target(Location place, String name) {
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

    Fleche(SHGuide plugin) {
        this.plugin = plugin;
    }

    /** Cible de l'étape du tutoriel ; null l'enlève. */
    void setTarget(Player p, Location target, String name) {
        Target t = target == null || !plugin.settings().accueil().arrow().enabled() ? null
                : new Target(target, name == null ? "" : name);
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

    /** Relit les cibles de la section « fleches » ; la première lisible est gardée. */
    void readSources() {
        List<Source> sources = plugin.settings().accueil().arrow().enabled() ? plugin.settings().arrowSources() : List.of();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Target found = null;
            for (Source s : sources) {
                Location place = place(PlaceholderAPI.setPlaceholders(p, s.target()));
                if (place != null) {
                    found = new Target(place, PlaceholderAPI.setPlaceholders(p, s.name()));
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
            if (dist < cfg.arrivedDistance() || dist < cfg.minDistance() && a.item == null) {
                despawn(a);
                continue;
            }
            boolean fresh = a.item == null || !a.item.isValid() || !a.follow.ready();
            boolean standing = p.isOnGround() || p.isFlying() || p.isClimbing() || p.isInWater() || p.isGliding();
            a.follow.follow(pl.getX(), pl.getY(), pl.getZ(), standing, lead(p));
            a.follow.turn(target.place().getX(), target.place().getZ());
            place(pl.getWorld(), a.follow, cfg, fresh);
            draw(p, a, pl, target, dist, cfg);
        }
    }

    /** Ticks d'avance : le temps que le mouvement arrive au serveur et que la flèche revienne au client. */
    private static double lead(Player p) {
        return Math.min(5, GLIDE - 1 + p.getPing() / 50.0);
    }

    /** Un mur entre le joueur et la flèche la rapproche, une marche la relève. */
    private static void place(World world, Suivi f, AccueilConfig.Arrow cfg, boolean now) {
        Vector out = new Vector(Math.cos(f.heading), 0, Math.sin(f.heading));
        double low = free(world, f, cfg.height(), out, cfg.ahead());
        if (low >= cfg.ahead()) {
            f.place(cfg.ahead(), 0, now);
            return;
        }
        double high = free(world, f, cfg.height() + STEP_LIFT, out, cfg.ahead());
        if (high > low) {
            f.place(Math.max(WALL_MIN, high), STEP_LIFT, now);
        } else {
            f.place(Math.max(WALL_MIN, low), 0, now);
        }
    }

    private static double free(World world, Suivi f, double height, Vector out, double ahead) {
        RayTraceResult hit = world.rayTraceBlocks(new Location(world, f.x, f.y + height, f.z), out, ahead + WALL_GAP,
                FluidCollisionMode.NEVER, true);
        return hit == null ? ahead : Math.hypot(hit.getHitPosition().getX() - f.x, hit.getHitPosition().getZ() - f.z) - WALL_GAP;
    }

    private void draw(Player p, Arrow a, Location pl, Target target, double dist, AccueilConfig.Arrow cfg) {
        Suivi f = a.follow;
        double height = cfg.height() + f.lift;
        double ox = Math.cos(f.heading) * f.radius;
        double oz = Math.sin(f.heading) * f.radius;
        Location pos = new Location(pl.getWorld(), f.x + ox, f.y + height, f.z + oz);
        double flat = Math.max(0.01, Math.hypot(target.place().getX() - f.x, target.place().getZ() - f.z));
        Vector3f direction = new Vector3f((float) (Math.cos(f.heading) * flat),
                (float) (target.place().getY() - pos.getY()), (float) (Math.sin(f.heading) * flat));
        Vector3f toEyes = new Vector3f((float) -ox, (float) (EYES - height), (float) -oz);
        Quaternionf turn = Orientation.of(direction, toEyes, cfg.yawOffset());
        if (a.item == null || !a.item.isValid()) {
            spawn(p, a, pos, turn, cfg);
        } else {
            if (a.sent.distanceSquared(pos) > MOVE_MIN) {
                a.sent = pos;
                a.item.teleport(pos);
                a.label.teleport(pos.clone().add(0, cfg.labelAbove(), 0));
            }
            if (Math.abs(a.sentTurn.dot(turn)) < TURN_MIN) {
                a.sentTurn = turn;
                a.item.setInterpolationDelay(0);
                a.item.setTransformation(transformation(turn, cfg));
            }
        }
        String label = label(target, pl, dist);
        if (!label.equals(a.lastLabel)) {
            a.lastLabel = label;
            a.label.text(Messages.chat(label));
        }
    }

    private String label(Target target, Location pl, double dist) {
        double dy = target.place().getY() - pl.getY();
        String height = "";
        if (Math.abs(dy) >= HEIGHT_MIN && Math.abs(dy) / dist >= SLOPE_MIN) {
            height = plugin.messages().text(dy > 0 ? "accueil.fleche-haut" : "accueil.fleche-bas");
        }
        return plugin.messages().text("accueil.fleche", "nom", target.name(),
                "distance", Integer.toString((int) Math.round(dist)), "hauteur", height);
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
            e.setTeleportDuration(GLIDE);
            e.setInterpolationDuration(GLIDE);
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
            e.setTeleportDuration(GLIDE);
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
