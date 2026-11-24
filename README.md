<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHGuide

*Jake, le guide du joueur : objectif du moment, programme du jour avec sa série, retour guidé et « Comment faire… ».*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

À tout moment, le joueur voit une seule chose à faire et a une raison de revenir le lendemain. Le menu de Jake (`/guide`, ou un clic sur Jake avec SHIslandJake) réunit :

| Partie | Effet |
|---|---|
| Objectif du moment | Le premier objectif de `objectifs` dont les conditions sont vraies : event qui commence bientôt, récompense à prendre, tâche presque finie, quête en cours… Un clic y emmène. |
| Programme du jour | Les tâches de `programme.taches` et une tâche tournante (`programme.tournantes`, une par jour pour tout le serveur), remises à zéro à minuit. Une tâche avance de ce que ses compteurs gagnent depuis le début de la journée, compte des valeurs égales à un texte, ou lit un nombre tel quel (`valeur`, par exemple les votes du jour faits avant la connexion). Les départs sont pris `depart-apres-connexion-secondes` après la connexion. Un joueur sans ligne dans le guide reprend la dernière venue gardée par le serveur. Quantités par palier (1 à 5). |
| Récompense et série | Un jour complet donne `programme.recompense` et fait avancer la série. Un jour raté par semaine est pardonné (joker, rendu chaque lundi). Les paliers de `serie.paliers` reviennent tous les `serie.cycle` jours. |
| Comment faire… | Un sous-menu de dix questions au plus (`aide`) : chaque bouton lance ses commandes ou explique en une ligne. |
| Retour guidé | Après `retour.jours` jours d'absence, un dialogue (ou le chat pour les anciennes versions) donne les nouveautés parues depuis la dernière venue et l'objectif du moment. |
| Barre d'event | Un event annoncé par `/shguide annonce` (par exemple dans les commandes de rappel du bot des évènements : `shguide annonce {heure} {nom} \| events`) affiche à tout le serveur une barre violette avec son compte à rebours, `annonce-barre.minutes-avant` minutes avant son début. |
| Accueil des nouveaux | Pour un joueur dont la toute première connexion a lieu avec le guide (section `accueil`), voir le tableau ci-dessous. |

L'accueil ne concerne que les nouveaux joueurs, les anciens ne voient rien de tout cela :

| Partie | Effet |
|---|---|
| Cadeau pour être resté (`accueil.rester`) | Donné après `minutes` minutes de jeu, comptées seulement quand `compte-si` est vrai (pas pendant l'AFK), avec une barre jaune et son compte à rebours en haut de l'écran. Il attend dans les récompenses de Jake. |
| Cadeau de demain (`accueil.demain`) | Annoncé le premier jour (menu de Jake, barre « Revenez demain », dialogue SHDialogs facultatif), puis mis dans les récompenses de Jake à la première connexion d'un autre jour, jusqu'à `jours` jours après l'arrivée. |
| Tutoriel (`accueil.tutoriel`) | Des étapes, chacune finie quand ses conditions `fait-si` sont vraies (passée tout de suite si le joueur l'a déjà faite). Une étape a un `clic` (menu de Jake, `/guide aller`), une `cible` (`monde x y z`) vers laquelle va la flèche, des commandes `debut` et `recompense`. Barre verte « Étape n/N » et objectif du moment avec les jetons `{tuto}`, `{tuto_texte}`, `{tuto_clic}`, `{tuto_description}`, `{tuto_n}` et `{tuto_total}`. |
| Arrivée (`accueil.arrivee`) | À sa première entrée dans le monde des îles le premier jour, le joueur est envoyé `hauteur` blocs plus haut, assis, et redescend à `vitesse-descente` blocs par seconde sous un grand titre, avec des commandes à des ticks précis. |
| Flèche (`accueil.fleche`) | Une flèche (item display) à `devant` blocs du joueur, du côté de la cible, vers le haut ou le bas quand la cible est surtout au-dessus ou au-dessous, et la distance au-dessus (▲ ou ▼ quand elle est nettement plus haute ou plus basse), montrées à lui seul, tant que l'étape en cours a une `cible`. Elle est fixe dans le monde : tourner la tête, sauter ou se baisser ne la bouge pas. Un mur la rapproche du joueur, une marche la relève. Rien n'est envoyé tant qu'elle ne change pas. `/guide fleche` la coupe ou la remet. |

La flèche sert aussi en dehors de l'accueil, à tous les joueurs : chaque entrée de la section `fleches` donne une `cible` (`monde x y z`, ou un placeholder qui le donne) et un `nom` ou un `texte`. La première cible lisible passe avant celle du tutoriel : un joueur n'a jamais deux flèches. Le mouvement, l'inclinaison et les envois se règlent dans `accueil.fleche.reglages`.

Le plugin ne connaît aucun autre plugin : tout passe par des placeholders PlaceholderAPI et des commandes écrits dans `config.yml`. Les joueurs sont vérifiés un par un, chacun une fois par `verification-secondes`, et les placeholders du guide lisent seulement le dernier calcul.

Utilisé sur : **Opale**.

## Dépendances

Requises : `PlaceholderAPI`, une base MariaDB (table `shguide_joueurs`, créée au démarrage).

## Commandes

| Commande | Permission | Effet |
|---|---|---|
| `/guide` | `shguide.use` | Menu de Jake |
| `/guide aller` | `shguide.use` | Lance le clic de l'objectif du moment (bouton « Y aller » du dialogue de retour) |
| `/guide fleche` | `shguide.use` | Coupe ou remet la flèche du tutoriel |
| `/shguide reload` | `shguide.admin.reload` | Recharge `config.yml` et `messages.yml` |
| `/shguide info <joueur>` | `shguide.admin.info` | Programme, série et dates d'un joueur |
| `/shguide annonce <minutes\|HH:MM> <texte> \| <commande>` | `shguide.admin.annonce` | Event qui commence dans `<minutes>` ou à `HH:MM` : il devient l'objectif du moment de tout le serveur `annonce-avant-minutes` avant son début, et sa barre de compte à rebours s'affiche `annonce-barre.minutes-avant` avant. L'annoncer plusieurs fois remplace l'annonce. |
| `/shguide set <joueur> <serie\|jour\|derniere-venue\|dernier-complet\|premier-jour> <valeur>` | `shguide.admin.set` | Corrige un joueur (dates au format 2026-11-23, `aucun` efface `dernier-complet` et `premier-jour`) |
| `/shguide accueil <joueur> <nouveau\|fin\|arrivee>` | `shguide.admin.accueil` | Refait du joueur un nouveau joueur (cadeaux, tutoriel et arrivée remis à zéro), finit son tutoriel, ou rejoue son arrivée là où il est |
| `/shguide reset <joueur>` | `shguide.admin.reset` | Remet le guide du joueur à zéro |

`shguide.admin` donne toutes les sous-commandes de `/shguide`. `shguide.use` est `op` par défaut : les joueurs le reçoivent par leur groupe.

## Placeholders

| Placeholder | Valeur |
|---|---|
| `%shguide_objectif%` | Texte court de l'objectif du moment |
| `%shguide_objectif_detail%` | Première ligne de sa description |
| `%shguide_programme%` / `%shguide_programme_total%` | Tâches faites / tâches du jour |
| `%shguide_serie%` / `%shguide_record%` | Série actuelle / meilleure série |
| `%shguide_joker%` | oui si le joker de la semaine est encore là |
| `%shguide_recompense_prete%` | oui si une récompense attend dans le menu |
| `%shguide_alerte%` | oui si Jake a quelque chose pour le joueur (récompense, première visite du jour) |
| `%shguide_nouveautes_nombre%` / `%shguide_nouveaute_<n>%` | Nouveautés depuis la dernière venue |
| `%shguide_menu_vu%` | oui si le joueur a ouvert le menu de Jake aujourd'hui |
| `%shguide_nouveau%` | oui pour un joueur arrivé depuis l'accueil (premier jour connu), non pour un ancien |
| `%shguide_accueil_temps%` | Temps de jeu restant avant le cadeau « rester » (m:ss), vide une fois le cadeau donné |
| `%shguide_tutoriel%` / `%shguide_tutoriel_fini%` | oui tant que le tutoriel est en cours / oui quand il est terminé |
| `%shguide_tutoriel_etape%` / `%shguide_tutoriel_total%` | Numéro de l'étape en cours / nombre d'étapes |
| `%shguide_tutoriel_texte%` | Texte de l'étape en cours, vide si le tutoriel est fini |

## Configuration

Fichiers par défaut, avec des valeurs d'exemple. Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHGuide/
├── <a href="src/main/resources/config.yml">config.yml</a>     base de données, programme, série, objectifs, retour, menu, « Comment faire… », sons, exemple d'accueil (commenté)
└── <a href="src/main/resources/messages.yml">messages.yml</a>   messages du chat, menu de Jake, objectif du moment, retour
</pre>

## Compilation

Java 25 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.
