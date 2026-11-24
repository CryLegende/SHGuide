package org.Cry_Legende.sHGuide;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/** Jake, le guide du joueur : objectif du moment, programme du jour, série, retour guidé, « Comment faire… ». */
public final class SHGuide extends JavaPlugin {

    private volatile Settings settings;
    private volatile Messages messages;
    private HikariDataSource pool;
    private GuideStore store;
    private Sessions sessions;
    private Retour retour;
    private Accueil accueil;
    private Fleche fleche;
    private Arrivee arrivee;
    private AnnonceBarre annonceBarre;
    private final Annonces annonces = new Annonces();
    private GuideExpansion expansion;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "messages.yml").exists()) {
            saveResource("messages.yml", false);
        }
        try {
            settings = readSettings();
            messages = new Messages(loadMessages());
        } catch (RuntimeException e) {
            getLogger().severe("config.yml ou messages.yml invalide, SHGuide s'arrête : " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Settings.DbConfig db = settings.db();
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
        getServer().getPluginManager().registerEvents(sessions, this);
        getServer().getPluginManager().registerEvents(new GuideMenu.Listener(this), this);
        getServer().getPluginManager().registerEvents(arrivee, this);
        GuideCommands commands = new GuideCommands(this);
        for (String name : List.of("guide", "shguide")) {
            PluginCommand command = getCommand(name);
            command.setExecutor(commands);
            command.setTabCompleter(commands);
        }
        expansion = new GuideExpansion(this);
        expansion.register();
        Bukkit.getScheduler().runTaskTimer(this, sessions::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, accueil::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, annonceBarre::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, fleche::tick, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, fleche::readSources, 20L, 1L);
        for (Player p : Bukkit.getOnlinePlayers()) {
            sessions.load(p);
        }
    }

    @Override
    public void onDisable() {
        if (expansion != null) {
            expansion.unregister();
        }
        if (fleche != null) {
            fleche.clearAll();
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

    /** Recharge config.yml et messages.yml ; un fichier invalide ne change rien et le message le nomme. */
    boolean reload(CommandSender sender) {
        Settings s;
        Messages m;
        try {
            s = readSettings();
        } catch (RuntimeException e) {
            messages.send(sender, "reload-erreur", "fichier", "config.yml", "erreur", e.getMessage());
            return false;
        }
        try {
            m = new Messages(loadMessages());
        } catch (RuntimeException e) {
            messages.send(sender, "reload-erreur", "fichier", "messages.yml", "erreur", e.getMessage());
            return false;
        }
        settings = s;
        Icons.clear();
        messages = m;
        messages.send(sender, "reload-ok");
        return true;
    }

    private Settings readSettings() {
        YamlConfiguration file = new YamlConfiguration();
        try {
            file.load(new File(getDataFolder(), "config.yml"));
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        return Settings.read(file);
    }

    private YamlConfiguration loadMessages() {
        YamlConfiguration file = new YamlConfiguration();
        try {
            file.load(new File(getDataFolder(), "messages.yml"));
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        InputStream defaults = getResource("messages.yml");
        if (defaults != null) {
            file.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaults, StandardCharsets.UTF_8)));
        }
        return file;
    }

    Settings settings() {
        return settings;
    }

    Messages messages() {
        return messages;
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
        return LocalDate.now(settings.zone());
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
                p.performCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
            }
        }
    }

    void sound(Player p, String key) {
        String name = settings.sounds().get(key);
        if (name != null && !name.isEmpty()) {
            p.playSound(p.getLocation(), name, 1f, 1f);
        }
    }
}
