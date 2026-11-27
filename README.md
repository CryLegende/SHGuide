<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHGuide

*Jake, le guide du joueur : objectif du moment, tâches du jour, récompenses et série, nouveautés, retour guidé, accueil des nouveaux.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

À tout moment, le joueur voit une seule chose à faire et a une raison de revenir le lendemain. Jake (`/guide`, ou un clic sur Jake avec SHIslandJake) ouvre un accueil court, et chaque sujet a son écran :

```
accueil ─┬─ objectif du moment (un clic y emmène)
         ├─ taches          les tâches du jour et leur avancement
         ├─ recompenses     cadeau du jour, série, paliers, cadeaux des nouveaux joueurs
         ├─ nouveautes      ce qui a changé, signalé tant que le joueur ne l'a pas vu
         ├─ premiers-pas    les étapes du tutoriel (nouveaux joueurs)
         └─ aide            « Comment faire ? »
```

Chaque écran s'ouvre aussi directement (`/guide taches`, `/guide recompenses`…), depuis le chat, un dialogue ou un autre menu. Les écrans, leurs cases, leurs textes et leurs clics sont tous dans `menus.yml`.

| Partie | Effet |
|---|---|
| Objectif du moment | Le premier objectif de `objectifs` dont les conditions sont vraies : event qui commence bientôt, récompense à prendre, tâche presque finie, quête en cours… Un clic y emmène. |
| Tâches du jour | Les tâches de `programme.taches` et une tâche tournante (`programme.tournantes`, une par jour pour tout le serveur), remises à zéro à minuit. Une tâche avance de ce que ses compteurs gagnent depuis le début de la journée, compte des valeurs égales à un texte, ou lit un nombre tel quel (`valeur`, par exemple les votes du jour faits avant la connexion). Les départs sont pris `depart-apres-connexion-secondes` après la connexion. Un joueur sans ligne dans le guide reprend la dernière venue gardée par le serveur. Quantités par palier (1 à 5). Une tâche, fixe ou tournante, peut avoir sa propre récompense (`recompense`, une liste de commandes écrite comme `programme.recompense`) : elle part une seule fois par jour, au moment où la tâche est faite, même si c'est la dernière du jour, avec `{joueur}` et les placeholders du joueur. Ce qu'elle donne s'écrit dans la `description` de la tâche. |
| Récompense et série | Un jour complet donne `programme.recompense` et fait avancer la série. Un jour raté par semaine est pardonné (joker, rendu chaque lundi). Les paliers de `serie.paliers` reviennent tous les `serie.cycle` jours. |
| Récompenses | Chaque cadeau se prend seul, ou tous d'un coup : cadeau du jour, palier de série, cadeau de bienvenue, cadeau du lendemain. Un clic répété ne redonne rien. |
| Nouveautés | Une entrée par nouveauté dans `nouveautes.yml` : `date` et `titre` suffisent, `texte` (une phrase simple, coupée et habillée par `phrase` de `menus.yml`), `commande`, `icone` et `clic` sont facultatifs. Elle brille tant que le joueur ne l'a pas vue (`nouveautes-recentes-jours`), et le dialogue de retour cite celles sorties depuis la dernière venue. |
| Liens | Un bouton lance une commande, une commande ouvre un écran, et le Retour d'un menu ouvert par Jake ramène à l'écran d'où vient le joueur (`/guide retour <commande>`). Les messages du chat se cliquent (`clics` de `messages.yml`). |
| Retour guidé | Après `retour.jours` jours d'absence, un dialogue (ou le chat pour les anciennes versions) donne le cadeau qui attend, les nouveautés sorties depuis la dernière venue et l'objectif du moment (`retour.lignes` de `messages.yml`, `%shguide_retour%`). |
| Barre d'event | Un event annoncé par `/shguide annonce` (par exemple dans les commandes de rappel du bot des évènements : `shguide annonce {heure} {nom} \| events`) affiche à tout le serveur une barre violette avec son compte à rebours, `annonce-barre.minutes-avant` minutes avant son début. |
| Moments | À chaque moment du guide (tâche faite, cadeau pris, étape du tutoriel…), une liste de commandes de `moments` est lancée pour le joueur concerné, voir [Moments](#moments). |
| Accueil des nouveaux | Pour un joueur dont la toute première connexion a lieu avec le guide (section `accueil`), voir le tableau ci-dessous. |

L'accueil ne concerne que les nouveaux joueurs, les anciens ne voient rien de tout cela :

| Partie | Effet |
|---|---|
| Cadeau pour être resté (`accueil.rester`) | Donné après `minutes` minutes de jeu, comptées seulement quand `compte-si` est vrai (pas pendant l'AFK), avec une barre jaune et son compte à rebours en haut de l'écran. Il attend dans les récompenses de Jake. |
| Cadeau de demain (`accueil.demain`) | Annoncé le premier jour (menu de Jake, barre « Revenez demain », dialogue SHDialogs facultatif), puis mis dans les récompenses de Jake à la première connexion d'un autre jour, jusqu'à `jours` jours après l'arrivée. |
| Tutoriel (`accueil.tutoriel`) | Des étapes, chacune finie quand ses conditions `fait-si` sont vraies (passée tout de suite si le joueur l'a déjà faite). Une étape a un `clic` (menu de Jake, `/guide aller`), une `cible` (`monde x y z`) vers laquelle va la flèche, des commandes `debut` et `recompense`. La barre verte « Étape n/N » se remplit à chaque action (`progres` sur `sur`, ou `progres-si`), reste pleine un moment quand l'étape est faite (`barre-pleine-ticks`) et repart de 0 à la suivante. Objectif du moment avec les jetons `{tuto}`, `{tuto_texte}`, `{tuto_clic}`, `{tuto_description}`, `{tuto_n}` et `{tuto_total}`. |
| Arrivée (`accueil.arrivee`) | À sa première entrée dans le monde des îles le premier jour, le joueur est envoyé `hauteur` blocs plus haut, assis, et redescend à `vitesse-descente` blocs par seconde sous un grand titre, avec des commandes à des ticks précis. Avec `titre-seul`, les titres des autres plugins ne s'affichent pas jusqu'à la fin de la descente (demande packetevents). |
| Flèche (`accueil.fleche`) | Une flèche (item display) à `devant` blocs du joueur, du côté de la cible, vers le haut ou le bas quand la cible est surtout au-dessus ou au-dessous, et la distance au-dessus (▲ ou ▼ quand elle est nettement plus haute ou plus basse), montrées à lui seul, tant que l'étape en cours a une `cible`. Elle est fixe dans le monde : tourner la tête, sauter ou se baisser ne la bouge pas, et un élan donné par le serveur (double saut) est compté dès son départ, elle n'est donc jamais rattrapée. Un mur la rapproche du joueur, une marche la relève. Rien n'est envoyé tant qu'elle ne change pas. `/guide fleche` la coupe ou la remet. |

La flèche sert aussi en dehors de l'accueil, à tous les joueurs : chaque entrée de la section `fleches` donne une `cible` (`monde x y z`, ou un placeholder qui le donne) et un `nom` ou un `texte`. Une cible de plusieurs points (`monde x y z x y z …`) est un chemin : la flèche avance dessus avec le joueur et montre toujours un endroit devant lui, sans se retourner quand il dépasse un point. `fin` est le texte laissé un moment quand la cible disparaît, pendant que la flèche s'efface. La première cible lisible passe avant celle du tutoriel : un joueur n'a jamais deux flèches. Le mouvement, l'inclinaison et les envois se règlent dans `accueil.fleche.reglages`.

Le plugin ne connaît aucun autre plugin : tout passe par des placeholders PlaceholderAPI et des commandes écrits dans `config.yml`. Les joueurs sont vérifiés un par un, chacun une fois par `verification-secondes`, et les placeholders du guide lisent seulement le dernier calcul. Une tâche faite n'est plus relue jusqu'à minuit, et les placeholders coûteux (`placeholders-lents`) sont lus moins souvent.

Utilisé sur : **Opale**.

## Dépendances

Requises : `PlaceholderAPI`, une base MariaDB (table `shguide_joueurs`, créée au démarrage). Optionnelle : `packetevents` (la flèche, envoyée en paquets ; sans lui, pas de flèche). Les placeholders des cibles (`fleches`) sont lus hors du fil principal.

## Commandes

| Commande | Permission | Effet |
|---|---|---|
| `/guide [écran]` | `shguide.use` | Accueil de Jake, ou un de ses écrans (`taches`, `recompenses`, `nouveautes`, `aide`, `premiers-pas`, accents facultatifs) |
| `/guide retour [commande]` | `shguide.use` | Bouton Retour d'un menu que Jake peut ouvrir : revient à l'écran de Jake d'où vient le joueur, sinon lance la commande |
| `/guide aller` | `shguide.use` | Lance le clic de l'objectif du moment |
| `/guide fleche` | `shguide.use` | Coupe ou remet la flèche du tutoriel |
| `/shguide reload` | `shguide.admin.reload` | Recharge les quatre fichiers ; si l'un est invalide, rien ne change et le message donne le fichier et la clé. Au démarrage, une nouveauté illisible est laissée de côté et un `menus.yml` illisible est remplacé par celui du jar : le guide marche, la console le dit |
| `/shguide info <joueur>` | `shguide.admin.info` | Programme, série et dates d'un joueur |
| `/shguide annonce <minutes\|HH:MM> <texte> \| <commande>` | `shguide.admin.annonce` | Event qui commence dans `<minutes>` ou à `HH:MM` : il devient l'objectif du moment de tout le serveur `annonce-avant-minutes` avant son début, et sa barre de compte à rebours s'affiche `annonce-barre.minutes-avant` avant. L'annoncer plusieurs fois remplace l'annonce. |
| `/shguide set <joueur> <serie\|jour\|derniere-venue\|dernier-complet\|premier-jour> <valeur>` | `shguide.admin.set` | Corrige un joueur (dates au format 2026-11-23, `aucun` efface `dernier-complet` et `premier-jour`) |
| `/shguide accueil <joueur> <nouveau\|fin\|arrivee>` | `shguide.admin.accueil` | Refait du joueur un nouveau joueur (cadeaux, tutoriel et arrivée remis à zéro), finit son tutoriel, ou rejoue son arrivée là où il est |
| `/shguide reset <joueur>` | `shguide.admin.reset` | Remet le guide du joueur à zéro |

`shguide.admin` donne toutes les sous-commandes de `/shguide`. `shguide.use` est `op` par défaut : les joueurs le reçoivent par leur groupe.

## Placeholders

| Placeholder | Valeur |
|---|---|
| `%shguide_objectif%` | Objectif du moment, court : ce qu'il faut faire s'il tient en `objectif-longueur-max` caractères, sinon son nom |
| `%shguide_objectif_nom%` / `%shguide_objectif_faire%` | Son nom / ce qu'il faut faire, en entier |
| `%shguide_objectif_detail%` | Première ligne de sa description |
| `%shguide_ligne%` | Ligne du scoreboard : étape du tutoriel, cadeau prêt, ou tâches et série (`scoreboard` de `messages.yml`) |
| `%shguide_programme%` / `%shguide_programme_total%` | Tâches faites / tâches du jour |
| `%shguide_serie%` / `%shguide_record%` | Série actuelle / meilleure série |
| `%shguide_joker%` | oui si le joker de la semaine est encore là |
| `%shguide_recompense_prete%` / `%shguide_cadeaux%` | oui si un cadeau attend / nombre de cadeaux |
| `%shguide_alerte%` / `%shguide_alerte_texte%` | oui si Jake a quelque chose pour le joueur / ce que c'est (cadeau, nouveautés, tâches du jour pas encore vues) |
| `%shguide_fin_jour%` | Temps avant les nouvelles tâches |
| `%shguide_nouveautes_non_lues%` | Nouveautés que le joueur n'a pas encore vues |
| `%shguide_nouveautes_nombre%` / `%shguide_nouveaute_<n>%` | Nouveautés sorties depuis la dernière venue / titre de la n-ième |
| `%shguide_retour%` | Texte du retour guidé, sur plusieurs lignes |
| `%shguide_menu_vu%` | oui si le joueur a ouvert le menu de Jake aujourd'hui |
| `%shguide_nouveau%` | oui pour un joueur arrivé depuis l'accueil (premier jour connu), non pour un ancien |
| `%shguide_accueil_temps%` | Temps de jeu restant avant le cadeau « rester » (m:ss), vide une fois le cadeau donné |
| `%shguide_tutoriel%` / `%shguide_tutoriel_fini%` | oui tant que le tutoriel est en cours / oui quand il est terminé |
| `%shguide_tutoriel_etape%` / `%shguide_tutoriel_total%` | Numéro de l'étape en cours / nombre d'étapes |
| `%shguide_tutoriel_texte%` | Texte de l'étape en cours, vide si le tutoriel est fini |

## Moments

La section `moments` de `config.yml` donne, pour chaque moment du guide, une liste de commandes lancées pour le joueur concerné, sur le fil principal. Elles s'écrivent comme les autres listes de commandes du fichier : `[console] …`, ou une commande que le joueur lance lui-même. `{joueur}` et les jetons du moment sont remplacés d'abord, puis les placeholders PlaceholderAPI, lus pour ce joueur. Les jetons de texte libre (`{tache}`, `{etape}`, `{nom}`) sont donnés sans le caractère `%`, pour qu'un texte ne devienne jamais un placeholder ; les jetons numériques peuvent servir dans un placeholder (`%x_{palier}%`). Une liste vide ou absente ne fait rien, et le plugin ne sait pas ce que font ces commandes : un autre plugin peut s'y brancher sans que le guide le connaisse.

```yaml
moments:
  tache-faite:
    - "[console] say {joueur} a fini une tâche, {faites} sur {total} : {tache}"
```

| Moment | Quand | Jetons |
|---|---|---|
| `tache-faite` | Une tâche du jour vient d'être faite, pas la dernière | `{tache}` son texte, `{faites}` `{total}` `{restantes}` |
| `programme-complet` | La dernière tâche du jour vient d'être faite | `{serie}` |
| `palier` | Un palier de série vient d'être atteint | `{palier}` le jour du palier |
| `cadeau-pret` | Un cadeau vient d'arriver dans les récompenses (jour complet, palier, cadeau de bienvenue, cadeau du lendemain) | `{cadeaux}` ceux qui attendent |
| `cadeau-pris` | Le joueur vient de récupérer un cadeau, un lancement par cadeau (« tout récupérer » en lance plusieurs) | `{sorte}` : `jour`, `palier`, `bienvenue` ou `demain` |
| `etape-faite` | Une étape du tutoriel vient d'être faite | `{etape}` son identifiant, `{numero}` `{total}` |
| `tutoriel-fini` | Le tutoriel est fini | |
| `retour` | Le joueur revient après l'absence qui déclenche le retour guidé, en même temps que son dialogue | `{jours}` |
| `annonce` | Une annonce d'event devient visible pour le joueur (barre ou objectif du moment), une fois par annonce et par joueur, même s'il se reconnecte. Elle part pour `moments-par-seconde` joueurs au plus à chaque seconde | `{nom}` l'event, `{minutes}` avant son début |

Un jour complet lance `programme-complet`, puis `palier` si la série atteint un palier, puis `cadeau-pret`. La récompense propre à une tâche (`recompense`, voir Tâches du jour) n'est pas un moment : elle part aussi pour la dernière tâche du jour, avant `programme-complet`. Une annonce est un nom et une minute de début : les rappels du bot (`shguide annonce HH:MM …`) d'un même event restent la même annonce. Un redémarrage du serveur ou un rechargement du plugin oublie ce qui a été dit : l'annonce repart pour tous les joueurs au rappel suivant du bot. Un moment inconnu ou une valeur qui n'est pas une liste est refusé par `/shguide reload`, qui nomme la clé ; au démarrage, la section `moments` invalide est ignorée (la console nomme la clé, aucun moment ne part) et le guide démarre quand même.

## Configuration

Fichiers par défaut, avec des valeurs d'exemple. Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHGuide/
├── <a href="src/main/resources/config.yml">config.yml</a>       base de données, tâches, récompenses, série, objectifs, retour, sons, moments, exemple d'accueil (commenté)
├── <a href="src/main/resources/menus.yml">menus.yml</a>        les écrans de Jake : cases, icônes, textes, conditions, clics
├── <a href="src/main/resources/nouveautes.yml">nouveautes.yml</a>   une entrée par nouveauté
└── <a href="src/main/resources/messages.yml">messages.yml</a>     messages du chat, objectif, scoreboard, alerte, retour
</pre>

## Compilation

Java 25 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.
