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

Le plugin ne connaît aucun autre plugin : tout passe par des placeholders PlaceholderAPI et des commandes écrits dans `config.yml`. Les joueurs sont vérifiés un par un, chacun une fois par `verification-secondes`, et les placeholders du guide lisent seulement le dernier calcul.

Utilisé sur : **Opale**.

## Dépendances

Requises : `PlaceholderAPI`, une base MariaDB (table `shguide_joueurs`, créée au démarrage).

## Commandes

| Commande | Permission | Effet |
|---|---|---|
| `/guide` | `shguide.use` | Menu de Jake |
| `/guide aller` | `shguide.use` | Lance le clic de l'objectif du moment (bouton « Y aller » du dialogue de retour) |
| `/shguide reload` | `shguide.admin.reload` | Recharge `config.yml` et `messages.yml` |
| `/shguide info <joueur>` | `shguide.admin.info` | Programme, série et dates d'un joueur |
| `/shguide annonce <minutes\|HH:MM> <texte> \| <commande>` | `shguide.admin.annonce` | Event qui commence dans `<minutes>` ou à `HH:MM` : il devient l'objectif du moment de tout le serveur `annonce-avant-minutes` avant son début. L'annoncer plusieurs fois remplace l'annonce. |
| `/shguide set <joueur> <serie\|jour\|derniere-venue\|dernier-complet> <valeur>` | `shguide.admin.set` | Corrige un joueur (dates au format 2026-11-23) |
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

## Configuration

Fichiers par défaut, avec des valeurs d'exemple. Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHGuide/
├── <a href="src/main/resources/config.yml">config.yml</a>     base de données, programme, série, objectifs, retour, menu, « Comment faire… », sons
└── <a href="src/main/resources/messages.yml">messages.yml</a>   messages du chat, menu de Jake, objectif du moment, retour
</pre>

## Compilation

Java 25 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.
