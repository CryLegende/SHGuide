package org.Cry_Legende.sHGuide;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lecture et écriture sur un vrai MariaDB. Ignoré sans SHGUIDE_TEST_URL (ex. jdbc:mariadb://127.0.0.1:3306/mc_test),
 * SHGUIDE_TEST_USER et SHGUIDE_TEST_PASSWORD. La base doit contenir « test » : le test recrée la table.
 */
class GuideStoreTest {

    private HikariDataSource pool;
    private GuideStore store;

    @BeforeEach
    void setUp() throws Exception {
        String url = System.getenv("SHGUIDE_TEST_URL");
        Assumptions.assumeTrue(url != null && url.contains("test"));
        HikariConfig c = new HikariConfig();
        c.setJdbcUrl(url);
        c.setUsername(System.getenv("SHGUIDE_TEST_USER"));
        c.setPassword(System.getenv("SHGUIDE_TEST_PASSWORD"));
        c.setDriverClassName("org.mariadb.jdbc.Driver");
        pool = new HikariDataSource(c);
        try (Connection con = pool.getConnection(); Statement st = con.createStatement()) {
            st.execute("DROP TABLE IF EXISTS shguide_joueurs");
        }
        store = new GuideStore(pool);
        store.createTable();
    }

    @AfterEach
    void tearDown() {
        if (pool != null) {
            pool.close();
        }
    }

    @Test
    void roundTrip() throws Exception {
        UUID id = UUID.randomUUID();
        assertNull(store.load(id));
        PlayerState s = PlayerState.fresh("Bot");
        s.streak = 5;
        s.pending.add("jour");
        s.baselines.put("vote#0", 12.0);
        s.jokerWeeks.add("2026-W48");
        store.save(id, s);
        s.streak = 6;
        store.save(id, s);
        PlayerState back = store.load(id);
        assertEquals(6, back.streak);
        assertEquals(12.0, back.baselines.get("vote#0"));
        assertTrue(back.jokerWeeks.contains("2026-W48"));
    }
}
