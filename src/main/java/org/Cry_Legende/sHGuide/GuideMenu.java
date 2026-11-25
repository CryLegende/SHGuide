package org.Cry_Legende.sHGuide;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
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
import java.util.OptionalDouble;

/** Les écrans de Jake : construits à l'ouverture d'après menus.yml, redessinés sur place après un clic qui change leur contenu. */
final class GuideMenu {

    /** Plus grande pile affichée : au-delà, le jeu n'a plus la place d'écrire le nombre. */
    private static final int MAX_AMOUNT = 99;

    private GuideMenu() {
    }

    private static final class Holder implements InventoryHolder {
        final String screen;
        int page;
        Map<Integer, List<String>> clicks = Map.of();
        Texte.Contexte ctx;
        Inventory inv;

        Holder(String screen) {
            this.screen = screen;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    static boolean isOurs(Inventory inv) {
        return inv != null && inv.getHolder(false) instanceof Holder;
    }

    /** L'accueil. */
    static void open(SHGuide plugin, Player p) {
        open(plugin, p, plugin.menus().home(), 0);
    }

    static void open(SHGuide plugin, Player p, String screenId, int page) {
        Programme.Tick t = plugin.sessions().recent(p);
        Sessions.Session s = plugin.sessions().get(p.getUniqueId());
        if (t == null || s == null) {
            plugin.messages().send(p, "chargement");
            return;
        }
        MenusConfig menus = plugin.menus();
        MenusConfig.Ecran screen = menus.screen(screenId);
        if (screen == null) {
            screen = menus.screen(menus.home());
        }
        Holder open = p.getOpenInventory().getTopInventory().getHolder(false) instanceof Holder h
                && h.screen.equals(screen.id()) && h.inv.getSize() == screen.size() ? h : null;
        Holder holder = open != null ? open : new Holder(screen.id());
        holder.page = Math.max(0, page);
        Dessin d = new Dessin(plugin, p, s, t, screen, holder);
        ItemStack[] items = d.draw();
        holder.clicks = d.clicks;
        holder.ctx = d.ctx;
        if (open == null) {
            holder.inv = Bukkit.createInventory(holder, screen.size(), Messages.item(Texte.line(screen.title(), d.ctx)));
            holder.inv.setContents(items);
            p.openInventory(holder.inv);
            plugin.sound(p, "ouverture");
        } else {
            holder.inv.setContents(items);
        }
        seen(plugin, p, s);
    }

    /** Le joueur a vu Jake aujourd'hui : l'alerte « tâches du jour » s'éteint. */
    private static void seen(SHGuide plugin, Player p, Sessions.Session s) {
        s.back = false;
        String today = plugin.today().toString();
        if (!today.equals(s.state.alertSeen)) {
            s.state.alertSeen = today;
            plugin.sessions().markDirty(p);
        }
        plugin.sessions().view(p, s);
    }

    /** Un écran en train d'être dessiné. */
    private static final class Dessin {

        private final SHGuide plugin;
        private final Player p;
        private final Sessions.Session s;
        private final Programme.Tick t;
        private final MenusConfig.Ecran screen;
        private final Holder holder;
        private final Map<Integer, List<String>> clicks = new HashMap<>();
        private Texte.Contexte ctx;
        private ItemStack[] items;

        Dessin(SHGuide plugin, Player p, Sessions.Session s, Programme.Tick t, MenusConfig.Ecran screen, Holder holder) {
            this.plugin = plugin;
            this.p = p;
            this.s = s;
            this.t = t;
            this.screen = screen;
            this.holder = holder;
        }

        ItemStack[] draw() {
            items = new ItemStack[screen.size()];
            ctx = Jetons.of(plugin, p, s, t);
            Map<String, String> more = new HashMap<>();
            MenusConfig.Zone news = screen.zones().get("nouveautes");
            List<Nouveautes.Entree> out = plugin.nouveautes().out(plugin.today());
            if (news != null) {
                int pages = Math.max(1, (out.size() + news.slots().size() - 1) / news.slots().size());
                holder.page = Math.min(holder.page, pages - 1);
                more.put("page", Integer.toString(holder.page + 1));
                more.put("pages", Integer.toString(pages));
                more.put("page_suivante", Jetons.yes(holder.page + 1 < pages));
                more.put("page_precedente", Jetons.yes(holder.page > 0));
            }
            ctx = ctx.with(more, Map.of(), Map.of());
            String phrase = "";
            for (MenusConfig.Phrase ph : screen.phrases()) {
                if (Texte.all(ph.conditions(), ctx)) {
                    phrase = Texte.line(ph.text(), ctx);
                    break;
                }
            }
            ctx = ctx.with(Map.of("phrase", phrase), Map.of(), Map.of());

            MenusConfig.Style style = plugin.menus().style();
            fill(screen.ring(), style.ring());
            String accent = style.accent();
            for (MenusConfig.AccentRule r : style.rules()) {
                if (Texte.all(r.conditions(), ctx)) {
                    accent = r.icon();
                    break;
                }
            }
            fill(screen.accent(), accent);
            for (MenusConfig.Bouton b : screen.buttons()) {
                for (int slot : b.slots()) {
                    if (!clicks.containsKey(slot) && Texte.all(b.conditions(), ctx)) {
                        put(slot, b.item(), ctx);
                    }
                }
            }
            for (MenusConfig.Zone z : screen.zones().values()) {
                switch (z.type()) {
                    case "taches" -> tasks(z);
                    case "piste" -> track(z);
                    case "paliers" -> steps(z);
                    case "nouveautes" -> news(z, out);
                    default -> tutorial(z);
                }
            }
            clicks.values().removeIf(List::isEmpty);
            return items;
        }

        private void fill(List<Integer> slots, String icon) {
            if (icon.isBlank()) {
                return;
            }
            ItemStack it = Icons.make(icon);
            ItemMeta meta = it.getItemMeta();
            meta.displayName(Messages.item(" "));
            meta.addItemFlags(ItemFlag.values());
            meta.setHideTooltip(true);
            it.setItemMeta(meta);
            for (int slot : slots) {
                items[slot] = it;
            }
        }

        /** Pose l'objet et retient son clic (une liste vide réserve quand même la case pour les boutons suivants). */
        private void put(int slot, MenusConfig.Modele m, Texte.Contexte c) {
            if (slot < 0 || slot >= items.length) {
                return;
            }
            ItemStack it = Icons.make(Texte.line(m.icon(), c));
            ItemMeta meta = it.getItemMeta();
            meta.displayName(Messages.item(Texte.line(m.name(), c)));
            List<String> lore = Texte.lines(m.lore(), c);
            meta.lore(lore.stream().map(Messages::item).toList());
            meta.addItemFlags(ItemFlag.values());
            if (m.glint() || !m.glintIf().isEmpty() && Texte.all(m.glintIf(), c)) {
                meta.setEnchantmentGlintOverride(true);
            }
            int amount = 1;
            if (!m.amount().isEmpty()) {
                OptionalDouble n = Num.parse(Texte.line(m.amount(), c));
                amount = (int) Math.max(1, Math.min(MAX_AMOUNT, n.orElse(1)));
                if (amount > it.getMaxStackSize()) {
                    meta.setMaxStackSize(amount);
                }
            }
            it.setItemMeta(meta);
            it.setAmount(amount);
            items[slot] = it;
            List<String> lines = new ArrayList<>();
            for (String line : m.click()) {
                String l = Texte.line(line, c).trim();
                if (!l.isEmpty()) {
                    lines.add(l);
                }
            }
            clicks.put(slot, lines);
        }

        private void tasks(MenusConfig.Zone z) {
            for (int i = 0; i < t.tasks().size() && i < z.slots().size(); i++) {
                Programme.TaskView v = t.tasks().get(i);
                String state = v.done() ? "faite" : v.current() > 0 ? "en-cours" : "a-faire";
                Texte.Contexte c = ctx.with(Map.of("numero", Integer.toString(i + 1), "total", Integer.toString(t.tasks().size()),
                                "texte", v.text(), "icone", v.def().icon(), "actuel", Num.format(Math.min(v.current(), v.goal())),
                                "quantite", Num.format(v.goal()), "clic", v.def().click(),
                                "cliquable", Jetons.yes(!v.def().click().isBlank())),
                        Map.of("tache", v.ratio()), Map.of("description", v.def().description()));
                put(z.slots().get(i), z.templates().get(state), c);
                MenusConfig.Modele light = z.templates().get("voyant-" + state);
                if (light != null && i < z.lights().size()) {
                    put(z.lights().get(i), light, c);
                }
            }
        }

        private void track(MenusConfig.Zone z) {
            Settings cfg = plugin.settings();
            Serie serie = Serie.of(s.state, plugin.today(), cfg.cycle(), cfg.stepDays());
            List<Integer> days = serie.cells(z.slots().size());
            int shift = (z.slots().size() - days.size()) / 2;
            int previous = serie.start();
            for (int i = 0; i < days.size(); i++) {
                int day = days.get(i);
                Serie.Jour state = serie.day(day, previous);
                previous = day;
                boolean last = i == days.size() - 1;
                String key = last ? "palier" : switch (state) {
                    case FAIT -> "fait";
                    case AUJOURDHUI -> "aujourdhui";
                    default -> "a-venir";
                };
                if (last && state == Serie.Jour.FAIT && z.templates().containsKey("palier-fait")) {
                    key = "palier-fait";
                }
                put(z.slots().get(i + shift), z.templates().get(key), ctx.with(Map.of("jour_piste", Integer.toString(day),
                        "gain", cfg.gain(day)), Map.of(), Map.of()));
            }
        }

        private void steps(MenusConfig.Zone z) {
            Settings cfg = plugin.settings();
            Serie serie = Serie.of(s.state, plugin.today(), cfg.cycle(), cfg.stepDays());
            int i = 0;
            for (int step : cfg.stepDays()) {
                if (i >= z.slots().size()) {
                    break;
                }
                String key = switch (serie.step(step, s.state.pending)) {
                    case A_PRENDRE -> "a-prendre";
                    case ATTEINT -> "atteint";
                    case SUIVANT -> "suivant";
                    case LOIN -> "loin";
                };
                put(z.slots().get(i++), z.templates().get(key), ctx.with(Map.of("jour_palier", Integer.toString(step),
                        "gain", cfg.gain(step), "reste", Integer.toString(Math.max(0, step - serie.position()))), Map.of(), Map.of()));
            }
        }

        private void news(MenusConfig.Zone z, List<Nouveautes.Entree> out) {
            Settings cfg = plugin.settings();
            LocalDate today = plugin.today();
            List<Nouveautes.Entree> unread = plugin.nouveautes().unread(s.state, today, cfg.newsRecentDays());
            int from = holder.page * z.slots().size();
            List<Nouveautes.Entree> page = out.subList(Math.min(from, out.size()), Math.min(from + z.slots().size(), out.size()));
            for (int i = 0; i < page.size(); i++) {
                Nouveautes.Entree n = page.get(i);
                put(z.slots().get(i), z.templates().get(unread.contains(n) ? "nouvelle" : "vue"), ctx.with(Map.of(
                        "titre", n.title(), "icone", n.icon(), "date", n.date().format(cfg.newsDate()), "commande", n.command(),
                        "commande_oui", Jetons.yes(!n.command().isBlank()), "cliquable", Jetons.yes(!n.click().isEmpty())),
                        Map.of(), Map.of("texte", n.lines(plugin.menus().sentence()))));
                clicks.put(z.slots().get(i), n.click());
            }
            if (plugin.nouveautes().markRead(s.state, page)) {
                plugin.sessions().markDirty(p);
            }
        }

        private void tutorial(MenusConfig.Zone z) {
            AccueilConfig ac = plugin.settings().accueil();
            List<AccueilConfig.Step> steps = ac.tutorial().steps();
            AccueilConfig.Step current = Accueil.tutorialActive(s.state, ac) ? Accueil.currentStep(s.state, ac) : null;
            for (int i = 0; i < steps.size() && i < z.slots().size(); i++) {
                AccueilConfig.Step st = steps.get(i);
                boolean done = s.state.tutoDone.contains(st.id()) || s.state.tutoFinished;
                boolean now = st == current;
                double part = 0;
                if (now && s.state.tutoBase != null) {
                    OptionalDouble raw = EtapeProgres.raw(st, ctx.resolver());
                    part = raw.isEmpty() ? 0 : EtapeProgres.since(raw.getAsDouble(), s.state.tutoBase);
                }
                put(z.slots().get(i), z.templates().get(done ? "faite" : now ? "en-cours" : "a-venir"), ctx.with(Map.of(
                        "numero", Integer.toString(i + 1), "total", Integer.toString(steps.size()), "texte", st.text(),
                        "icone", st.icon(), "clic", now ? st.click() : "", "cliquable", Jetons.yes(now && !st.click().isBlank())),
                        Map.of("etape", part), Map.of("description", st.lore())));
            }
        }
    }

    /** Lance les actions d'un clic, dans l'ordre. */
    private static void run(SHGuide plugin, Player p, Holder h, List<String> lines) {
        boolean command = false;
        boolean quiet = false;
        for (String line : lines) {
            Action a;
            try {
                a = Action.parse(line);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Écran " + h.screen + " : " + e.getMessage());
                continue;
            }
            if (a == null) {
                continue;
            }
            switch (a.type()) {
                case MENU -> {
                    quiet = true;
                    open(plugin, p, a.arg(), 0);
                }
                case RECUPERER -> {
                    quiet = true;
                    Recompenses.claim(plugin, p, a.arg());
                    open(plugin, p, h.screen, h.page);
                }
                case PAGE -> open(plugin, p, h.screen, h.page + Integer.parseInt(a.arg()));
                case FLECHE -> {
                    plugin.toggleArrow(p);
                    open(plugin, p, h.screen, h.page);
                }
                case FERMER -> p.closeInventory();
                case MESSAGE -> p.sendMessage(Messages.chat(a.arg().replace("{joueur}", p.getName())));
                case SON -> p.playSound(p.getLocation(), a.arg(), 1f, 1f);
                case CONSOLE -> plugin.runCommands(p, List.of("[console] " + a.arg()));
                case OBJECTIF -> {
                    Sessions.Session s = plugin.sessions().get(p.getUniqueId());
                    String click = s == null || s.objective == null ? "" : s.objective.click();
                    if (!click.isBlank()) {
                        command |= runObjective(plugin, p, h, click);
                    }
                }
                case JOUEUR -> {
                    command = true;
                    leave(plugin, p, h);
                    plugin.runCommands(p, List.of(a.arg()));
                }
            }
        }
        if (!quiet) {
            plugin.sound(p, "clic");
        }
        if (command) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) {
                    return;
                }
                Inventory top = p.getOpenInventory().getTopInventory();
                // la commande n'a ouvert aucun autre menu (téléportation, message...) : le nôtre se ferme
                if (top.equals(h.inv)) {
                    p.closeInventory();
                    plugin.origine().clear(p);
                } else if (top.getType() == InventoryType.CRAFTING) {
                    plugin.origine().clear(p);
                }
            }, plugin.menus().closeTicks());
        }
    }

    /** Clic de l'objectif : un écran de Jake (« guide recompenses ») s'ouvre directement. Vrai si c'est une autre commande. */
    private static boolean runObjective(SHGuide plugin, Player p, Holder h, String click) {
        String[] words = click.trim().split("\\s+", 2);
        if (words[0].equalsIgnoreCase("guide") && (words.length == 1 || plugin.menus().screen(words[1]) != null)) {
            open(plugin, p, words.length == 1 ? plugin.menus().home() : words[1], 0);
            return false;
        }
        leave(plugin, p, h);
        plugin.runCommands(p, List.of(click));
        return true;
    }

    /** Le menu que la commande va ouvrir pourra revenir ici, sauf si notre menu est déjà fermé (« [fermer] » avant elle). */
    private static void leave(SHGuide plugin, Player p, Holder h) {
        if (p.getOpenInventory().getTopInventory().equals(h.inv)) {
            plugin.origine().set(p, h.screen, h.page);
        }
    }

    static final class Listener implements org.bukkit.event.Listener {

        private final SHGuide plugin;

        Listener(SHGuide plugin) {
            this.plugin = plugin;
        }

        @EventHandler
        public void onClick(InventoryClickEvent e) {
            if (!(e.getInventory().getHolder(false) instanceof Holder h)) {
                return;
            }
            e.setCancelled(true);
            if (e.getClickedInventory() != e.getInventory() || !(e.getWhoClicked() instanceof Player p)) {
                return;
            }
            List<String> lines = h.clicks.get(e.getRawSlot());
            if (lines != null) {
                plugin.sync(() -> {
                    // un clic resté en route pendant que le menu changeait ne compte pas
                    if (p.isOnline() && p.getOpenInventory().getTopInventory().equals(h.inv)) {
                        run(plugin, p, h, lines);
                    }
                });
            }
        }

        @EventHandler
        public void onDrag(InventoryDragEvent e) {
            if (e.getInventory().getHolder(false) instanceof Holder) {
                e.setCancelled(true);
            }
        }
    }
}
