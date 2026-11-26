package org.Cry_Legende.sHGuide;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MomentsTest {

    private static final LocalDate DAY = LocalDate.parse("2026-11-23");

    private Settings cfg;
    private final Map<String, String> values = new HashMap<>();
    private final Resolver r = text -> {
        String out = text;
        for (var e : values.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    };

    @BeforeEach
    void setUp() {
        cfg = Settings.read(SettingsTest.example());
        values.put("%player_level%", "10");
        values.put("%player_gamemode%", "SURVIVAL");
        values.put("%statistic_jump%", "100");
    }

    /** Ce que Sessions.refresh envoie aux moments : la vérification, les cadeaux qu'elle a ajoutés et la série. */
    private List<Moments.Event> check(PlayerState s) {
        int before = s.pending.size();
        Programme.Tick t = Programme.update(s, cfg, r, DAY);
        List<String> arrived = List.copyOf(s.pending.subList(before, s.pending.size()));
        return Moments.after(t, arrived, t.newlyCompleted() ? Streak.display(s, DAY) : 0, s.pending.size());
    }

    private static List<String> names(List<Moments.Event> events) {
        return events.stream().map(Moments.Event::name).toList();
    }

    // ------------------------------------------------------------------ jetons

    @Test
    void tokensAreReplacedBeforePlaceholders() {
        // « %{jeton}% » devient un placeholder une fois le jeton remplacé : les jetons passent en premier
        Map<String, String> tokens = Map.of("joueur", "Bot", "tache", "Sauter", "qui", "player_name");
        List<String> out = Moments.expand(List.of("[console] say {joueur} {tache} %{qui}%"), tokens,
                text -> text.replace("%player_name%", "NOM"));
        assertEquals(List.of("[console] say Bot Sauter NOM"), out);
    }

    @Test
    void unknownTokenStaysAndBlankLinesVanish() {
        List<String> out = Moments.expand(List.of("say {joueur} {inconnu}", "", "   ", "  [console] list  "), Map.of("joueur", "Bot"), text -> text);
        assertEquals(List.of("say Bot {inconnu}", "[console] list"), out);
    }

    @Test
    void placeholderResultIsTrimmedAndAnEmptyOneDropsTheLine() {
        List<String> out = Moments.expand(List.of("%rien%", "say {joueur}"), Map.of("joueur", "Bot"), text -> text.replace("%rien%", " "));
        assertEquals(List.of("say Bot"), out);
    }

    @Test
    void anEmptyListGivesNothing() {
        assertTrue(Moments.expand(List.of(), Map.of("joueur", "Bot"), text -> text).isEmpty());
    }

    // ------------------------------------------------------------------ configuration

    @Test
    void exampleConfigListsEveryMomentEmpty() {
        Map<String, List<String>> moments = cfg.moments();
        assertEquals(Set.copyOf(Moments.NAMES), moments.keySet());
        assertEquals(9, Moments.NAMES.size());
        assertTrue(moments.values().stream().allMatch(List::isEmpty));
    }

    @Test
    void absentSectionOrListIsEmpty() {
        YamlConfiguration c = SettingsTest.example();
        c.set("moments", null);
        assertTrue(Settings.read(c).moments().isEmpty());
        YamlConfiguration d = SettingsTest.example();
        d.set("moments.retour", null);
        assertTrue(Settings.read(d).moments().getOrDefault("retour", List.of()).isEmpty());
    }

    @Test
    void commandsAreRead() {
        YamlConfiguration c = SettingsTest.example();
        c.set("moments.tache-faite", List.of("[console] say {joueur} {tache}", "say bravo"));
        assertEquals(List.of("[console] say {joueur} {tache}", "say bravo"), Settings.read(c).moments().get("tache-faite"));
    }

    @Test
    void unknownMomentIsRefusedAndNamed() {
        YamlConfiguration c = SettingsTest.example();
        c.set("moments.tache-finie", List.of("say x"));
        String error = assertThrows(IllegalArgumentException.class, () -> Settings.read(c)).getMessage();
        assertTrue(error.contains("moments.tache-finie"), error);
        assertTrue(error.contains("tache-faite"), error);
    }

    @Test
    void aValueThatIsNotAListIsRefusedAndNamed() {
        YamlConfiguration c = SettingsTest.example();
        c.set("moments.palier", "[console] say x");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Settings.read(c)).getMessage().contains("moments.palier"));
        YamlConfiguration d = SettingsTest.example();
        d.set("moments", List.of("say x"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Settings.read(d)).getMessage().startsWith("moments"));
    }

    @Test
    void aCommandThatIsNotTextIsRefusedAndNamed() {
        YamlConfiguration c = SettingsTest.example();
        c.set("moments.annonce", List.of("say x", 5));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Settings.read(c)).getMessage().contains("moments.annonce[1]"));
    }

    // ------------------------------------------------------------------ tâche faite, programme complet, palier, cadeau prêt

    @Test
    void aTaskDoneIsAMomentWithItsCounts() {
        PlayerState s = PlayerState.fresh("Bot");
        Programme.update(s, cfg, r, DAY);
        values.put("%player_level%", "11");
        List<Moments.Event> events = check(s);
        assertEquals(List.of("tache-faite"), names(events));
        Map<String, String> tokens = events.get(0).tokens();
        assertEquals("Gagner un niveau d'XP", tokens.get("tache"));
        assertEquals("1", tokens.get("faites"));
        assertEquals("3", tokens.get("total"));
        assertEquals("2", tokens.get("restantes"));
        assertTrue(check(s).isEmpty(), "rien de plus à la vérification suivante");
    }

    @Test
    void twoTasksInTheSameCheckAreTwoMomentsWithTheStateAfterward() {
        PlayerState s = PlayerState.fresh("Bot");
        Programme.update(s, cfg, r, DAY);
        values.put("%player_level%", "11");
        values.put("%player_gamemode%", "CREATIVE");
        List<Moments.Event> events = check(s);
        assertEquals(List.of("tache-faite", "tache-faite"), names(events));
        assertEquals("2", events.get(0).tokens().get("faites"));
        assertEquals("1", events.get(1).tokens().get("restantes"));
    }

    @Test
    void theLastTaskIsTheProgramMomentAndTheGiftIsReady() {
        PlayerState s = PlayerState.fresh("Bot");
        Programme.update(s, cfg, r, DAY);
        values.put("%player_level%", "11");
        values.put("%player_gamemode%", "CREATIVE");
        check(s);
        values.put("%statistic_jump%", "103");
        List<Moments.Event> events = check(s);
        assertEquals(List.of("programme-complet", "cadeau-pret"), names(events));
        assertEquals("1", events.get(0).tokens().get("serie"));
        assertEquals("1", events.get(1).tokens().get("cadeaux"));
    }

    @Test
    void theLastTaskFinishedWithAnotherOneIsStillOnlyTheProgramMoment() {
        PlayerState s = PlayerState.fresh("Bot");
        Programme.update(s, cfg, r, DAY);
        values.put("%player_level%", "11");
        values.put("%player_gamemode%", "CREATIVE");
        values.put("%statistic_jump%", "103");
        assertEquals(List.of("programme-complet", "cadeau-pret"), names(check(s)));
    }

    @Test
    void aSeriesStepIsAMomentBetweenTheProgramAndTheGift() {
        PlayerState s = PlayerState.fresh("Bot");
        s.streak = 6;
        s.lastComplete = DAY.minusDays(1).toString();
        Programme.update(s, cfg, r, DAY);
        values.put("%player_level%", "11");
        values.put("%player_gamemode%", "CREATIVE");
        values.put("%statistic_jump%", "103");
        List<Moments.Event> events = check(s);
        assertEquals(List.of("programme-complet", "palier", "cadeau-pret"), names(events));
        assertEquals("7", events.get(0).tokens().get("serie"));
        assertEquals("7", events.get(1).tokens().get("palier"));
        assertEquals("2", events.get(2).tokens().get("cadeaux"));
    }

    @Test
    void aCheckWithNothingNewIsNoMoment() {
        PlayerState s = PlayerState.fresh("Bot");
        assertTrue(check(s).isEmpty());
        assertTrue(check(s).isEmpty());
    }

    @Test
    void giftsAlreadyWaitingAreNotAnnouncedAgain() {
        PlayerState s = PlayerState.fresh("Bot");
        s.pending.add("jour");
        Programme.update(s, cfg, r, DAY);
        assertTrue(check(s).isEmpty());
    }

    // ------------------------------------------------------------------ cadeau pris, retour, annonce

    @Test
    void everyGiftHasItsKind() {
        assertEquals("jour", Recompenses.sorte("jour"));
        assertEquals("jour", Recompenses.sorte("jour:diamant"));
        assertEquals("palier", Recompenses.sorte("palier:7"));
        assertEquals("bienvenue", Recompenses.sorte("accueil:rester"));
        assertEquals("demain", Recompenses.sorte("accueil:demain"));
        assertEquals("", Recompenses.sorte("autre"));
    }

    @Test
    void returnTellsHowManyDaysAway() {
        assertEquals(5, Retour.daysAway("2026-11-18", DAY));
        assertTrue(Retour.isReturn("2026-11-18", DAY, 3));
        assertFalse(Retour.isReturn("2026-11-22", DAY, 3));
    }

    @Test
    void anAnnouncementIsANameAndAStartMinute() {
        Annonces.Active a = new Annonces.Active("TNT Run", "minijeu join", 600_000L);
        assertEquals(a.key(), new Annonces.Active("TNT Run", "autre clic", 600_000L + 30_000L).key());
        assertNotEquals(a.key(), new Annonces.Active("TNT Run", "minijeu join", 660_000L).key());
        assertNotEquals(a.key(), new Annonces.Active("Block Party", "minijeu join", 600_000L).key());
    }

    @Test
    void minutesLeftRoundUpAndStopAtZero() {
        Annonces.Active a = new Annonces.Active("TNT Run", "", 10 * 60_000L);
        assertEquals(7, Annonces.minutesLeft(a, 3 * 60_000L));
        assertEquals(8, Annonces.minutesLeft(a, 2 * 60_000L));
        assertEquals(9, Annonces.minutesLeft(a, 2 * 60_000L - 1));
        assertEquals(1, Annonces.minutesLeft(a, 10 * 60_000L - 1));
        assertEquals(0, Annonces.minutesLeft(a, 10 * 60_000L));
        assertEquals(0, Annonces.minutesLeft(a, 12 * 60_000L));
    }
}
