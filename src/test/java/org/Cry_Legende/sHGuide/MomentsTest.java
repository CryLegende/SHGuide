package org.Cry_Legende.sHGuide;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

    /** Une vérification du programme puis ce que Sessions.refresh en tire pour les moments (la même méthode). */
    private List<Moments.Event> check(PlayerState s) {
        int before = s.pending.size();
        Programme.Tick t = Programme.update(s, cfg, r, DAY);
        return Moments.after(s, before, t, DAY);
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
    void freeTextTokensLoseTheirPercentSignButNumbersStayUsableInAPlaceholder() {
        // le placeholder qu'un texte laisserait derrière lui ne doit jamais être lu
        Map<String, String> tokens = Map.of("joueur", "Bot", "tache", "Vendre 50%", "nom", "%player_name% Run", "etape", "100% %x%",
                "palier", "7");
        List<String> out = Moments.expand(List.of("say {tache} / {nom} / {etape} / %x_{palier}%"), tokens,
                text -> text.replace("%player_name%", "LU").replace("%x_7%", "SEPT"));
        assertEquals(List.of("say Vendre 50 / player_name Run / 100 x / SEPT"), out);
    }

    @Test
    void theTokensGivenAreNotChanged() {
        Map<String, String> tokens = Map.of("joueur", "Bot", "tache", "50%");
        Moments.expand(List.of("say {tache}"), tokens, text -> text);
        assertEquals("50%", tokens.get("tache"));
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
        assertEquals(10, cfg.momentsPerSecond());
    }

    @Test
    void momentsPerSecondIsReadAndMustBeAWholeNumberOfOneAtLeast() {
        YamlConfiguration c = SettingsTest.example();
        c.set("moments-par-seconde", 25);
        assertEquals(25, Settings.read(c).momentsPerSecond());
        c.set("moments-par-seconde", null);
        assertEquals(10, Settings.read(c).momentsPerSecond());
        for (Object bad : List.of(0, -3, "dix", 2.5)) {
            YamlConfiguration d = SettingsTest.example();
            d.set("moments-par-seconde", bad);
            assertTrue(assertThrows(IllegalArgumentException.class, () -> Settings.read(d)).getMessage().contains("moments-par-seconde"), String.valueOf(bad));
        }
    }

    @Test
    void atStartupABadMomentsSectionIsIgnoredAndNamedWhileTheRestIsRead() {
        YamlConfiguration c = SettingsTest.example();
        c.set("moments.tache-finie", List.of("say x"));
        c.set("moments.retour", List.of("say bonjour"));
        c.set("moments-par-seconde", 3);
        List<String> errors = new ArrayList<>();
        Settings s = Settings.read(c, errors);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("moments.tache-finie"), errors.get(0));
        assertTrue(s.moments().isEmpty(), "aucun moment ne part");
        assertEquals(10, s.momentsPerSecond());
        assertEquals(2, s.tasks().size());
        assertEquals(Set.of(7, 14, 21, 30), s.stepDays());
        // un reload (sans liste d'erreurs) refuse le même fichier
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Settings.read(c)).getMessage().contains("moments.tache-finie"));
    }

    @Test
    void atStartupABadSpeedIsIgnoredToo() {
        YamlConfiguration c = SettingsTest.example();
        c.set("moments.retour", List.of("say bonjour"));
        c.set("moments-par-seconde", "vite");
        List<String> errors = new ArrayList<>();
        Settings s = Settings.read(c, errors);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("moments-par-seconde"), errors.get(0));
        assertTrue(s.moments().isEmpty());
    }

    @Test
    void aValidSectionAtStartupRaisesNoError() {
        YamlConfiguration c = SettingsTest.example();
        c.set("moments.retour", List.of("say bonjour"));
        List<String> errors = new ArrayList<>();
        assertEquals(List.of("say bonjour"), Settings.read(c, errors).moments().get("retour"));
        assertTrue(errors.isEmpty());
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

    // ------------------------------------------------------------------ récompense propre à une tâche

    /** L'exemple du jar, avec une récompense sur « niveau » (fixe) et sur « sauts » (tournante, la dernière de la journée dans ces tests). */
    private static YamlConfiguration withTaskRewards() {
        YamlConfiguration c = SettingsTest.example();
        c.set("programme.taches.niveau.recompense", List.of("[console] give {joueur} diamond 1", "say %player_name% bravo"));
        c.set("programme.tournantes.sauts.recompense", List.of("[console] give {joueur} emerald 2"));
        return c;
    }

    private static List<String> ids(List<Programme.TaskView> views) {
        return views.stream().map(v -> v.def().id()).toList();
    }

    /** Une vérification du programme puis les tâches dont la récompense part (la même méthode que Sessions.refresh). */
    private List<String> rewardsOf(PlayerState s) {
        return ids(Moments.rewarded(Programme.update(s, cfg, r, DAY)));
    }

    @Test
    void aTaskWithARewardGivesItsCommandsTheMomentItIsDone() {
        cfg = Settings.read(withTaskRewards());
        PlayerState s = PlayerState.fresh("Bot");
        Programme.update(s, cfg, r, DAY);
        values.put("%player_level%", "11");
        List<Programme.TaskView> rewarded = Moments.rewarded(Programme.update(s, cfg, r, DAY));
        assertEquals(List.of("niveau"), ids(rewarded));
        assertEquals(List.of("[console] give Bot diamond 1", "say NOM bravo"),
                Moments.expand(rewarded.get(0).def().reward(), Map.of("joueur", "Bot"), text -> text.replace("%player_name%", "NOM")));
        assertTrue(rewardsOf(s).isEmpty(), "pas une deuxième fois à la vérification suivante");
    }

    @Test
    void theLastTaskOfTheDayGivesItsRewardToo() {
        cfg = Settings.read(withTaskRewards());
        PlayerState s = PlayerState.fresh("Bot");
        Programme.update(s, cfg, r, DAY);
        values.put("%player_level%", "11");
        values.put("%player_gamemode%", "CREATIVE");
        assertEquals(List.of("niveau"), rewardsOf(s));
        values.put("%statistic_jump%", "103");
        Programme.Tick last = Programme.update(s, cfg, r, DAY);
        assertTrue(last.newlyCompleted());
        assertEquals(List.of("sauts"), ids(Moments.rewarded(last)));
        assertEquals(List.of("[console] give {joueur} emerald 2"), Moments.rewarded(last).get(0).def().reward());
        assertTrue(rewardsOf(s).isEmpty());
    }

    @Test
    void theLastTaskIsStillOnlyTheProgramMomentButItsRewardGoesAnyway() {
        cfg = Settings.read(withTaskRewards());
        PlayerState s = PlayerState.fresh("Bot");
        Programme.update(s, cfg, r, DAY);
        values.put("%player_level%", "11");
        values.put("%player_gamemode%", "CREATIVE");
        values.put("%statistic_jump%", "103");
        Programme.Tick t = Programme.update(s, cfg, r, DAY);
        assertEquals(List.of("programme-complet"), names(Moments.after(t, List.of(), 1, 0)));
        assertEquals(List.of("niveau", "sauts"), ids(Moments.rewarded(t)), "dans l'ordre du programme");
    }

    @Test
    void aTaskWithoutARewardGivesNothingNew() {
        cfg = Settings.read(withTaskRewards());
        PlayerState s = PlayerState.fresh("Bot");
        Programme.update(s, cfg, r, DAY);
        values.put("%player_gamemode%", "CREATIVE");
        Programme.Tick t = Programme.update(s, cfg, r, DAY);
        assertEquals(List.of("creatif"), ids(t.newlyDone()));
        assertTrue(Moments.rewarded(t).isEmpty());
        // et sans aucune récompense dans la config, comme avant
        cfg = Settings.read(SettingsTest.example());
        assertTrue(cfg.tasksFor(DAY).stream().allMatch(d -> d.reward().isEmpty()));
        PlayerState plain = PlayerState.fresh("Bot");
        Programme.update(plain, cfg, r, DAY);
        values.put("%player_level%", "11");
        Programme.Tick done = Programme.update(plain, cfg, r, DAY);
        assertFalse(done.newlyDone().isEmpty());
        assertTrue(Moments.rewarded(done).isEmpty());
    }

    @Test
    void aRewardNeverRunsTwiceInTheSameDay() {
        cfg = Settings.read(withTaskRewards());
        PlayerState s = PlayerState.fresh("Bot");
        Programme.update(s, cfg, r, DAY);
        values.put("%player_level%", "11");
        assertEquals(List.of("niveau"), rewardsOf(s));
        // le compteur redescend (le départ est repris) puis remonte : la tâche reste faite
        values.put("%player_level%", "3");
        assertTrue(rewardsOf(s).isEmpty());
        values.put("%player_level%", "9");
        assertTrue(rewardsOf(s).isEmpty());
        // reconnexion : l'état enregistré puis relu
        com.google.gson.Gson gson = new com.google.gson.Gson();
        PlayerState back = gson.fromJson(gson.toJson(s), PlayerState.class).normalize();
        assertTrue(rewardsOf(back).isEmpty());
        // /shguide reload : la config relue, l'état du joueur gardé
        cfg = Settings.read(withTaskRewards());
        assertTrue(rewardsOf(back).isEmpty());
        // le lendemain, elle repart une fois
        Programme.update(back, cfg, r, DAY.plusDays(1));
        values.put("%player_level%", "10");
        assertEquals(List.of("niveau"), ids(Moments.rewarded(Programme.update(back, cfg, r, DAY.plusDays(1)))));
        assertTrue(Moments.rewarded(Programme.update(back, cfg, r, DAY.plusDays(1))).isEmpty());
    }

    @Test
    void aTaskAlreadyDoneAtLoginGivesItsRewardOnce() {
        YamlConfiguration c = withTaskRewards();
        c.set("programme.taches.vote.texte", "Voter une fois");
        c.set("programme.taches.vote.valeur", "%votes_jour%");
        c.set("programme.taches.vote.objectif", List.of(1));
        c.set("programme.taches.vote.recompense", List.of("[console] give {joueur} gold_ingot 1"));
        cfg = Settings.read(c);
        values.put("%votes_jour%", "1");
        PlayerState s = PlayerState.fresh("Bot");
        // juste après la connexion, avant les départs : une tâche « valeur » est déjà faite
        assertEquals(List.of("vote"), ids(Moments.rewarded(Programme.update(s, cfg, r, DAY, false))));
        assertTrue(Moments.rewarded(Programme.update(s, cfg, r, DAY, false)).isEmpty());
        assertTrue(Moments.rewarded(Programme.update(s, cfg, r, DAY, true)).isEmpty());
    }

    @Test
    void theTaskThatReplacedAnotherGivesItsOwnReward() {
        YamlConfiguration c = withTaskRewards();
        c.set("programme.taches.creatif.si", List.of("%quete_active% != /"));
        c.set("programme.taches.creatif.sinon", "tournante");
        c.set("programme.taches.creatif.recompense", List.of("[console] give {joueur} stone 1"));
        c.set("programme.tournantes.peche.texte", "Pêcher {objectif} poissons");
        c.set("programme.tournantes.peche.compteurs", List.of("%statistic_fish_caught%"));
        c.set("programme.tournantes.peche.objectif", List.of(2));
        c.set("programme.tournantes.peche.recompense", List.of("[console] give {joueur} cod 1"));
        cfg = Settings.read(c);
        values.put("%quete_active%", "/");
        values.put("%statistic_fish_caught%", "5");
        PlayerState s = PlayerState.fresh("Bot");
        Programme.update(s, cfg, r, DAY);
        String alt = cfg.alternateFor(DAY).id();
        assertEquals(alt, s.choices.get("creatif"));
        values.put("%statistic_fish_caught%", "8");
        values.put("%statistic_jump%", "110");
        values.put("%player_gamemode%", "CREATIVE");
        List<Programme.TaskView> rewarded = Moments.rewarded(Programme.update(s, cfg, r, DAY));
        // la tâche du jour et celle qui remplace « creatif » : chacune sa récompense, « creatif » (absente) aucune
        assertEquals(Set.of("sauts", "peche"), Set.copyOf(ids(rewarded)));
        assertEquals(2, rewarded.size());
        assertEquals(cfg.rotatingById(alt).reward(), rewarded.stream().filter(v -> v.def().id().equals(alt)).findFirst().orElseThrow().def().reward());
    }

    @Test
    void aTaskRewardIsReadForFixedAndRotatingTasksAndAbsentMeansNone() {
        Settings s = Settings.read(withTaskRewards());
        assertEquals(List.of("[console] give {joueur} diamond 1", "say %player_name% bravo"), s.tasks().get(0).reward());
        assertTrue(s.tasks().get(1).reward().isEmpty());
        assertEquals(List.of("[console] give {joueur} emerald 2"), s.rotating().get(0).reward());
        YamlConfiguration c = withTaskRewards();
        c.set("programme.taches.niveau.recompense", List.of());
        assertTrue(Settings.read(c).tasks().get(0).reward().isEmpty());
        c.set("programme.taches.niveau.recompense", null);
        assertTrue(Settings.read(c).tasks().get(0).reward().isEmpty());
    }

    @Test
    void aBadTaskRewardIsRefusedAndNamed() {
        YamlConfiguration c = SettingsTest.example();
        c.set("programme.taches.niveau.recompense", "[console] give {joueur} diamond 1");
        String notAList = assertThrows(IllegalArgumentException.class, () -> Settings.read(c)).getMessage();
        assertTrue(notAList.startsWith("programme.taches.niveau.recompense"), notAList);
        YamlConfiguration d = SettingsTest.example();
        d.set("programme.tournantes.sauts.recompense", List.of("say x", 5));
        String notText = assertThrows(IllegalArgumentException.class, () -> Settings.read(d)).getMessage();
        assertTrue(notText.startsWith("programme.tournantes.sauts.recompense[1]"), notText);
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
    void anAnnouncementIsToldToAFewPlayersPerSecondAndEveryoneGetsTheirTurn() {
        List<UUID> online = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            online.add(UUID.randomUUID());
        }
        Map<UUID, String> told = new HashMap<>();
        List<UUID> first = AnnonceBarre.toTell(online, id -> id, told, "TNT@10", 10);
        assertEquals(online.subList(0, 10), first);
        assertEquals(10, told.size(), "seuls ceux qui sont partis sont notés");
        assertEquals(online.subList(10, 20), AnnonceBarre.toTell(online, id -> id, told, "TNT@10", 10));
        assertEquals(online.subList(20, 25), AnnonceBarre.toTell(online, id -> id, told, "TNT@10", 10));
        assertTrue(AnnonceBarre.toTell(online, id -> id, told, "TNT@10", 10).isEmpty(), "chacun une seule fois");
    }

    @Test
    void aPlayerWhoArrivesLateGetsTheAnnouncementAndANewAnnouncementIsToldToAllAgain() {
        List<UUID> online = new ArrayList<>(List.of(UUID.randomUUID(), UUID.randomUUID()));
        Map<UUID, String> told = new HashMap<>();
        assertEquals(2, AnnonceBarre.toTell(online, id -> id, told, "TNT@10", 10).size());
        UUID late = UUID.randomUUID();
        online.add(late);
        assertEquals(List.of(late), AnnonceBarre.toTell(online, id -> id, told, "TNT@10", 10));
        assertEquals(3, AnnonceBarre.toTell(online, id -> id, told, "Block Party@12", 10).size());
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
