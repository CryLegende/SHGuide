package org.Cry_Legende.sHGuide;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

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
        int total = t.tasks().size();
        int serie = Streak.display(s.state, today);
        String phraseKey = !s.state.pending.isEmpty() ? "phrase-prete"
                : left == 0 ? "phrase-fini" : left == total ? "phrase-debut" : "phrase-encours";
        String phrase = m.text("menu.jake." + phraseKey, "joueur", p.getName(), "reste", Long.toString(left));
        holder.inv.setItem(slot(cfg, "jake"), item(icon(cfg, "jake", "PLAYER_HEAD"), m.text("menu.jake.nom"),
                m.lore("menu.jake.lore", Map.of(), "phrase", phrase, "serie", Integer.toString(serie),
                        "total", Integer.toString(total), "joueur", p.getName())));

        Objectives.Choice c = s.objective;
        String objText = c == null ? m.text("objectif.aucun") : c.text();
        boolean objClick = c != null && !c.click().isEmpty();
        Map<String, List<String>> objBlocks = new HashMap<>();
        objBlocks.put("description", c == null ? List.of() : c.lore());
        objBlocks.put("clic", objClick ? m.block("menu.objectif.clic") : List.of());
        holder.inv.setItem(slot(cfg, "objectif"), item(c == null ? icon(cfg, "objectif", "COMPASS") : c.icon(),
                m.text("menu.objectif.nom"), m.lore("menu.objectif.lore", objBlocks, "texte", objText)));
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

        String doneIcon = icon(cfg, "fait", "");
        for (int i = 0; i < total && i < cfg.taskSlots().size(); i++) {
            Programme.TaskView v = t.tasks().get(i);
            boolean click = !v.done() && !v.def().click().isEmpty();
            Map<String, List<String>> blocks = new HashMap<>();
            blocks.put("description", v.def().description());
            blocks.put("clic", click ? m.block("menu.tache.clic") : m.block("menu.tache.sans-clic"));
            String[] repl = {"texte", v.text(), "actuel", Num.format(Math.min(v.current(), v.goal())),
                    "objectif", Num.format(v.goal()), "barre", bar(m, v.current(), v.goal()),
                    "numero", Integer.toString(i + 1), "total", Integer.toString(total)};
            String state = v.done() ? "-fait" : "";
            ItemStack it = item(v.done() && !doneIcon.isEmpty() ? doneIcon : v.def().icon(),
                    m.text("menu.tache.nom" + state, repl), m.lore("menu.tache.lore" + state, blocks, repl));
            if (v.done() && doneIcon.isEmpty()) {
                glint(it);
            }
            int slot = cfg.taskSlots().get(i);
            holder.inv.setItem(slot, it);
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
        String[] rewardRepl = {"faites", Long.toString(total - left), "total", Integer.toString(total)};
        Settings.DailyVariant todayGain = ready ? s.state.pending.stream().filter(id -> id.startsWith("jour"))
                .findFirst().map(id -> pendingVariant(plugin, id)).orElse(null) : cfg.variantFor(today);
        Settings.DailyVariant tomorrowGain = cfg.variantFor(today.plusDays(1));
        Map<String, List<String>> rewardBlocks = new HashMap<>();
        rewardBlocks.put("gain", todayGain == null ? List.of() : m.block("menu.recompense.gain", "gain", todayGain.text()));
        rewardBlocks.put("demain", tomorrowGain == null ? List.of()
                : m.block("menu.recompense.demain", "gain", tomorrowGain.text()));
        ItemStack reward = item(icon(cfg, "recompense-" + key, "CHEST"), m.text("menu.recompense.nom-" + key, rewardRepl),
                m.lore("menu.recompense.lore-" + key, rewardBlocks, rewardRepl));
        if (ready) {
            glint(reward);
            holder.actions.put(slot(cfg, "recompense"), () -> {
                claim(plugin, p);
                open(plugin, p);
            });
        }
        holder.inv.setItem(slot(cfg, "recompense"), reward);

        holder.inv.setItem(slot(cfg, "aide"), item(icon(cfg, "aide", "BOOK"), m.text("menu.aide.nom"),
                m.lore("menu.aide.lore", Map.of())));
        holder.actions.put(slot(cfg, "aide"), () -> openHelp(plugin, p));
        holder.inv.setItem(slot(cfg, "ile"), item(icon(cfg, "ile", "GRASS_BLOCK"), m.text("menu.ile.nom"),
                m.lore("menu.ile.lore", Map.of())));
        holder.actions.put(slot(cfg, "ile"), () -> {
            p.closeInventory();
            plugin.runCommands(p, List.of(cfg.islandCommand()));
        });
        holder.inv.setItem(slot(cfg, "reglages"), item(icon(cfg, "reglages", "COMPARATOR"), m.text("menu.reglages.nom"),
                m.lore("menu.reglages.lore", Map.of())));
        holder.actions.put(slot(cfg, "reglages"), () -> {
            p.closeInventory();
            plugin.runCommands(p, List.of(cfg.settingsCommand()));
        });

        int position = serie == 0 ? 0 : ((serie - 1) % cfg.cycle()) + 1;
        int nextStep = cfg.stepDays().stream().filter(d -> d > position).min(Integer::compare).orElse(cfg.cycle());
        Map<String, List<String>> serieBlocks = new HashMap<>();
        serieBlocks.put("joker", Streak.jokerAvailable(s.state, today)
                ? m.block("menu.serie.joker-oui") : m.block("menu.serie.joker-non"));
        String gain = m.text("menu.serie.gains." + nextStep);
        serieBlocks.put("gain", gain.isEmpty() ? List.of() : m.block("menu.serie.gain", "gain", gain));
        holder.inv.setItem(slot(cfg, "serie"), item(icon(cfg, "serie", "CAMPFIRE"),
                m.text("menu.serie.nom", "serie", Integer.toString(serie)),
                m.lore("menu.serie.lore", serieBlocks, "serie", Integer.toString(serie),
                        "reste", Integer.toString(Math.max(1, nextStep - position)), "palier", Integer.toString(nextStep),
                        "record", Integer.toString(s.state.best))));

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
        holder.inv.setItem(HELP_BACK, item(icon(cfg, "retour", "ARROW"), m.text("menu.retour.nom"),
                m.lore("menu.retour.lore", Map.of())));
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
        List<String> gains = new ArrayList<>();
        for (String id : pending) {
            if (id.equals("jour") || id.startsWith("jour:")) {
                plugin.runCommands(p, plugin.settings().dailyReward());
                Settings.DailyVariant v = pendingVariant(plugin, id);
                if (v != null) {
                    plugin.runCommands(p, v.commands());
                    gains.add(v.text());
                }
            } else if (id.startsWith("palier:")) {
                int day = Integer.parseInt(id.substring("palier:".length()));
                plugin.runCommands(p, plugin.settings().steps().getOrDefault(day, List.of()));
                plugin.messages().send(p, "recompense-palier", "jour", Integer.toString(day));
            }
        }
        String gain = gains.isEmpty() ? "" : plugin.messages().text("chat.recompense-gain", "texte", String.join(", ", gains));
        plugin.messages().send(p, "recompense", "serie", Integer.toString(Streak.display(s.state, plugin.today())),
                "gain", gain);
        plugin.sound(p, "recompense");
    }

    /** « jour » (avant la 1.2) = la récompense d'aujourd'hui ; « jour:<id> » = celle du jour où le programme a été fini. */
    static Settings.DailyVariant pendingVariant(SHGuide plugin, String id) {
        Settings cfg = plugin.settings();
        return id.startsWith("jour:") ? cfg.variantById(id.substring(5)) : cfg.variantFor(plugin.today());
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

    /** Barre de progression : « barre-cases » cases, pleines à la part faite. */
    static String bar(Messages m, double current, double goal) {
        int cells = Math.max(1, m.number("menu.tache.barre-cases", 10));
        int full = goal <= 0 ? cells : (int) Math.floor(Math.max(0, Math.min(1, current / goal)) * cells);
        return m.text("menu.tache.barre-pleine").repeat(full) + m.text("menu.tache.barre-vide").repeat(cells - full);
    }

    private static void glint(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
    }

    private static ItemStack item(String icon, String name, List<String> lore) {
        ItemStack it = Icons.make(icon);
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
