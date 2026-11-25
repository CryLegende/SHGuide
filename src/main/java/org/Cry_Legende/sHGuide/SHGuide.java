package org.Cry_Legende.sHGuide;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/** Jake, le guide du joueur : objectif du moment, tâches du jour, récompenses, nouveautés, retour guidé, accueil. */
public final class SHGuide extends JavaPlugin {

    /** Les quatre fichiers, lus ensemble : un reload les remplace tous d'un coup, ou aucun. */
    private record Files(Settings settings, MenusConfig menus, Nouveautes nouveautes, Messages messages) {
    }

    private volatile Files files;
    private HikariDataSource pool;
    private GuideStore store;
    private Sessions sessions;
    private Retour retour;
    private Accueil accueil;
    private Fleche fleche;
    private Arrivee arrivee;
    private AnnonceBarre annonceBarre;
    private Origine origine;
    private final Annonces annonces = new Annonces();
    private GuideExpansion expansion;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        for (String name : List.of("messages.yml", "menus.yml", "nouveautes.yml")) {
            if (!new File(getDataFolder(), name).exists()) {
                saveResource(name, false);
            }
        }
        try {
            files = readFiles();
        } catch (InvalidFile e) {
            getLogger().severe(e.name + " invalide, SHGuide s'arrête : " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Settings.DbConfig db = settings().db();
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:mariadb://" + db.host() + ":" + db.port() + "/" + db.database());
        hc.setUsername(db.user());
        hc.setPassword(db.password());
        hc.setDriverClassName("org.mariadb.jdbc.Driver");
        hc.setMaximumPoolSize(3);
        hc.setPoolName("SHGuide");
        try {
            pool = new HikariDataSource(hc);
            store = new GuideStore(pool);
            store.createTable();
        } catch (Exception e) {
            getLogger().severe("Table shguide_joueurs impossible à créer, SHGuide s'arrête : " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        sessions = new Sessions(this);
        retour = new Retour(this);
        accueil = new Accueil(this);
        fleche = new Fleche(this);
        arrivee = new Arrivee(this);
        annonceBarre = new AnnonceBarre(this);
        origine = new Origine(this);
        getServer().getPluginManager().registerEvents(sessions, this);
        getServer().getPluginManager().registerEvents(new GuideMenu.Listener(this), this);
        getServer().getPluginManager().registerEvents(origine, this);
        getServer().getPluginManager().registerEvents(arrivee, this);
        getServer().getPluginManager().registerEvents(fleche, this);
        GuideCommands commands = new GuideCommands(this);
        for (String name : List.of("guide", "shguide")) {
            PluginCommand command = getCommand(name);
            command.setExecutor(commands);
            command.setTabCompleter(commands);
        }
        expansion = new GuideExpansion(this);
        expansion.register();
        Bukkit.getScheduler().runTaskTimer(this, sessions::tick, 20L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, accueil::tick, 20L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, annonceBarre::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, fleche::tick, 1L, 1L);
        for (Player p : Bukkit.getOnlinePlayers()) {
            sessions.load(p);
        }
    }

    @Override
    public void onDisable() {
        // les écrans ouverts ne répondraient plus aux clics
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (GuideMenu.isOurs(p.getOpenInventory().getTopInventory())) {
                p.closeInventory();
            }
        }
        if (expansion != null) {
            expansion.unregister();
        }
        if (fleche != null) {
            fleche.close();
        }
        if (arrivee != null) {
            arrivee.close();
        }
        if (accueil != null) {
            accueil.hideAll();
        }
        if (annonceBarre != null) {
            annonceBarre.hideAll();
        }
        if (sessions != null) {
            sessions.saveAll();
        }
        if (pool != null) {
            pool.close();
        }
    }

    /** Recharge les quatre fichiers ; si l'un est invalide, rien ne change et le message le nomme. */
    boolean reload(CommandSender sender) {
        Files next;
        try {
            next = readFiles();
        } catch (InvalidFile e) {
            messages().send(sender, "reload-erreur", "fichier", e.name, "erreur", e.getMessage());
            return false;
        }
        files = next;
        Icons.clear();
        messages().send(sender, "reload-ok");
        return true;
    }

    /** Fichier illisible ou incohérent, et lequel. */
    private static final class InvalidFile extends RuntimeException {
        private final String name;

        private InvalidFile(String name, RuntimeException cause) {
            super(String.valueOf(cause.getMessage()), cause);
            this.name = name;
        }
    }

    private Files readFiles() {
        Settings s = read("config.yml", Settings::read);
        MenusConfig menus = read("menus.yml", MenusConfig::read);
        Nouveautes news = read("nouveautes.yml", Nouveautes::read);
        Messages m = read("messages.yml", file -> {
            InputStream defaults = getResource("messages.yml");
            if (defaults != null) {
                file.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaults, StandardCharsets.UTF_8)));
            }
            return new Messages(file);
        });
        checkTasks(s, menus);
        return new Files(s, menus, news, m);
    }

    private <T> T read(String name, java.util.function.Function<YamlConfiguration, T> reader) {
        YamlConfiguration file = new YamlConfiguration();
        try {
            file.load(new File(getDataFolder(), name));
            return reader.apply(file);
        } catch (RuntimeException e) {
            throw new InvalidFile(name, e);
        } catch (Exception e) {
            throw new InvalidFile(name, new IllegalArgumentException(e.getMessage()));
        }
    }

    /** Les zones « taches » ont assez de cases pour les tâches d'un jour. */
    private static void checkTasks(Settings s, MenusConfig menus) {
        int daily = s.tasks().size() + (s.rotating().isEmpty() ? 0 : 1);
        for (MenusConfig.Ecran e : menus.screens().values()) {
            MenusConfig.Zone z = e.zones().get("taches");
            if (z != null && z.slots().size() < daily) {
                throw new InvalidFile("menus.yml", new IllegalArgumentException("menus." + e.id() + ".zones.taches.cases : "
                        + daily + " cases attendues pour les tâches du jour"));
            }
        }
    }

    /** Coupe ou remet la flèche du tutoriel. */
    void toggleArrow(Player p) {
        Sessions.Session s = sessions.get(p.getUniqueId());
        if (s == null) {
            messages().send(p, "chargement");
            return;
        }
        s.state.arrowOff = !s.state.arrowOff;
        sessions.markDirty(p);
        if (s.state.arrowOff) {
            fleche.setTarget(p, null, "");
        }
        messages().send(p, s.state.arrowOff ? "fleche-off" : "fleche-on");
    }

    Settings settings() {
        return files.settings();
    }

    MenusConfig menus() {
        return files.menus();
    }

    Nouveautes nouveautes() {
        return files.nouveautes();
    }

    Messages messages() {
        return files.messages();
    }

    Origine origine() {
        return origine;
    }

    Sessions sessions() {
        return sessions;
    }

    Retour retour() {
        return retour;
    }

    Accueil accueil() {
        return accueil;
    }

    Fleche fleche() {
        return fleche;
    }

    AnnonceBarre annonceBarre() {
        return annonceBarre;
    }

    Arrivee arrivee() {
        return arrivee;
    }

    Annonces annonces() {
        return annonces;
    }

    GuideStore store() {
        return store;
    }

    LocalDate today() {
        return LocalDate.now(settings().zone());
    }

    void sync(Runnable r) {
        Bukkit.getScheduler().runTask(this, r);
    }

    /** Commandes de la config : [console] en console, sinon lancées par le joueur ; {joueur} = son pseudo. */
    void runCommands(Player p, List<String> commands) {
        for (String raw : commands) {
            String cmd = raw.replace("{joueur}", p.getName()).trim();
            if (cmd.isEmpty()) {
                continue;
            }
            if (cmd.startsWith("[console]")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.substring("[console]".length()).trim());
            } else {
                playerCommand(p, cmd);
            }
        }
    }

    /**
     * Commande du joueur, comme s'il l'avait tapée : performCommand seul n'avertit pas les plugins qui écoutent les commandes
     * (menus DeluxeMenus, restrictions de combat...).
     */
    void playerCommand(Player p, String command) {
        PlayerCommandPreprocessEvent e = new PlayerCommandPreprocessEvent(p, command.startsWith("/") ? command : "/" + command);
        Bukkit.getPluginManager().callEvent(e);
        String typed = e.getMessage().startsWith("/") ? e.getMessage().substring(1) : e.getMessage();
        if (!e.isCancelled() && !typed.isBlank()) {
            p.performCommand(typed);
        }
    }

    void sound(Player p, String key) {
        String name = settings().sounds().get(key);
        if (name != null && !name.isEmpty()) {
            p.playSound(p.getLocation(), name, 1f, 1f);
        }
    }
}
