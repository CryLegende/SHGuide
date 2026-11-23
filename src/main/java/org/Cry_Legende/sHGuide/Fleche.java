package org.Cry_Legende.sHGuide;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Flèche vers l'étape du tutoriel : un item display à plat, devant le joueur sous ses yeux, tourné vers la cible, et
 * la distance au-dessus. Les deux entités ne sont montrées qu'à ce joueur, ne sont jamais sauvegardées et glissent
 * d'une position à l'autre (interpolation du client), mises à jour à chaque tick seulement si le joueur a bougé.
 */
final class Fleche {

    /** Ticks d'avance donnés à la position (le client voit le joueur avant le serveur). */
    private static final double PREDICT = 2.0;

    private static final class Arrow {
        Location target;
        String name = "";
        ItemDisplay item;
        TextDisplay label;
        Location last;
        float lastYaw = Float.NaN;
        int lastMeters = -1;
    }

    private final SHGuide plugin;
    private final Map<UUID, Arrow> arrows = new HashMap<>();

    Fleche(SHGuide plugin) {
        this.plugin = plugin;
    }

    /** Nouvelle cible du joueur ; null enlève la flèche. */
    void setTarget(Player p, Location target, String name) {
        AccueilConfig.Arrow cfg = plugin.settings().accueil().arrow();
        if (target == null || !cfg.enabled()) {
            Arrow a = arrows.remove(p.getUniqueId());
            if (a != null) {
                despawn(a);
            }
            return;
        }
        Arrow a = arrows.computeIfAbsent(p.getUniqueId(), k -> new Arrow());
        a.target = target;
        a.name = name == null ? "" : name;
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
            Location pl = p.getLocation();
            if (a.target.getWorld() != pl.getWorld() || p.isDead() || p.isInsideVehicle()) {
                despawn(a);
                continue;
            }
            double dist = pl.distance(a.target);
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
            double yaw = Math.toRadians(pl.getYaw());
            double fx = -Math.sin(yaw);
            double fz = Math.cos(yaw);
            Location pos = pl.clone().add(fx * cfg.ahead() + vx * PREDICT,
                    p.getEyeHeight() - cfg.below() + vy * PREDICT, fz * cfg.ahead() + vz * PREDICT);
            double dx = a.target.getX() - pos.getX();
            double dz = a.target.getZ() - pos.getZ();
            pos.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)) + cfg.yawOffset());
            pos.setPitch(0);
            if (a.item == null || !a.item.isValid()) {
                spawn(p, a, pos, cfg);
            } else {
                a.item.teleport(pos);
                a.label.teleport(pos.clone().add(0, cfg.labelAbove(), 0));
            }
            int meters = (int) Math.round(dist);
            if (meters != a.lastMeters) {
                a.lastMeters = meters;
                a.label.text(Messages.chat(plugin.messages().text("accueil.fleche",
                        "nom", a.name, "distance", Integer.toString(meters))));
            }
        }
    }

    private void spawn(Player p, Arrow a, Location pos, AccueilConfig.Arrow cfg) {
        despawn(a);
        Material mat = Material.matchMaterial(cfg.item());
        ItemStack stack = new ItemStack(mat == null || !mat.isItem() ? Material.SPECTRAL_ARROW : mat);
        // Le dessin de la flèche monte en diagonale : un quart de tour en Z la met droite, puis elle est couchée en X
        // pour pointer vers l'avant de l'entité (son yaw).
        Quaternionf flat = new Quaternionf().rotateX((float) Math.toRadians(90)).rotateZ((float) Math.toRadians(45));
        a.item = pos.getWorld().spawn(pos, ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.setVisibleByDefault(false);
            e.setItemStack(stack);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setBillboard(Display.Billboard.FIXED);
            e.setTransformation(new Transformation(new Vector3f(), flat,
                    new Vector3f(cfg.scale(), cfg.scale(), cfg.scale()), new Quaternionf()));
            e.setTeleportDuration(2);
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
        a.lastMeters = -1;
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
    }
}
