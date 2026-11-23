package org.Cry_Legende.sHGuide;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.function.BiConsumer;

/** /guide pour les joueurs, /shguide pour l'administration. */
final class GuideCommands implements TabExecutor {

    private static final List<String> SUBS = List.of("reload", "info", "annonce", "set", "reset");
    private static final List<String> FIELDS = List.of("serie", "jour", "derniere-venue", "dernier-complet");
    private static final String ANNONCE_USAGE = "/shguide annonce <minutes|HH:MM> <texte> | <commande>";

    private final SHGuide plugin;

    GuideCommands(SHGuide plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages m = plugin.messages();
        if (command.getName().equalsIgnoreCase("guide")) {
            if (!(sender instanceof Player p)) {
                m.send(sender, "joueurs-seulement");
            } else if (!p.hasPermission("shguide.use")) {
                m.send(p, "permission");
            } else if (args.length > 0 && args[0].equalsIgnoreCase("aller")) {
                goToObjective(p);
            } else {
                GuideMenu.open(plugin, p);
            }
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (!SUBS.contains(sub) || !sender.hasPermission("shguide.admin." + sub)) {
            m.send(sender, "utilisation", "commandes", usage(sender));
            return true;
        }
        switch (sub) {
            case "reload" -> plugin.reload(sender);
            case "annonce" -> annonce(sender, args);
            case "info" -> withSession(sender, args, (p, s) -> info(sender, p, s));
            case "set" -> set(sender, args);
            default -> withSession(sender, args, (p, s) -> {
                String seen = s.state.lastSeen;
                s.state = PlayerState.fresh(p.getName());
                s.state.lastSeen = seen;
                plugin.sessions().markDirty(p);
                plugin.sessions().refresh(p);
                m.send(sender, "reset-ok", "joueur", p.getName());
            });
        }
        return true;
    }

    /** /guide aller : lance le clic de l'objectif du moment (bouton « Y aller » du dialogue de retour). */
    private void goToObjective(Player p) {
        plugin.sessions().refresh(p);
        Sessions.Session s = plugin.sessions().get(p.getUniqueId());
        if (s == null) {
            plugin.messages().send(p, "chargement");
            return;
        }
        String click = s.objective == null ? "" : s.objective.click();
        if (click.isEmpty() || click.equalsIgnoreCase("guide")) {
            GuideMenu.open(plugin, p);
        } else {
            plugin.runCommands(p, java.util.List.of(click));
        }
    }

    private void info(CommandSender sender, Player p, Sessions.Session s) {
        LocalDate today = plugin.today();
        long done = s.last == null ? 0 : s.last.tasks().stream().filter(Programme.TaskView::done).count();
        int total = s.last == null ? 0 : s.last.tasks().size();
        for (String line : plugin.messages().lines("chat.info", "joueur", p.getName(), "jour", String.valueOf(s.state.day),
                "palier", Integer.toString(s.state.palier), "faites", Long.toString(done), "total", Integer.toString(total),
                "serie", Integer.toString(Streak.display(s.state, today)), "record", Integer.toString(s.state.best),
                "joker", Streak.jokerAvailable(s.state, today) ? "oui" : "non", "venue", String.valueOf(s.state.lastSeen),
                "attente", s.state.pending.isEmpty() ? "rien" : String.join(", ", s.state.pending))) {
            sender.sendMessage(Messages.chat(line));
        }
    }

    private void annonce(CommandSender sender, String[] args) {
        String rest = String.join(" ", Arrays.copyOfRange(args, Math.min(2, args.length), args.length));
        int bar = rest.lastIndexOf(" | ");
        ZonedDateTime now = ZonedDateTime.now(plugin.settings().zone());
        OptionalLong start = args.length < 3 || bar < 0 ? OptionalLong.empty()
                : Annonces.startOf(args[1], now, plugin.settings().annonceGraceMinutes());
        if (start.isEmpty()) {
            plugin.messages().send(sender, "utilisation", "commandes", ANNONCE_USAGE);
            return;
        }
        plugin.annonces().set(start.getAsLong(), rest.substring(0, bar).trim(), rest.substring(bar + 3).trim());
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.sessions().refresh(p);
        }
        long minutes = Math.max(0, (start.getAsLong() - now.toInstant().toEpochMilli() + 59_999) / 60_000);
        plugin.messages().send(sender, "annonce-ok", "minutes", Long.toString(minutes));
    }

    private void set(CommandSender sender, String[] args) {
        if (args.length < 4 || !FIELDS.contains(args[2].toLowerCase(Locale.ROOT))) {
            plugin.messages().send(sender, "utilisation", "commandes",
                    "/shguide set <joueur> <" + String.join("|", FIELDS) + "> <valeur>");
            return;
        }
        String field = args[2].toLowerCase(Locale.ROOT);
        String value = args[3];
        withSession(sender, args, (p, s) -> {
            try {
                switch (field) {
                    case "serie" -> {
                        int n = Integer.parseInt(value);
                        if (n < 0) {
                            throw new NumberFormatException();
                        }
                        s.state.streak = n;
                        s.state.best = Math.max(s.state.best, n);
                    }
                    case "jour" -> s.state.day = LocalDate.parse(value).toString();
                    case "derniere-venue" -> s.state.lastSeen = LocalDate.parse(value).toString();
                    default -> s.state.lastComplete = value.equalsIgnoreCase("aucun") ? null : LocalDate.parse(value).toString();
                }
            } catch (NumberFormatException | DateTimeParseException e) {
                plugin.messages().send(sender, "set-erreur", "champ", field, "valeur", value);
                return;
            }
            plugin.sessions().markDirty(p);
            plugin.messages().send(sender, "set-ok", "champ", field, "joueur", p.getName(), "valeur", value);
        });
    }

    private void withSession(CommandSender sender, String[] args, BiConsumer<Player, Sessions.Session> action) {
        Player p = args.length < 2 ? null : Bukkit.getPlayerExact(args[1]);
        Sessions.Session s = p == null ? null : plugin.sessions().get(p.getUniqueId());
        if (s == null) {
            plugin.messages().send(sender, "joueur-inconnu", "joueur", args.length < 2 ? "?" : args[1]);
            return;
        }
        action.accept(p, s);
    }

    private String usage(CommandSender sender) {
        List<String> allowed = SUBS.stream().filter(s -> sender.hasPermission("shguide.admin." + s)).toList();
        return allowed.isEmpty() ? "/guide" : "/shguide <" + String.join("|", allowed) + ">";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("guide")) {
            if (args.length == 1 && "aller".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                out.add("aller");
            }
            return out;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            for (String s : SUBS) {
                if (sender.hasPermission("shguide.admin." + s) && s.startsWith(sub)) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && List.of("info", "set", "reset").contains(sub) && sender.hasPermission("shguide.admin." + sub)) {
            String start = args[1].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(start)) {
                    out.add(p.getName());
                }
            }
        } else if (args.length == 3 && sub.equals("set") && sender.hasPermission("shguide.admin.set")) {
            for (String f : FIELDS) {
                if (f.startsWith(args[2].toLowerCase(Locale.ROOT))) {
                    out.add(f);
                }
            }
        }
        return out;
    }
}
