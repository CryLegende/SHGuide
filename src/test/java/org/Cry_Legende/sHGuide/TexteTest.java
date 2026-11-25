package org.Cry_Legende.sHGuide;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TexteTest {

    private static Texte.Contexte ctx(Map<String, String> tokens, Map<String, Double> parts, Map<String, List<String>> blocks) {
        Resolver papi = t -> t.replace("%vide%", "").replace("%deux%", "a\nb").replace("%n%", "12");
        return new Texte.Contexte(tokens, parts, blocks, papi, new Texte.Bar(10, "■", "&8■"));
    }

    @Test
    void tokensAndUnknownTokens() {
        assertEquals("Salut Cry, {inconnu}", Texte.line("Salut {joueur}, {inconnu}", ctx(Map.of("joueur", "Cry"), Map.of(), Map.of())));
    }

    @Test
    void pluralFollowsTheNumber() {
        assertEquals("0 tâche", Texte.line("{n|tâche|tâches}", ctx(Map.of("n", "0"), Map.of(), Map.of())));
        assertEquals("1 tâche", Texte.line("{n|tâche|tâches}", ctx(Map.of("n", "1"), Map.of(), Map.of())));
        assertEquals("2 tâches", Texte.line("{n|tâche|tâches}", ctx(Map.of("n", "2"), Map.of(), Map.of())));
        assertEquals("1 500 blocs", Texte.line("{n|bloc|blocs}", ctx(Map.of("n", "1500"), Map.of(), Map.of())));
    }

    @Test
    void barIsFullOnlyWhenDone() {
        Map<String, Double> parts = Map.of("t", 0.999, "f", 1.0, "z", 0.0);
        assertEquals("&b" + "■".repeat(9) + "&8■", Texte.line("{barre:t:&b}", ctx(Map.of(), parts, Map.of())));
        assertEquals("&a" + "■".repeat(10), Texte.line("{barre:f:&a}", ctx(Map.of(), parts, Map.of())));
        assertEquals("&b" + "&8■".repeat(10), Texte.line("{barre:z:&b}", ctx(Map.of(), parts, Map.of())));
        assertEquals("&b" + "&8■".repeat(10), Texte.line("{barre:inconnue:&b}", ctx(Map.of(), parts, Map.of())));
    }

    @Test
    void barOfTenthsIsExact() {
        for (int i = 0; i <= 10; i++) {
            assertEquals("&b" + "■".repeat(i) + "&8■".repeat(10 - i),
                    Texte.line("{barre:t:&b}", ctx(Map.of(), Map.of("t", i / 10.0), Map.of())));
        }
    }

    @Test
    void conditionalLines() {
        List<String> t = List.of("a", "[si {n} >= 1] &e{n|nouveauté|nouveautés}", "z");
        assertEquals(List.of("a", "&e3 nouveautés", "z"), Texte.lines(t, ctx(Map.of("n", "3"), Map.of(), Map.of())));
        assertEquals(List.of("a", "z"), Texte.lines(t, ctx(Map.of("n", "0"), Map.of(), Map.of())));
    }

    @Test
    void blocksReplaceTheLineAndKeepTheirConditions() {
        Map<String, List<String>> blocks = Map.of("liste", List.of("x {n}", "[si {n} == 2] caché"), "rien", List.of());
        assertEquals(List.of("a", "x 1", "z"), Texte.lines(List.of("a", "{liste}", "{rien}", "z"), ctx(Map.of("n", "1"), Map.of(), blocks)));
    }

    @Test
    void placeholdersCanGiveSeveralLinesOrNone() {
        assertEquals(List.of("&7a", "b"), Texte.lines(List.of("&7%deux%"), ctx(Map.of(), Map.of(), Map.of())));
        assertEquals(List.of("a"), Texte.lines(List.of("a", "&7%vide%"), ctx(Map.of(), Map.of(), Map.of())));
    }

    @Test
    void blankLinesAreTidied() {
        assertEquals(List.of("", "a", "", "b"), Texte.lines(List.of("", "a", "", "[si 1 == 2] x", "", "b", "", ""), ctx(Map.of(), Map.of(), Map.of())));
    }

    @Test
    void conditionsReadTokensAndPlaceholders() {
        Texte.Contexte c = ctx(Map.of("etat", "pret"), Map.of(), Map.of());
        assertTrue(Texte.all(List.of("{etat} == pret", "%n% >= 12"), c));
        assertFalse(Texte.all(List.of("{etat} == pret", "%n% > 12"), c));
        assertTrue(Texte.all(List.of(), c));
    }

    @Test
    void stripRemovesEveryColorForm() {
        assertEquals("Bâtisseur", Texte.strip("&e&lBâtis§aseur"));
        assertEquals("Opale", Texte.strip("&#5CFFB0Opale"));
    }

    @Test
    void sentencesAreCutBetweenWordsInRowsOfSimilarLength() {
        Texte.Phrase style = new Texte.Phrase(20, "[", "-", "]");
        assertEquals(List.of("[court]"), Texte.sentence("court", style));
        assertEquals(List.of(), Texte.sentence("   ", style));
        List<String> rows = Texte.sentence("un deux trois quatre cinq six sept", style);
        assertEquals(2, rows.size());
        assertTrue(rows.get(0).startsWith("["));
        assertTrue(rows.get(1).endsWith("]"));
        assertTrue(Math.abs(rows.get(0).length() - rows.get(1).length()) <= 6, rows.toString());
        assertEquals(List.of("[anticonstitutionnellement long]"), Texte.sentence("anticonstitutionnellement long", new Texte.Phrase(40, "[", "-", "]")));
        assertEquals(List.of("[&aquatre", "-cinq]"), Texte.sentence("&aquatre cinq", new Texte.Phrase(7, "[", "-", "]")));
    }
}
