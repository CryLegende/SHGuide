package org.Cry_Legende.sHGuide;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Paquets de la flèche : un « item display » et un « text display » qui n'existent que chez le joueur qui les reçoit. Le
 * serveur n'en garde aucune entité. Seule classe qui touche à packetevents.
 */
final class FlechePaquets {

    // Métadonnées (26.3) : drapeaux de l'entité, puis celles d'un display, d'un item display et d'un text display
    private static final int FLAGS = 0;
    private static final byte GLOWING = 0x40;
    private static final int DELAY = 8;
    private static final int TURN_TICKS = 9;
    private static final int MOVE_TICKS = 10;
    private static final int SCALE = 12;
    private static final int ROTATION = 13;
    private static final int BILLBOARD = 15;
    private static final byte CENTER = 3;
    private static final int BRIGHTNESS = 16;
    private static final int FULL_BRIGHT = 15 << 4 | 15 << 20;
    private static final int GLOW_COLOR = 22;
    private static final int ITEM = 23;
    private static final int TEXT = 23;
    private static final int BACKGROUND = 25;
    private static final int STYLE = 27;
    private static final byte SHADOW = 0x01;

    private FlechePaquets() {
    }

    static int newEntityId() {
        return SpigotReflectionUtil.generateEntityId();
    }

    static void spawnItem(Player p, int id, double x, double y, double z, Material material, Quaternionf turn, float scale, int glow, int ticks) {
        spawn(p, id, false, x, y, z);
        List<EntityData<?>> data = new ArrayList<>(8);
        if (glow >= 0) {
            data.add(new EntityData<>(FLAGS, EntityDataTypes.BYTE, GLOWING));
            data.add(new EntityData<>(GLOW_COLOR, EntityDataTypes.INT, glow));
        }
        data.add(new EntityData<>(TURN_TICKS, EntityDataTypes.INT, ticks));
        data.add(new EntityData<>(MOVE_TICKS, EntityDataTypes.INT, ticks));
        data.add(new EntityData<>(SCALE, EntityDataTypes.VECTOR3F, new Vector3f(scale, scale, scale)));
        data.add(new EntityData<>(ROTATION, EntityDataTypes.QUATERNION, quaternion(turn)));
        data.add(new EntityData<>(BRIGHTNESS, EntityDataTypes.INT, FULL_BRIGHT));
        data.add(new EntityData<>(ITEM, EntityDataTypes.ITEMSTACK, SpigotConversionUtil.fromBukkitItemStack(new ItemStack(material))));
        send(p, new WrapperPlayServerEntityMetadata(id, data));
    }

    static void spawnLabel(Player p, int id, double x, double y, double z, Component text, float scale, int ticks) {
        spawn(p, id, true, x, y, z);
        send(p, new WrapperPlayServerEntityMetadata(id, List.of(
                new EntityData<>(MOVE_TICKS, EntityDataTypes.INT, ticks),
                new EntityData<>(SCALE, EntityDataTypes.VECTOR3F, new Vector3f(scale, scale, scale)),
                new EntityData<>(BILLBOARD, EntityDataTypes.BYTE, CENTER),
                new EntityData<>(BRIGHTNESS, EntityDataTypes.INT, FULL_BRIGHT),
                new EntityData<>(TEXT, EntityDataTypes.ADV_COMPONENT, text),
                new EntityData<>(BACKGROUND, EntityDataTypes.INT, 0),
                new EntityData<>(STYLE, EntityDataTypes.BYTE, SHADOW))));
    }

    private static void spawn(Player p, int id, boolean label, double x, double y, double z) {
        send(p, new WrapperPlayServerSpawnEntity(id, Optional.of(UUID.randomUUID()), label ? EntityTypes.TEXT_DISPLAY : EntityTypes.ITEM_DISPLAY,
                new Vector3d(x, y, z), 0, 0, 0, 0, Optional.empty()));
    }

    static void move(Player p, int id, double x, double y, double z) {
        send(p, new WrapperPlayServerEntityTeleport(id, new Vector3d(x, y, z), 0, 0, false));
    }

    static void turn(Player p, int id, Quaternionf turn) {
        send(p, new WrapperPlayServerEntityMetadata(id, List.of(
                new EntityData<>(DELAY, EntityDataTypes.INT, 0),
                new EntityData<>(ROTATION, EntityDataTypes.QUATERNION, quaternion(turn)))));
    }

    static void text(Player p, int id, Component text) {
        send(p, new WrapperPlayServerEntityMetadata(id, List.of(new EntityData<>(TEXT, EntityDataTypes.ADV_COMPONENT, text))));
    }

    /** Durée des glissements : plus longue quand les positions sont envoyées moins souvent. */
    static void glide(Player p, int item, int label, int ticks) {
        send(p, new WrapperPlayServerEntityMetadata(item, List.of(
                new EntityData<>(TURN_TICKS, EntityDataTypes.INT, ticks),
                new EntityData<>(MOVE_TICKS, EntityDataTypes.INT, ticks))));
        send(p, new WrapperPlayServerEntityMetadata(label, List.of(new EntityData<>(MOVE_TICKS, EntityDataTypes.INT, ticks))));
    }

    static void destroy(Player p, int... ids) {
        send(p, new WrapperPlayServerDestroyEntities(ids));
    }

    private static Quaternion4f quaternion(Quaternionf q) {
        return new Quaternion4f(q.x, q.y, q.z, q.w);
    }

    private static void send(Player p, PacketWrapper<?> packet) {
        try {
            PacketEvents.getAPI().getPlayerManager().sendPacketSilently(p, packet);
        } catch (RuntimeException ignored) {
            // joueur en cours de déconnexion
        }
    }
}
