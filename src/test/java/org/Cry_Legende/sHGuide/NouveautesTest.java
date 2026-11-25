package org.Cry_Legende.sHGuide;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NouveautesTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-11-25");

    private static Nouveautes news(String yaml) {
        return Nouveautes.read(YamlConfiguration.loadConfiguration(new StringReader(yaml)));
    }

    private static final String THREE = """
            vieille:
              date: "2026-10-01"
              titre: "Vieille"
            vente:
              date: 2026-11-24
              titre: "Vente"
              commande: "/sell"
            deco:
              date: "2026-11-24"
              titre: "Deco"
              clic:
                - "[menu] aide"
            noel:
              date: "2026-12-01"
              titre: "Noël"
            """;

    private static List<String> ids(List<Nouveautes.Entree> l) {
        return l.stream().map(Nouveautes.Entree::id).toList();
    }

    @Test
    void newestFirstThenFileOrderAndFutureHidden() {
        assertEquals(List.of("vente", "deco", "vieille"), ids(news(THREE).out(TODAY)));
        assertEquals(List.of("noel", "vente", "deco", "vieille"), ids(news(THREE).out(LocalDate.parse("2026-12-01"))));
    }

    @Test
    void commandIsTheDefaultClick() {
        Nouveautes n = news(THREE);
        assertEquals(List.of("/sell"), n.out(TODAY).get(0).click());
        assertEquals(List.of("[menu] aide"), n.out(TODAY).get(1).click());
        assertEquals(List.of(), n.out(TODAY).get(2).click());
    }

    @Test
    void unreadSkipsOldReadAndBeforeArrival() {
        Nouveautes n = news(THREE);
        PlayerState s = PlayerState.fresh("Cry");
        assertEquals(List.of("vente", "deco"), ids(n.unread(s, TODAY, 30)));
        assertEquals(List.of("vente", "deco", "vieille"), ids(n.unread(s, TODAY, 90)));
        s.firstDay = "2026-11-24";
        assertEquals(List.of(), ids(n.unread(s, TODAY, 30)));
        s.firstDay = "2026-11-23";
        assertTrue(n.markRead(s, List.of(n.out(TODAY).get(0))));
        assertEquals(List.of("deco"), ids(n.unread(s, TODAY, 30)));
        assertFalse(n.markRead(s, List.of(n.out(TODAY).get(0))));
    }

    @Test
    void removedNewsAreForgotten() {
        PlayerState s = PlayerState.fresh("Cry");
        s.newsRead.add("retiree");
        s.newsRead.add("vente");
        assertTrue(news(THREE).markRead(s, List.of()));
        assertEquals(java.util.Set.of("vente"), s.newsRead);
    }

    @Test
    void sinceLastVisit() {
        assertEquals(List.of("vente", "deco"), ids(news(THREE).since("2026-11-20", TODAY)));
        assertEquals(List.of(), ids(news(THREE).since("2026-11-24", TODAY)));
        assertEquals(List.of(), ids(news(THREE).since(null, TODAY)));
    }

    @Test
    void errorsNameTheEntry() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> news("a:\n  date: \"24/11\"\n  titre: x\n")).getMessage().contains("a.date"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> news("a:\n  date: \"2026-11-24\"\n")).getMessage().contains("a.titre"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> news("a:\n  date: \"2026-11-24\"\n  titre: x\n  clic: \"[truc] y\"\n")).getMessage().contains("a.clic"));
    }

    @Test
    void emptyFileMeansNoNews() {
        assertEquals(List.of(), news("").out(TODAY));
    }
}
