package org.Cry_Legende.sHGuide;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Écran de Jake d'où vient un joueur : gardé quand un bouton de Jake lance une commande, il sert à « /guide retour » (le
 * bouton Retour du menu que la commande a ouvert). Le Retour des menus plus loin dans la suite ne change pas. Oublié dès que
 * le joueur ferme tout ou revient chez Jake.
 */
final class Origine implements Listener {

    /** Écran de départ, menu ouvert par la commande (null tant qu'il n'est pas là) et dernier menu ouvert, par leur titre. */
    record Depart(String screen, int page, Component first, Component current) {

        Depart opened(Component title) {
            return new Depart(screen, page, first == null ? title : first, title);
        }

        /** Vrai tant que le joueur est dans le menu que Jake a ouvert. */
        boolean inFirst() {
            return first != null && first.equals(current);
        }
    }

    private static final long CLOSE_CHECK_TICKS = 5;

    private final SHGuide plugin;
    private final Map<UUID, Depart> from = new ConcurrentHashMap<>();

    Origine(SHGuide plugin) {
        this.plugin = plugin;
    }

    void set(Player p, String screen, int page) {
        from.put(p.getUniqueId(), new Depart(screen, page, null, null));
    }

    /** L'écran d'où vient le joueur s'il est dans le menu que Jake a ouvert, sinon null. */
    Depart back(Player p) {
        Depart d = from.get(p.getUniqueId());
        return d != null && d.inFirst() ? d : null;
    }

    void clear(Player p) {
        from.remove(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (GuideMenu.isOurs(e.getInventory())) {
            from.remove(id);
            return;
        }
        from.computeIfPresent(id, (k, d) -> d.opened(e.getView().title()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p) || !from.containsKey(p.getUniqueId())) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            InventoryType open = p.getOpenInventory().getType();
            if (open == InventoryType.CRAFTING || open == InventoryType.CREATIVE) {
                from.remove(p.getUniqueId());
            }
        }, CLOSE_CHECK_TICKS);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        from.remove(e.getPlayer().getUniqueId());
    }
}
