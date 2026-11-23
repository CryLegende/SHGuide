package org.Cry_Legende.sHGuide;

import com.google.gson.Gson;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/** Table shguide_joueurs : une ligne par joueur, son état en JSON. Méthodes bloquantes, hors du fil principal. */
final class GuideStore {

    private static final Gson GSON = new Gson();

    private final DataSource source;

    GuideStore(DataSource source) {
        this.source = source;
    }

    void createTable() throws SQLException {
        try (Connection c = source.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS shguide_joueurs ("
                    + "uuid CHAR(36) NOT NULL PRIMARY KEY, "
                    + "name VARCHAR(16) NOT NULL, "
                    + "data MEDIUMTEXT NOT NULL, "
                    + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    PlayerState load(UUID id) throws SQLException {
        try (Connection c = source.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT data FROM shguide_joueurs WHERE uuid = ?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? GSON.fromJson(rs.getString(1), PlayerState.class).normalize() : null;
            }
        }
    }

    void save(UUID id, PlayerState s) throws SQLException {
        String json = GSON.toJson(s);
        try (Connection c = source.getConnection(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO shguide_joueurs (uuid, name, data) VALUES (?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE name = VALUES(name), data = VALUES(data)")) {
            ps.setString(1, id.toString());
            ps.setString(2, s.name);
            ps.setString(3, json);
            ps.executeUpdate();
        }
    }
}
