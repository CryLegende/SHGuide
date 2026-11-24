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
 * Flèche vers une cible : un item display devant le joueur, sous ses yeux, tourné vers la cible (haut et bas compris),
 * et la distance au-dessus. Les deux entités ne sont montrées qu'à ce joueur, ne sont jamais sauvegardées et glissent
 * d'une position à l'autre (interpolation du client), mises à jour à chaque tick seulement si le joueur a bougé.
 *
 * Une seule flèche par joueur : une cible de la section « fleches » passe avant l'étape du tutoriel.
 */
final class Fleche {

    /** Ticks d'avance donnés à la position (le client voit le joueur avant le serveur). */
    private static final double PREDICT = 2.0;
    /** Écart gardé avec un mur, et distance minimale aux yeux quand il est tout près. */
    private static final double WALL_GAP = 0.25;
    private static final double WALL_MIN = 0.6;
    /** Le sens de la hauteur s'affiche à partir de cet écart (blocs) et de cette pente (sinus de 20°). */
    private static final double HEIGHT_MIN = 6;
    private static final double SLOPE_MIN = 0.34;

    /** Cible en plus du tutoriel : « cible » (monde x y z) et « nom », avec placeholders. */
    record Source(String id, String target, String name) {
    }

    private record Target(Location place, String name) {
    }

    private static final class Arrow {
        Target tutorial;
        Target other;
        ItemDisplay item;
        TextDisplay label;
        Location last;
        float lastYaw = Float.NaN;
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
        Target before = a.target();
        if (other) {
            a.other = t;
        } else {
            a.tutorial = t;
        }
        Target now = a.target();
        if (now == null) {
            arrows.remove(p.getUniqueId());
            despawn(a);
        } else if (!now.equals(before)) {
            a.last = null;
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
            if (a.item != null && a.item.isValid() && a.last != null && a.last.getWorld() == pl.getWorld()
                    && a.last.distanceSquared(pl) < 0.0004 && Math.abs(a.lastYaw - pl.getYaw()) < 0.5f) {
                continue;
            }
            double vx = 0, vy = 0, vz = 0;
            if (a.last != null && a.last.getWorld() == pl.getWorld()) {
                vx = pl.getX() - a.last.getX();
                vy = pl.getY() - a.last.getY();
                vz = pl.getZ() - a.last.getZ();
                if (vx * vx + vy * vy + vz * vz > 4) {
                    vx = vy = vz = 0;
                }
            }
            a.last = pl.clone();
            a.lastYaw = pl.getYaw();
            Vector offset = offset(p, pl, cfg);
            Location pos = new Location(pl.getWorld(), pl.getX() + offset.getX() + vx * PREDICT,
                    pl.getY() + p.getEyeHeight() + offset.getY() + vy * PREDICT, pl.getZ() + offset.getZ() + vz * PREDICT);
            Vector3f direction = new Vector3f((float) (target.place().getX() - pos.getX()),
                    (float) (target.place().getY() - pos.getY()), (float) (target.place().getZ() - pos.getZ()));
            if (direction.lengthSquared() < 1e-4f) {
                direction.set(0, 0, 1);
            }
            Vector3f toEyes = new Vector3f((float) -offset.getX(), (float) -offset.getY(), (float) -offset.getZ());
            Quaternionf turn = Orientation.of(direction, toEyes, cfg.yawOffset());
            if (a.item == null || !a.item.isValid()) {
                spawn(p, a, pos, turn, cfg);
            } else {
                a.item.teleport(pos);
                a.item.setInterpolationDelay(0);
                a.item.setTransformation(transformation(turn, cfg));
                a.label.teleport(pos.clone().add(0, cfg.labelAbove(), 0));
            }
            String label = label(target, pl, dist);
            if (!label.equals(a.lastLabel)) {
                a.lastLabel = label;
                a.label.text(Messages.chat(label));
            }
        }
    }

    /** Place de la flèche par rapport aux yeux : devant et plus bas, ramenée vers le joueur quand un mur la cacherait. */
    private static Vector offset(Player p, Location pl, AccueilConfig.Arrow cfg) {
        double yaw = Math.toRadians(pl.getYaw());
        Vector offset = new Vector(-Math.sin(yaw) * cfg.ahead(), -cfg.below(), Math.cos(yaw) * cfg.ahead());
        double length = offset.length();
        if (length < WALL_MIN) {
            return offset;
        }
        Location eyes = pl.clone().add(0, p.getEyeHeight(), 0);
        RayTraceResult hit = pl.getWorld().rayTraceBlocks(eyes, offset, length + WALL_GAP, FluidCollisionMode.NEVER, true);
        if (hit == null) {
            return offset;
        }
        double free = hit.getHitPosition().distance(eyes.toVector()) - WALL_GAP;
        return offset.multiply(Math.max(WALL_MIN, Math.min(length, free)) / length);
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
        Location last = a.last;
        float lastYaw = a.lastYaw;
        despawn(a);
        a.last = last;
        a.lastYaw = lastYaw;
        Material mat = Material.matchMaterial(cfg.item());
        ItemStack stack = new ItemStack(mat == null || !mat.isItem() ? Material.SPECTRAL_ARROW : mat);
        a.item = pos.getWorld().spawn(pos, ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.setVisibleByDefault(false);
            e.setItemStack(stack);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setBillboard(Display.Billboard.FIXED);
            e.setTransformation(transformation(turn, cfg));
            e.setTeleportDuration(2);
            e.setInterpolationDuration(2);
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
            e.setTeleportDuration(2);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(cfg.labelScale(), cfg.labelScale(), cfg.labelScale()), new Quaternionf()));
        });
        p.showEntity(plugin, a.item);
        p.showEntity(plugin, a.label);
    }

    private static void despawn(Arrow a) {
        if (a.item != null) {
            a.item.remove();
            a.item = null;
        }
        if (a.label != null) {
            a.label.remove();
            a.label = null;
        }
        a.last = null;
        a.lastYaw = Float.NaN;
        a.lastLabel = "";
    }
}
