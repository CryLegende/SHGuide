package org.Cry_Legende.sHGuide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** État du guide d'un joueur, enregistré en JSON dans shguide_joueurs. Dates au format 2026-11-23. */
public final class PlayerState {

    /** Pseudo, pour lire la table à la main. */
    public String name;
    /** Jour du programme en cours. */
    public String day;
    /** Palier du jour (1 à 5), 0 tant qu'il n'est pas lu. */
    public int palier;
    /** Tâche tournante du jour. */
    public String rotating;
    /** Valeur de chaque compteur au début de la journée (tâche#numéro du compteur). */
    public Map<String, Double> baselines = new HashMap<>();
    /** Tâches faites aujourd'hui. */
    public Set<String> done = new HashSet<>();
    /** Programme du jour complet. */
    public boolean completed;
    /** Dernier jour complet. */
    public String lastComplete;
    public int streak;
    public int best;
    /** Semaines dont le joker est utilisé (2026-W48). */
    public Set<String> jokerWeeks = new HashSet<>();
    /** Récompenses à récupérer dans le menu : « jour » ou « palier:7 ». */
    public List<String> pending = new ArrayList<>();
    /** Dernier jour de connexion. */
    public String lastSeen;
    /** Dernier jour où le menu a été ouvert. */
    public String alertSeen;

    static PlayerState fresh(String name) {
        PlayerState s = new PlayerState();
        s.name = name;
        return s;
    }

    /** Gson laisse null les collections absentes d'un ancien JSON. */
    PlayerState normalize() {
        if (baselines == null) {
            baselines = new HashMap<>();
        }
        if (done == null) {
            done = new HashSet<>();
        }
        if (jokerWeeks == null) {
            jokerWeeks = new HashSet<>();
        }
        if (pending == null) {
            pending = new ArrayList<>();
        }
        return this;
    }
}
