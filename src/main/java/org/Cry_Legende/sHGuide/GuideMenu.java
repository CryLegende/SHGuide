package org.Cry_Legende.sHGuide;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Menu de Jake (45 cases) et sous-menu « Comment faire… ». */
final class GuideMenu {

    /** Colonnes 2 à 6 des rangées 2 et 3 : les colonnes 1 et 7 portent l'accent. */
    private static final int[] HELP_CELLS = {20, 21, 22, 23, 24, 29, 30, 31, 32, 33};
    private static final int HELP_BACK = 36;

    private GuideMenu() {
    }

    private abstract static class Holder implements InventoryHolder {
        final Map<Integer, Runnable> actions = new HashMap<>();
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    static final class Main extends Holder {
    }

    static final class Help extends Holder {
    }

    static void open(SHGuide plugin, Player p) {
        Programme.Tick t = plugin.sessions().refresh(p);
        Sessions.Session s = plugin.sessions().get(p.getUniqueId());
        if (t == null || s == null) {
            plugin.messages().send(p, "chargement");
            return;
        }
        Settings cfg = plugin.settings();
        Messages m = plugin.messages();
        LocalDate today = plugin.today();
        Main holder = new Main();
        holder.inv = Bukkit.createInventory(holder, 45, Messages.item(m.text("menu.titre")));
        frame(holder.inv, cfg);

        long left = t.tasks().stream().filter(v -> !v.done()).count();
        int serie = Streak.display(s.state, today);
        String phrase;
        if (!s.state.pending.isEmpty()) {
            phrase = m.text("menu.jake.phrase-prete", "joueur", p.getName());
        } else if (left == 0) {
            phrase = m.text("menu.jake.phrase-fini", "joueur", p.getName());
        } else if (left == t.tasks().size()) {
            phrase = m.text("menu.jake.phrase-debut", "joueur", p.getName());
        } else {
            phrase = m.text("menu.jake.phrase-encours", "joueur", p.getName(), "reste", Long.toString(left));
        }
        ItemStack jake = item(icon(cfg, "jake", "PLAYER_HEAD"), m.text("menu.jake.nom"),
                List.of(phrase, "", m.text("menu.jake.serie", "serie", Integer.toString(serie))));
        if (jake.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(p);
            jake.setItemMeta(skull);
        }
        holder.inv.setItem(slot(cfg, "jake"), jake);

        Objectives.Choice c = s.objective;
        String objText = c == null ? m.text("objectif.aucun") : c.text();
        List<String> objLore = new ArrayList<>();
        objLore.add(m.text("menu.objectif.ligne", "texte", objText));
        boolean objClick = c != null && !c.click().isEmpty();
        if (c != null) {
            objLore.addAll(c.lore());
        }
        if (objClick) {
            objLore.add("");
            objLore.add(m.text("menu.objectif.clic"));
        }
        holder.inv.setItem(slot(cfg, "objectif"), item(c == null ? "COMPASS" : c.icon(), m.text("menu.objectif.nom"), objLore));
        if (objClick) {
            String click = c.click();
            holder.actions.put(slot(cfg, "objectif"), () -> {
                if (click.equalsIgnoreCase("guide")) {
                    open(plugin, p);
                } else {
                    p.closeInventory();
                    plugin.runCommands(p, List.of(click));
                }
            });
        }

        for (int i = 0; i < t.tasks().size() && i < cfg.taskSlots().size(); i++) {
            Programme.TaskView v = t.tasks().get(i);
            List<String> lore = new ArrayList<>();
            lore.add(v.done() ? m.text("menu.tache.fait")
                    : m.text("menu.tache.progression", "actuel", Num.format(v.current()), "objectif", Num.format(v.goal())));
            boolean click = !v.done() && !v.def().click().isEmpty();
            if (click) {
                lore.add("");
                lore.add(m.text("menu.tache.clic"));
            }
            int slot = cfg.taskSlots().get(i);
            holder.inv.setItem(slot, item(v.done() ? icon(cfg, "fait", "LIME_DYE") : v.def().icon(),
                    m.text("menu.tache.nom", "texte", v.text()), lore));
            if (click) {
                String command = v.def().click();
                holder.actions.put(slot, () -> {
                    p.closeInventory();
                    plugin.runCommands(p, List.of(command));
                });
            }
        }

        boolean ready = !s.state.pending.isEmpty();
        String key = ready ? "prete" : s.state.completed ? "prise" : "fermee";
        ItemStack reward = item(icon(cfg, "recompense-" + key, "CHEST"), m.text("menu.recompense.nom-" + key),
                m.lines("menu.recompense.lore-" + key));
        if (ready) {
            ItemMeta meta = reward.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            reward.setItemMeta(meta);
            holder.actions.put(slot(cfg, "recompense"), () -> {
                claim(plugin, p);
                open(plugin, p);
            });
        }
        holder.inv.setItem(slot(cfg, "recompense"), reward);

        holder.inv.setItem(slot(cfg, "aide"), item(icon(cfg, "aide", "BOOK"), m.text("menu.aide.nom"), m.lines("menu.aide.lore")));
        holder.actions.put(slot(cfg, "aide"), () -> openHelp(plugin, p));
        holder.inv.setItem(slot(cfg, "ile"), item(icon(cfg, "ile", "GRASS_BLOCK"), m.text("menu.ile.nom"), m.lines("menu.ile.lore")));
        holder.actions.put(slot(cfg, "ile"), () -> {
            p.closeInventory();
            plugin.runCommands(p, List.of(cfg.islandCommand()));
        });
        holder.inv.setItem(slot(cfg, "reglages"), item(icon(cfg, "reglages", "NAME_TAG"), m.text("menu.reglages.nom"),
                m.lines("menu.reglages.lore")));
        holder.actions.put(slot(cfg, "reglages"), () -> {
            p.closeInventory();
            plugin.runCommands(p, List.of(cfg.settingsCommand()));
        });

        int position = serie == 0 ? 0 : ((serie - 1) % cfg.cycle()) + 1;
        int nextStep = cfg.stepDays().stream().filter(d -> d > position).min(Integer::compare).orElse(cfg.cycle());
        List<String> serieLore = new ArrayList<>();
        serieLore.add(m.text("menu.serie.prochain", "reste", Integer.toString(Math.max(1, nextStep - position))));
        serieLore.add(m.text("menu.serie.record", "record", Integer.toString(s.state.best)));
        serieLore.add(Streak.jokerAvailable(s.state, today) ? m.text("menu.serie.joker-oui") : m.text("menu.serie.joker-non"));
        holder.inv.setItem(slot(cfg, "serie"), item(icon(cfg, "serie", "BLAZE_POWDER"),
                m.text("menu.serie.nom", "serie", Integer.toString(serie)), serieLore));

        if (!today.toString().equals(s.state.alertSeen)) {
            s.state.alertSeen = today.toString();
            plugin.sessions().markDirty(p);
        }
        p.openInventory(holder.inv);
        plugin.sound(p, "ouverture");
    }

    static void openHelp(SHGuide plugin, Player p) {
        Settings cfg = plugin.settings();
        Messages m = plugin.messages();
        Help holder = new Help();
        holder.inv = Bukkit.createInventory(holder, 45, Messages.item(m.text("menu.titre-aide")));
        frame(holder.inv, cfg);
        for (int i = 0; i < cfg.help().size() && i < HELP_CELLS.length; i++) {
            Settings.HelpEntry h = cfg.help().get(i);
            holder.inv.setItem(HELP_CELLS[i], item(h.icon(), h.name(), h.lore()));
            if (!h.commands().isEmpty()) {
                holder.actions.put(HELP_CELLS[i], () -> {
                    if (h.close()) {
                        p.closeInventory();
                    }
                    plugin.runCommands(p, h.commands());
                });
            }
        }
        holder.inv.setItem(HELP_BACK, item(icon(cfg, "retour", "ARROW"), m.text("menu.retour.nom"), List.of()));
        holder.actions.put(HELP_BACK, () -> open(plugin, p));
        p.openInventory(holder.inv);
    }

    /** Donne toutes les récompenses en attente : celle du jour et les paliers de série atteints. */
    static void claim(SHGuide plugin, Player p) {
        Sessions.Session s = plugin.sessions().get(p.getUniqueId());
        if (s == null || s.state.pending.isEmpty()) {
            plugin.messages().send(p, "rien-a-prendre");
            return;
        }
        List<String> pending = new ArrayList<>(s.state.pending);
        s.state.pending.clear();
        plugin.sessions().markDirty(p);
        for (String id : pending) {
            if (id.equals("jour")) {
                plugin.runCommands(p, plugin.settings().dailyReward());
            } else if (id.startsWith("palier:")) {
                int day = Integer.parseInt(id.substring("palier:".length()));
                plugin.runCommands(p, plugin.settings().steps().getOrDefault(day, List.of()));
                plugin.messages().send(p, "recompense-palier", "jour", Integer.toString(day));
            }
        }
        plugin.messages().send(p, "recompense", "serie", Integer.toString(Streak.display(s.state, plugin.today())));
        plugin.sound(p, "recompense");
    }

    private static int slot(Settings cfg, String key) {
        return cfg.slots().getOrDefault(key, 0);
    }

    private static String icon(Settings cfg, String key, String fallback) {
        return cfg.icons().getOrDefault(key, fallback);
    }

    private static void frame(Inventory inv, Settings cfg) {
        ItemStack ring = item(icon(cfg, "contour", "BLACK_STAINED_GLASS_PANE"), " ", List.of());
        ItemStack accent = item(icon(cfg, "accent", "CYAN_STAINED_GLASS_PANE"), " ", List.of());
        for (int i = 0; i < 45; i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == 4 || col == 0 || col == 8) {
                inv.setItem(i, ring);
            } else if (col == 1 || col == 7) {
                inv.setItem(i, accent);
            }
        }
    }

    private static ItemStack item(String material, String name, List<String> lore) {
        Material mat = Material.matchMaterial(material);
        ItemStack it = new ItemStack(mat == null || !mat.isItem() ? Material.PAPER : mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Messages.item(name));
        meta.lore(lore.stream().map(Messages::item).toList());
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    static final class Listener implements org.bukkit.event.Listener {

        private final SHGuide plugin;

        Listener(SHGuide plugin) {
            this.plugin = plugin;
        }

        @EventHandler
        public void onClick(InventoryClickEvent e) {
            if (!(e.getInventory().getHolder() instanceof Holder h)) {
                return;
            }
            e.setCancelled(true);
            if (e.getClickedInventory() != e.getInventory() || !(e.getWhoClicked() instanceof Player p)) {
                return;
            }
            Runnable action = h.actions.get(e.getRawSlot());
            if (action != null) {
                plugin.sound(p, "clic");
                plugin.sync(action);
            }
        }

        @EventHandler
        public void onDrag(InventoryDragEvent e) {
            if (e.getInventory().getHolder() instanceof Holder) {
                e.setCancelled(true);
            }
        }
    }
}
