package org.Cry_Legende.sHGuide;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenusConfigTest {

    static YamlConfiguration example() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                MenusConfigTest.class.getResourceAsStream("/menus.yml"), StandardCharsets.UTF_8));
    }

    private static String error(YamlConfiguration c) {
        return assertThrows(IllegalArgumentException.class, () -> MenusConfig.read(c)).getMessage();
    }

    @Test
    void readsTheExample() {
        MenusConfig m = MenusConfig.read(example());
        assertEquals(List.of("accueil", "taches", "recompenses", "nouveautes", "aide", "premiers-pas"), List.copyOf(m.screens().keySet()));
        assertEquals("accueil", m.home());
        MenusConfig.Ecran home = m.screen("accueil");
        assertEquals(45, home.size());
        assertTrue(home.ring().containsAll(List.of(0, 8, 9, 17, 36, 44)));
        assertEquals(List.of(10, 16, 19, 25, 28, 34), home.accent());
        assertEquals(List.of(10, 16, 28, 34), m.screen("recompenses").accent());
        assertEquals(List.of(20, 21, 23, 24), m.screen("taches").zones().get("taches").slots());
        assertEquals(List.of(29, 30, 32, 33), m.screen("taches").zones().get("taches").lights());
    }

    @Test
    void screensAreFoundWithoutAccentsOrCase() {
        MenusConfig m = MenusConfig.read(example());
        assertNotNull(m.screen("Tâches"));
        assertNotNull(m.screen("RÉCOMPENSES"));
        assertNotNull(m.screen("nouveautés"));
        assertNull(m.screen("inconnu"));
        assertNull(m.screen(null));
    }

    @Test
    void everyButtonOfTheExampleStaysInsideAndLinksSomewhere() {
        MenusConfig m = MenusConfig.read(example());
        for (MenusConfig.Ecran e : m.screens().values()) {
            for (MenusConfig.Bouton b : e.buttons()) {
                for (int s : b.slots()) {
                    assertTrue(s >= 0 && s < e.size(), e.id() + "." + b.id());
                }
                for (String line : b.item().click()) {
                    Action a = Action.parse(line);
                    if (a != null && a.type() == Action.Type.MENU) {
                        assertNotNull(m.screen(a.arg()), line);
                    }
                }
            }
        }
    }

    @Test
    void mistakesNameTheKey() {
        YamlConfiguration c = example();
        c.set("menus.accueil.boutons.jake.case", 45);
        assertTrue(error(c).contains("menus.accueil.boutons.jake.case"));
        c = example();
        c.set("menus.accueil.boutons.aide.clic", List.of("[menu] aidee"));
        assertTrue(error(c).contains("aidee"));
        c = example();
        c.set("menus.accueil.boutons.aide.clic", List.of("[ouvrir] aide"));
        assertTrue(error(c).contains("[ouvrir] aide"));
        c = example();
        c.set("menus.accueil.boutons.taches.brille-si", List.of("{taches_reste}"));
        assertTrue(error(c).contains("menus.accueil.boutons.taches.brille-si"));
        c = example();
        c.set("menus.taches.zones.taches.faite", null);
        assertTrue(error(c).contains("faite"));
        c = example();
        c.set("menus.taches.zones.tache.cases", List.of(1));
        assertTrue(error(c).contains("zone inconnue"));
        c = example();
        c.set("accueil", "nulle-part");
        assertTrue(error(c).contains("nulle-part"));
        c = example();
        c.set("menus.accueil.lignes", 7);
        assertTrue(error(c).contains("menus.accueil.lignes"));
    }

    @Test
    void oneRowAndTwoRowMenusHaveNoAccent() {
        YamlConfiguration c = example();
        c.set("menus.petit.lignes", 2);
        c.set("menus.petit.boutons.x.case", 0);
        MenusConfig.Ecran e = MenusConfig.read(c).screen("petit");
        assertEquals(18, e.size());
        assertTrue(e.accent().isEmpty());
    }

    @Test
    void actionsAreParsed() {
        assertEquals(new Action(Action.Type.JOUEUR, "vote"), Action.parse("/vote"));
        assertEquals(new Action(Action.Type.JOUEUR, "jake reglages guide"), Action.parse("[joueur] jake reglages guide"));
        assertEquals(new Action(Action.Type.CONSOLE, "say a"), Action.parse("[console] /say a"));
        assertEquals(new Action(Action.Type.RECUPERER, "palier:7"), Action.parse("[recuperer] palier:7"));
        assertEquals(new Action(Action.Type.FERMER, ""), Action.parse("[fermer]"));
        assertNull(Action.parse("  "));
        assertNull(Action.parse("[joueur] "));
        assertThrows(IllegalArgumentException.class, () -> Action.parse("[page] suivante"));
        assertThrows(IllegalArgumentException.class, () -> Action.parse("[menu]"));
        assertThrows(IllegalArgumentException.class, () -> Action.parse("[recuperer"));
    }

    @Test
    void rewardsArePickedByName() {
        List<String> pending = List.of("jour:cle", "palier:7", "accueil:demain", "jour:jaune");
        assertEquals(pending, Recompenses.pick(pending, "tout"));
        assertEquals(List.of("jour:cle", "jour:jaune"), Recompenses.pick(pending, "jour"));
        assertEquals(List.of("palier:7"), Recompenses.pick(pending, "palier"));
        assertEquals(List.of("palier:7"), Recompenses.pick(pending, "palier:7"));
        assertEquals(List.of(), Recompenses.pick(pending, "palier:14"));
        assertEquals(List.of("accueil:demain"), Recompenses.pick(pending, "demain"));
        assertEquals(List.of(), Recompenses.pick(pending, "bienvenue"));
        assertEquals(List.of("jour"), Recompenses.pick(List.of("jour"), "jour"));
        assertFalse(Recompenses.isDaily("journal"));
    }
}
