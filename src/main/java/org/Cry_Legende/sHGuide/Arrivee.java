package org.Cry_Legende.sHGuide;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.List;

/**
 * Arrivée d'un nouveau joueur sur son île : à sa première entrée dans le monde des îles le jour de son arrivée, il
 * repart « hauteur » blocs plus haut, le regard vers son île, et redescend doucement sous un grand titre. Les commandes
 * de « commandes » suivent (dialogue d'accueil, sons…), chacune après ses ticks.
 */
final class Arrivee implements Listener {

    private final SHGuide plugin;
    /** Null sans packetevents : le titre de l'arrivée n'est alors pas protégé. */
    private final TitreSeul alone;

    Arrivee(SHGuide plugin) {
        this.plugin = plugin;
        this.alone = Bukkit.getPluginManager().isPluginEnabled("packetevents") ? new TitreSeul() : null;
    }

    void close() {
        if (alone != null) {
            alone.close();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (alone != null) {
            alone.release(e.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent e) {
        AccueilConfig c = plugin.settings().accueil();
        AccueilConfig.Arrival a = c.arrival();
        Player p = e.getPlayer();
        if (!c.enabled() || a == null || !p.getWorld().getName().equals(a.world())) {
            return;
        }
        Sessions.Session s = plugin.sessions().get(p.getUniqueId());
        if (s == null || s.state.firstDay == null || s.state.arrived) {
            return;
        }
        s.state.arrived = true;
        s.dirty = true;
        if (!plugin.today().toString().equals(s.state.firstDay)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> play(p, a), 10L);
    }

    /** Lance l'arrivée (aussi par /shguide accueil <joueur> arrivee pour la tester). */
    void play(Player p, AccueilConfig.Arrival a) {
        if (!p.isOnline() || !p.getWorld().getName().equals(a.world())) {
            return;
        }
        if (a.height() > 0) {
            Location home = p.getLocation().clone();
            Location up = home.clone().add(0, a.height(), 0);
            up.setPitch(a.pitch());
            p.teleport(up);
            p.setFallDistance(0);
            if (a.speed() > 0) {
                descend(p, home, up, a);
            } else if (a.fallSeconds() > 0) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, a.fallSeconds() * 20, 0,
                        false, false, false));
            }
        }
        if (!a.title().isEmpty() || !a.subtitle().isEmpty()) {
            showTitle(p, a);
        }
        if (!a.sound().isEmpty()) {
            p.playSound(p.getLocation(), a.sound(), 1f, 1f);
        }
        for (AccueilConfig.Timed t : a.commands()) {
            Runnable run = () -> {
                if (p.isOnline()) {
                    plugin.runCommands(p, List.of(t.command()));
                }
            };
            if (t.tick() == 0) {
                run.run();
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, run, t.tick());
            }
        }
    }

    /**
     * Descente « sur rail » : le joueur est assis sur un item display vide (invisible) qui descend de « vitesse-descente »
     * blocs par seconde jusqu'au point de départ ; le client interpole chaque déplacement, la caméra glisse sans à-coups.
     * Un appui sur Maj le fait descendre de lui-même (chute lente en filet).
     */
    private void descend(Player p, Location home, Location up, AccueilConfig.Arrival a) {
        ItemDisplay seat = up.getWorld().spawn(up, ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.setTeleportDuration(3);
        });
        if (!seat.addPassenger(p)) {
            plugin.getLogger().warning("Arrivée de " + p.getName() + " : montée refusée, chute lente à la place.");
        }
        double step = a.speed() / 10.0;
        long max = Math.max(40L, (long) Math.ceil(a.height() / a.speed() * 20) + 40);
        long[] elapsed = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            elapsed[0] += 2;
            boolean riding = p.isOnline() && seat.isValid() && seat.getPassengers().contains(p);
            Location at = seat.getLocation();
            if (!riding || at.getY() <= home.getY() + 0.05 || elapsed[0] >= max) {
                task.cancel();
                if (seat.isValid()) {
                    seat.eject();
                    seat.remove();
                }
                if (p.isOnline()) {
                    p.setFallDistance(0);
                    if (!riding) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0, false, false, false));
                    }
                }
                return;
            }
            Location next = at.clone();
            next.setY(Math.max(home.getY(), at.getY() - step));
            seat.teleport(next, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
        }, 10L, 2L);
    }

    /** Titre de l'arrivée, seul à l'écran jusqu'à la fin de la descente si « titre-seul ». */
    private void showTitle(Player p, AccueilConfig.Arrival a) {
        Component title = Messages.chat(Messages.replace(a.title(), "joueur", p.getName()));
        Component subtitle = Messages.chat(Messages.replace(a.subtitle(), "joueur", p.getName()));
        if (alone == null || !a.alone()) {
            p.showTitle(Title.title(title, subtitle,
                    Title.Times.times(ticks(a.fadeIn()), ticks(a.stay()), ticks(a.fadeOut()))));
            return;
        }
        alone.show(p, title, subtitle, a.fadeIn(), a.stay(), a.fadeOut(), a.keepTicks());
        Bukkit.getScheduler().runTaskLater(plugin, () -> alone.release(p.getUniqueId()), a.keepTicks());
    }

    private static Duration ticks(int t) {
        return Duration.ofMillis(Math.max(0, t) * 50L);
    }
}
