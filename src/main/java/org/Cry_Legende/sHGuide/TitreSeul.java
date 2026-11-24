package org.Cry_Legende.sHGuide;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetTitleSubtitle;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetTitleText;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetTitleTimes;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Titre gardé à l'écran : les titres des autres plugins n'atteignent pas le joueur pendant sa durée. Le nôtre part en
 * paquets silencieux, que cet écouteur ne voit pas. Il n'est branché sur packetevents que tant qu'un titre est gardé.
 */
final class TitreSeul extends PacketListenerAbstract {

    /** Joueur → fin de la garde, en millisecondes. */
    private final Map<UUID, Long> kept = new ConcurrentHashMap<>();
    private PacketListenerCommon registered;

    TitreSeul() {
        super(PacketListenerPriority.LOWEST);
    }

    /** Montre le titre et le garde seul à l'écran pendant « keepTicks ». */
    void show(Player p, Component title, Component subtitle, int in, int stay, int out, int keepTicks) {
        kept.put(p.getUniqueId(), System.currentTimeMillis() + keepTicks * 50L);
        if (registered == null) {
            registered = PacketEvents.getAPI().getEventManager().registerListener(this);
        }
        var players = PacketEvents.getAPI().getPlayerManager();
        players.sendPacketSilently(p, new WrapperPlayServerSetTitleTimes(in, stay, out));
        players.sendPacketSilently(p, new WrapperPlayServerSetTitleSubtitle(subtitle));
        players.sendPacketSilently(p, new WrapperPlayServerSetTitleText(title));
    }

    /** Fin de la garde (durée passée, déconnexion). */
    void release(UUID id) {
        kept.remove(id);
        if (kept.isEmpty()) {
            close();
        }
    }

    void close() {
        kept.clear();
        if (registered != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(registered);
            registered = null;
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent e) {
        PacketTypeCommon type = e.getPacketType();
        if (type != PacketType.Play.Server.SET_TITLE_TEXT && type != PacketType.Play.Server.SET_TITLE_SUBTITLE
                && type != PacketType.Play.Server.SET_TITLE_TIMES && type != PacketType.Play.Server.CLEAR_TITLES) {
            return;
        }
        UUID id = e.getUser() == null ? null : e.getUser().getUUID();
        Long until = id == null ? null : kept.get(id);
        if (until != null && System.currentTimeMillis() < until) {
            e.setCancelled(true);
        }
    }
}
