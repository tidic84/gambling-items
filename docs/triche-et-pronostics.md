# Triche et pronostics

Deux systèmes s'ajoutent aux jeux du casino : la **triche**, avec la sécurité qui la traque, et un **marché de prédiction** façon Polymarket. Ils partagent un même registre serveur (`CasinoLedger`) : soupçons, interdictions, questions et gains des créateurs.

## Triche

### Les outils

| Outil | Jeu | Effet | Utilisations | Recette |
| --- | --- | --- | --- | --- |
| Lentille de cartes marquées | Blackjack | Lit la carte cachée du croupier et la prochaine carte du sabot, une fois par main. Seul le tricheur les voit. | 12 | 4 pépites d'or autour d'une vitre |
| Aimant de roulette | Roulette | Décale la bille d'une case, vers le voisin de roue qui rapporte le plus au tricheur. Il faut avoir des jetons sur le tapis. | 8 | 2 redstone, 5 lingots de fer en fer à cheval |
| Cale de rouleau | Machine à sous | Le prochain tirage garde la meilleure de deux lignes. On peut la poser et la retirer gratuitement : le contrôle a lieu au tirage. | 16 | 2 pépites de fer sur un papier |
| Montre truquée | Crash | Vibre (message et son pour le porteur seul) quelques ticks avant le crash, en lisant le point secret. | 10 | une horloge entre 2 redstone |

Quand un joueur porte l'outil, un bouton rouge apparaît dans l'en-tête de la fenêtre du jeu. Son infobulle indique le risque d'être repéré, calculé par le serveur.

### Détection

Chaque usage déclenche un tirage côté serveur :

```
risque = base de l'outil + soupçons × heatBasisPoints + caméras × cameraBasisPoints   (plafonné à maximumBasisPoints)
```

- **Soupçons** : de 0 à 100. Un tour qui passe inaperçu les augmente selon l'outil. Ils baissent d'un point toutes les `coolingSeconds`.
- **Caméras de surveillance** : chaque caméra dans un rayon de `cameraRadius` blocs autour du joueur ajoute du risque, même quand il joue depuis le casino de poche.

**Pris sur le fait :** l'outil est confisqué, la mise de la manche est saisie (main perdue, jetons saisis, crédit perdu, mise du crash perdue) et les joueurs proches sont prévenus. Le tricheur est interdit de tous les jeux pendant `banSeconds` et ses soupçons restent élevés.

**Inaperçu :** l'effet s'applique, l'outil s'use et les soupçons montent.

Les règles pures sont dans `core/.../cheat/CheatRules.java` (risque, refroidissement, aimant, montre), avec des tests unitaires. L'intégration est dans `fabric/cheat/CasinoSecurity.java`.

### Commandes

- `/gamblingitems security status [joueur]` : soupçons, interdiction restante, nombre de tricheries et de prises. Le joueur peut consulter son propre dossier ; il faut être opérateur pour celui des autres.
- `/gamblingitems security pardon <joueur>` (opérateur) : efface les soupçons et l'interdiction.

## Pronostics

### Principe

Chaque question est un pari OUI/NON. Les prix viennent d'un teneur de marché automatique, la *logarithmic market scoring rule* de Hanson (`core/.../prediction/Lmsr.java`) :

- le prix du OUI se lit comme une probabilité, et OUI + NON = 100 % ;
- on peut toujours acheter et revendre, même quand on est seul à parier ;
- le teneur de marché ne peut pas perdre plus que la subvention déposée à l'ouverture.

Toutes les règles de calcul sont dans `PredictionMarket.java` : achat, revente, règlement, annulation et solvabilité. Un test fait jouer 200 marchés aléatoires et vérifie que chaque ticket est payé en entier.

### Types de questions

| Type | Question | Réglée par |
| --- | --- | --- |
| Progrès | « X obtiendra « Objectif » à temps ? », parmi 16 progrès vanilla | le jeu (OUI dès que le progrès est obtenu, NON à l'échéance) |
| Morts | « X mourra au moins N fois ? » | le jeu (statistique de morts) |
| Diamants | « X minera au moins N minerais de diamant ? » | le jeu (minerai normal et deepslate) |
| Monstres tués | « X tuera au moins N monstres ? » | le jeu |
| Question libre | Le nom d'un papier renommé à l'enclume, posé dans la case « ? » | le créateur, avec possibilité de contestation |

Pour une question libre, le créateur annonce la réponse, puis tout le monde peut la contester pendant `challengeMinutes`. Sans contestation, la réponse est validée. En cas de contestation, un opérateur tranche avec `/gamblingitems market resolve <id> yes|no|cancel`. Si personne ne tranche, la question est annulée et les tickets sont remboursés au dernier prix. C'est le principe de l'oracle optimiste de Polymarket, sans la caution.

### Tickets

Un achat consomme toute la mise préparée et donne un **ticket physique**, un objet qui porte la position : question, côté, parts, prix payé et acheteur. On peut le donner, le vendre, le ranger ou se le faire voler : il paie celui qui l'encaisse. Avant l'échéance, on le revend au teneur de marché ; après le règlement, chaque part gagnante paie `shareValue`.

### Créateurs et maison

N'importe qui ouvre une question en déposant au moins `minimumSubsidy`. Cette subvention règle la profondeur du marché. Le créateur touche les frais (`feeBasisPoints` de chaque achat) et ce que le teneur de marché n'a pas perdu ; c'est le bouton « Gains de créateur ». La maison ouvre aussi seule des questions sur les joueurs connectés (`houseMarkets`) ; elle garde ce qui reste de leurs pools.

### Commission des jeux (lien avec la triche)

Parier sur son propre échec est autorisé mais surveillé : OUI sur ses propres morts, NON sur son propre progrès, ses diamants ou ses monstres. Quand un tel ticket gagnant est encaissé, la commission fait un tirage (`insiderBasisPoints`, augmenté par les soupçons de l'acheteur). Si elle repère le pari, le ticket est saisi et l'acheteur est interdit de casino. Sinon, le ticket paie mais les soupçons montent. Le ticket garde l'identité de son acheteur : le confier à un complice n'y change rien.

### Où jouer

- Onglet **Pronos** du casino de poche.
- **Tableau des cotes**, à accrocher au mur : il affiche les trois questions les plus actives et ouvre le marché d'un clic. Recette : papier, émeraude, planches.
- `/gamblingitems market list` liste les questions en cours.

## Configuration

`config/gamblingitems/games.json` passe en schéma 21 et gagne deux sections, ajoutées automatiquement aux fichiers existants :

- `security` : `enabled`, `heatBasisPoints`, `cameraBasisPoints`, `maximumBasisPoints`, `coolingSeconds`, `banSeconds`, `caughtHeat`, `cameraRadius`, `watchLeadTicks`, et `tools.<outil>.baseBasisPoints` / `heat` ;
- `predictionMarket` : `shareValue`, `feeBasisPoints`, `minimumSubsidy`, `maximumOpenPerPlayer`, `challengeMinutes`, `proposalTimeoutMinutes`, `disputeTimeoutHours`, `retentionDays`, `announce`, `insiderBasisPoints`, `insiderHeat`, `houseMarkets.{enabled, intervalMinutes, maximumOpen, subsidy, durationMinutes}`.

`predictionMarket` peut aussi utiliser la monnaie configurable du casino (`currency.games`).

## Vérifié et non vérifié

Vérifié :
- tests unitaires du cœur ;
- 136 GameTests serveur Fabric 1.21.1, dont 14 nouveaux : chaque outil, inaperçu ou repéré, achat, revente et encaissement par la fenêtre, règlement automatique, question libre contestée, commission, sauvegarde ;
- build NeoForge 1.21.1 ;
- génération des 42 portages sans erreur ;
- ouverture et capture de chaque fenêtre dans un client Fabric 1.21.1 (`ClientSmoke` ouvre aussi le marché et donne les outils de triche) : boutons de triche dans l'en-tête, onglet Pronos, écran du marché.

Non vérifié :
- rendu du tableau des cotes posé dans le monde, et affichage pendant une partie (carte lue par la lentille, cadre rouge de la montre) : seules les fenêtres au repos ont été capturées ;
- compilation des portages autres que 1.21.1. Points à surveiller : signature de `appendHoverText` (1.21.5 et plus), permissions des commandes (1.21.11 et plus), API 26.x.
