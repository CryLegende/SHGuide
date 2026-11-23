package org.Cry_Legende.sHGuide;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Icônes des menus, même écriture que DeluxeMenus : un matériau ({@code CHEST}), une tête de HeadDatabase
 * ({@code hdb-81626}, si le plugin est là) ou une tête texturée ({@code basehead-<valeur base64>}).
 * Une icône inconnue devient du papier.
 */
final class Icons {

    private static final Map<String, ItemStack> CACHE = new ConcurrentHashMap<>();

    private Icons() {
    }

    static ItemStack make(String spec) {
        String s = spec == null ? "" : spec.trim();
        ItemStack cached = CACHE.get(s);
        if (cached != null) {
            return cached.clone();
        }
        ItemStack built = build(s);
        if (built != null) {
            CACHE.put(s, built.clone());
            return built;
        }
        // HeadDatabase pas encore prêt : tête simple, redemandée à la prochaine ouverture
        return new ItemStack(Material.PLAYER_HEAD);
    }

    static void clear() {
        CACHE.clear();
    }

    /** null = tête HeadDatabase pas disponible pour l'instant. */
    private static ItemStack build(String s) {
        String lower = s.toLowerCase();
        if (lower.startsWith("hdb-")) {
            return headDatabase(s.substring(4));
        }
        if (lower.startsWith("basehead-")) {
            return textured(s.substring("basehead-".length()));
        }
        Material mat = Material.matchMaterial(s);
        return new ItemStack(mat == null || !mat.isItem() ? Material.PAPER : mat);
    }

    /** HeadDatabase lu par réflexion : pas de dépendance à la compilation, rien si le plugin manque. */
    private static ItemStack headDatabase(String id) {
        if (Bukkit.getPluginManager().getPlugin("HeadDatabase") == null) {
            return null;
        }
        try {
            Class<?> api = Class.forName("me.arcaniax.hdb.api.HeadDatabaseAPI", true,
                    Bukkit.getPluginManager().getPlugin("HeadDatabase").getClass().getClassLoader());
            Object instance = api.getConstructor().newInstance();
            Object item = api.getMethod("getItemHead", String.class).invoke(instance, id);
            return item instanceof ItemStack it ? it.clone() : null;
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static ItemStack textured(String value) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (value.isEmpty() || !(head.getItemMeta() instanceof SkullMeta meta)) {
            return head;
        }
        PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)));
        profile.setProperty(new ProfileProperty("textures", value));
        meta.setPlayerProfile(profile);
        head.setItemMeta(meta);
        return head;
    }
}
