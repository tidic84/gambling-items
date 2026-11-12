# Machines à lots

Dix machines s'ajoutent aux bornes existantes : la roue de la fortune, le pousse-pièces, la pince à lots, le pachinko, le gacha à capsules, le terminal haute mise, et quatre bornes d'arcade (Plinko géant, Big Six, vidéo poker, course de chevaux). Chacune est un bloc à poser. Le Plinko, le Big Six et la course font deux blocs de large. Toutes se jouent **sur le modèle 3D**, sans fenêtre ni HUD : on touche la commande de la borne, celle sous le curseur est surlignée en or.

Tout le contenu du mod est rangé dans son propre onglet créatif « Gambling Items », et non plus dans « Blocs fonctionnels ».

## Déroulement d'une partie

1. Le joueur dépose des objets cotés (ou la monnaie du casino) dans les cinq cases de crédit.
2. **Jouer** engage tout le crédit, s'il atteint la mise minimale et ne dépasse pas la mise maximale.
3. Le serveur tire le résultat **une seule fois**, règle aussitôt la partie, puis seulement l'animation commence. L'écran et le meuble ne font que rejouer ce résultat.
4. Si le gain atteint la mise, les objets joués restent en place et seul le bénéfice est ajouté en monnaie. Sinon le crédit est pris et la part rendue (par exemple 0,5x) est payée en monnaie.

Une deuxième demande pendant l'animation est refusée sans tirage. Un clic sur une machine qui n'est plus la bonne, trop éloignée ou désactivée ne fait rien. La partie refuse aussi de démarrer si les cases de gains ne peuvent pas contenir le plus gros lot possible. Fermer la fenêtre rend le crédit restant à l'inventaire ; les gains restent dans le coffre de la machine, propre à chaque joueur.

## Règles et chances par défaut

Les multiplicateurs incluent la mise : 1,5x rend une fois et demie ce qui a été joué. L'écran affiche chaque gain avec sa probabilité exacte et le retour moyen.

| Machine | Règle | Retour moyen |
| --- | --- | --- |
| Roue de la fortune | 12 secteurs pondérés, de 0x à 15x | 95,29 % |
| Pousse-pièces | nombre de pièces tombées, de 0x à 25x | 95 % |
| Pince à lots | 7 positions ; chaque lot a sa valeur et sa prise (chance d'être tenu) | 96 % à chaque position, 95 % pour le lot à 25x |
| Pachinko | 8 rangées de clous à 50/50, 9 poches (loi binomiale, 70 billes sur 256 au centre) | 96,25 % |
| Gacha à capsules | 5 raretés, de 0,3x à 25x | 95 % |
| Terminal haute mise | cible choisie de 1,25x à 100x, atteinte avec une chance d'exactement 1 sur la cible | 96 % (part de la maison de 4 %) |
| Plinko géant | 12 rangées de clous à 50/50, 13 poches de 0,4x à 41x | affiché par la borne |
| Big Six | roue de 54 cases ; on mise sur un symbole, payé 2x à 41x | affiché par la borne |
| Vidéo poker | Jacks or Better 9/6 : donne, on garde des cartes, on tire une fois | `returnBasisPoints` 9 600 par défaut |
| Course de chevaux | 6 chevaux pondérés, multijoueur : les paris de tous se jouent sur la même course | affiché par la borne |
| Craps | lignes pass et don't pass, vraies règles (7/11, 2/3/12, point) ; un 12 au premier lancer est barré pour don't pass (mise rendue) | 98,59 % (pass), 98,64 % (don't pass) |
| Baccara | punto banco, sabot de 8 jeux, tableau de tirage imprimé ; joueur 1:1, banque 0,95:1, égalité 8:1 (mises joueur/banque rendues sur égalité) | 98,76 % (joueur), 98,94 % (banque), 85,64 % (égalité) |
| Keno | 5 numéros sur 80, 20 tirés (loi hypergéométrique exacte) ; paie à 2, 3, 4 ou 5 numéros sortis | 95,13 % |
| Ticket à gratter | 9 cases ; 3 cases identiques gagnent ce lot, de 1x à 100x | 95 % |

Craps et baccara gardent l'avantage de la maison du vrai jeu : les chances de chaque issue sont calculées exactement (`CrapsRules`, `BaccaratRules`, testés dans le core), pas configurées ; seuls les paiements le sont (`payouts`). Les dés, les cartes, la grille du keno et le ticket montrés sont reconstruits depuis l'issue tirée et sa graine : tous les joueurs voient la même partie.

Les retours des quatre bornes d'arcade ne sont pas recopiés ici : ils découlent des tables par défaut et la borne affiche la probabilité exacte de chaque gain.

## Arcade : tickets, caisse, jetons

- Par défaut (`arcade.tickets` à `true`), un gain des bornes ne paie plus en objets mais en **tickets d'arcade**, comptés une fois le résultat montré. Un ticket vaut `ticketValue` (10 = un centième de lingot de fer).
- La **caisse** échange les objets cotés contre des **jetons** de 1, 5, 25 et 100 (un jeton de 1 vaut `chipValue`, 1 000 = un lingot de fer), et les jetons contre des objets. L'échange est exact : si la monnaie ne peut pas être rendue en objets, la caisse prend quelques jetons de moins, ou refuse. Les jetons sont des objets cotés : ils se misent comme le reste et s'empilent sur les tables.
- La caisse tient aussi le **comptoir à lots** : 16 lots par défaut, du lingot de fer (100 tickets) à l'étoile du Nether (50 000).
- Le **tableau des scores** (bloc mural, 2×2) affiche les 10 meilleurs gains de la semaine ; un clic dessus montre au joueur ses 20 dernières parties.
- Décor : néons (16 couleurs, lumière 15, dessinés en pleine lumière même la nuit ; un colorant les recolore) et moquettes de casino.
- **Tabourets de casino** : un clic assoit le joueur (un seul par tabouret), s'accroupir le relève. Le siège est invisible, jamais sauvegardé, et disparaît dès qu'il est quitté ou que le tabouret est cassé.
- **Carte VIP** : un lot du comptoir (25 000 tickets par défaut). Une borne réservée (`/gamblingitems cabinet <x y z> vip on`) refuse les joueurs sans carte dans l'inventaire et porte un badge doré VIP au-dessus d'elle.
- **Thèmes** : accroupi, un colorant sur une borne lui donne un style : rouge, orange, jaune = Vegas (ampoules qui chassent, enseigne rouge et or) ; blanc, gris, noir, marron = rétro (chrome, crème et turquoise) ; les autres = néon de leur couleur (enseigne, lampes et lueur au sol). Le même colorant une seconde fois rend le style d'origine ; le colorant n'est consommé que s'il peint.
- **Gros gain** (10x ou plus) : la borne éclaire réellement la pièce (lumière 15) pendant la célébration, un gyrophare tourne sur son toit et des confettis tombent devant elle.
- **Néons au rythme** : près d'une borne qui joue la musique d'arcade, chaque néon (à 8 blocs au plus) s'entoure d'un halo qui gonfle à chaque temps (120 bpm).
- Le **croupier** suit le joueur du regard et distribue quand une table voisine joue. Un clic dessus ouvre le guichet de la caisse (jetons et comptoir à lots), pratique à côté des tables. Accroupi, un clic lui fait expliquer les règles du jeu le plus proche (table, écran ou borne à 5 blocs au plus).

```json
"arcade": {
  "tickets": true,
  "ticketValue": 10,
  "chipValue": 1000,
  "raceBettingTicks": 200,
  "prizes": [{"item": "minecraft:diamond", "count": 1, "tickets": 1000}]
}
```

`raceBettingTicks` est le temps pendant lequel une course prend les paris après le premier.

## Réglages par borne (administrateur)

`/gamblingitems cabinet <x y z> info|open|close|reset|vip on|vip off|minimum <valeur>|maximum <valeur>` (permission 2) règle une borne posée sans toucher à la configuration : la fermer, la réserver aux VIP, lui donner sa propre mise minimale ou maximale, ou revenir aux réglages du fichier avec `reset`.

## Configuration

Les machines sont réglées dans la section `machines` de `config/gamblingitems/games.json`. Un fichier existant (schéma 19 ou 20) reçoit les sections manquantes (`machines`, les quatre bornes d'arcade, `arcade`) à son premier chargement, sans toucher aux autres réglages. Si la monnaie du casino payait déjà `slotMachine`, elle paie aussi les six machines.

```json
"machines": {
  "luckyWheel": {
    "enabled": true,
    "minimumStake": 1000,
    "maximumStake": 0,
    "returnBasisPoints": 10000,
    "playTicks": 100,
    "resultTicks": 60,
    "prizes": [{"multiplier": "0", "weight": 10}, {"multiplier": "1.5", "weight": 8}]
  }
}
```

- Clés : `luckyWheel`, `coinPusher`, `prizeClaw`, `pachinko`, `capsuleGacha`, `highStakes`, `plinko`, `bigSix`, `videoPoker`, `horseRace`. Ce sont aussi leurs noms dans `currency.games`.
- `enabled` : `false` ferme la machine, et le joueur reçoit un message à l'ouverture.
- `minimumStake` et `maximumStake` sont des valeurs internes (1 000 = 1 à l'écran). `maximumStake` à 0 signifie sans maximum.
- `returnBasisPoints` multiplie chaque gain (10 000 = 100 %). Le terminal haute mise est réglé à 9 600 par défaut.
- `playTicks` (de 10 à 400) est la durée de l'animation ; `resultTicks` est la durée d'affichage du résultat sur le meuble.
- Roue, pousse-pièces et gacha : `prizes` avec `multiplier` et `weight`.
- Pince : `prizes` avec `multiplier` et `gripPercent` (0 à 100), 9 positions au plus.
- Pachinko : `pockets`, exactement 9 multiplicateurs ; les chances sont celles des clous, pas un réglage.
- Haute mise : `targets`, chaque cible strictement au-dessus de 1.
- Plinko : `pockets`, exactement 13 multiplicateurs. Big Six et course : `prizes` avec `multiplier` et `weight`. Vidéo poker : `paytable`, 9 multiplicateurs de la paire de valets à la quinte flush royale.
- Les multiplicateurs acceptent deux décimales au plus.

Le chargement refuse une table dont le retour moyen effectif dépasserait 100 % à une position, avec un message qui nomme la machine fautive.

## Préférences du joueur

Chaque joueur peut régler `config/gamblingitems/client.json` sur son propre client. Ce fichier n'est jamais envoyé au serveur.

```json
{
  "soundVolume": 1.0,
  "reducedMotion": false,
  "ambientMusic": true
}
```

- `soundVolume` (0 à 1) multiplie les sons du casino.
- `ambientMusic` : une musique chiptune en boucle près des bornes, qui s'éteint quand on s'en éloigne. `false` la coupe.
- `reducedMotion` montre le résultat des machines et des machines à sous sans faire tourner la roue, les rouleaux ou la bille.

Les clés manquantes sont ajoutées au fichier, les valeurs déjà présentes sont conservées.

## Jouer directement sur la borne

Aucune borne n'ouvre de fenêtre : tout se joue en touchant sa commande avec un objet de valeur en main.

| Borne | Jouer | Changer de choix |
|---|---|---|
| Machine à sous | le levier, ou le gros bouton rouge | — |
| Roue de la chance | la roue, ou le bouton rouge | — |
| Pousse-pièces | le plateau avec la fente | — |
| Pince | le bouton de descente | le joystick (lot suivant) |
| Pachinko | la poignée de lancement | — |
| Gacha | la manivelle | — |
| High stakes | le bouton vert | le bouton rouge (cible suivante) |
| Plinko | le bouton de lâcher | — |
| Big Six | — | la touche du symbole misé (elle joue aussitôt) |
| Vidéo poker | la touche rouge : donne, puis tire | une carte, ou sa touche HOLD : la garder |
| Course de chevaux | — | la touche du cheval (pari ; la course part après `raceBettingTicks`) |

- La pile tenue est misée en entier, ou la plus grande partie que la machine accepte (mise maximale, lot qu'elle doit pouvoir payer). Le tirage est fait une seule fois par le serveur.
- Pendant qu'une partie se joue, la borne refuse une nouvelle mise. Une borne fermée par un administrateur ne prend rien.
- Le gacha secoue ses capsules avec une petite physique (26 billes, collisions, gravité) calculée sur le client seulement ; elle s'endort dès que le tas est calme.
- La bille du Plinko tombe de la coupelle, rebondit sur chaque clou en arc de parabole (gravité, hauteur de rebond propre à chaque clou), puis rebondit deux fois dans sa poche avant de s'arrêter. Le clou touché s'allume et sonne au moment du choc. Le trajet reste celui tiré par le serveur, et tous les joueurs voient la même chute (`PlinkoFlight`, testé dans le core).

À partir de dix fois la mise, un gain est fêté : lampes en folie et « GROS GAIN ! » sur l'enseigne, étincelles et son pour les joueurs autour, pluie de pièces dans la fenêtre. L'option `reducedMotion` coupe les particules et la pluie de pièces.

## Modèles et textures

Les modèles des bornes, des écrans et des tables sont générés, pas écrits à la main. Après une modification, relancer depuis la racine du dépôt :

```
python tools/generate_cabinet_models.py   # les 15 bornes (3 blocs de haut) et leurs textures cab_*
python tools/generate_station_models.py   # le moniteur 3×2 des 8 écrans et leurs icônes d'inventaire
python tools/generate_table_models.py     # les tables de blackjack, roulette et bingo, et leurs textures table_*
python tools/generate_arcade_assets.py    # jetons, néons, moquettes, caisse, tableau des scores, croupier, tabouret, carte VIP
python tools/generate_sounds.py           # les sons propres à chaque borne et la musique (numpy + soundfile)
```

Seul le décor fixe est dans les modèles. Ce qui bouge (rouleaux, roue, pièces, pince, bille, capsules, cadran, levier) est dessiné par `SlotCabinet` et `MachineCabinet`. Pour photographier toutes les bornes, tous les écrans et toutes les tables en jeu :

```
JAVA_TOOL_OPTIONS="-Dgamblingitems.clientsmoke=true -Dgamblingitems.clientsmoke.gallery=true" ./gradlew -p platforms/fabric-1.21.1 runClient --args="--quickPlaySingleplayer smoke"
```

Les captures arrivent dans `run/screenshots/gamblingitems-gallery-*.png`. `-Dgamblingitems.clientsmoke.gallery.only=floor` ne garde que les vues dont le nom commence ainsi (ici : jeux de table, tabourets, thèmes, VIP, gros gain).

### Mesure des FPS

```
JAVA_TOOL_OPTIONS="-Dgamblingitems.clientsmoke=true -Dgamblingitems.clientsmoke.bench=true" ./gradlew -p platforms/fabric-1.21.1 runClient --args="--quickPlaySingleplayer smoke"
```

Construit la salle de la galerie, fait jouer toutes les bornes en continu, puis mesure 20 s depuis trois places : la salle vue d'en haut (`floor`), de près (`close`) et dos aux meubles (`away`). Le résultat (FPS moyens, 1 % bas, temps passé à dessiner les meubles) est écrit dans `run/gamblingitems-bench.txt`. Les chiffres dépendent du PC : ne comparer que deux mesures faites sur la même machine.

Ce qui garde le rendu rapide, à respecter dans le nouveau code de rendu :
- les quads ont leur propre tampon (`WorldCanvas`), vidé une fois par meuble : ne pas dessiner dans le tampon partagé, où chaque passage quad ↔ texte coûte un appel de dessin ;
- un meuble entièrement derrière la vue n'est pas dessiné, le texte de moins de 2 pixels non plus, et les formes rondes ont moins de segments de loin (`WorldCanvas.segments`) ;
- rien n'est alloué à chaque image si on peut l'éviter : `WorldCanvas.translate` et `WorldCanvas.width` gardent les traductions et les largeurs de texte, `WorldCanvas.text` écrit sans empiler de pose ;
- un cube opaque n'a que ses faces extérieures ; un cube translucide garde les deux sens.

`generate_sounds.py` réécrit aussi les anciens sons : l'encodeur OGG n'est pas reproductible octet pour octet, donc ne garder que les fichiers des bornes ajoutées (`git checkout` des autres).

## Limites connues

- Chaque borne a ses quatre sons (`machine.<borne>.start|tick|win|lose`), synthétisés par `tools/generate_sounds.py` ; les sons d'interface (`ui.*`) réutilisent toujours des sons vanilla.
- Le coût de la physique des capsules n'a pas été mesuré en jeu, ni celui des confettis et des halos de néons.
- Les halos des néons sont dessinés par la borne voisine qui joue la musique : un néon loin de toute borne ne pulse pas.
- Un néon cassé redonne un néon rose (sa couleur n'est pas gardée dans l'objet) ; un colorant le recolore.
- La hauteur du joueur assis sur un tabouret est réglée pour 1.20.5 et plus ; avant, elle est corrigée par le calcul mais n'a pas été vérifiée en jeu.
- Depuis Minecraft 1.21.6, le décor de la machine dans la fenêtre n'est pas découpé au bord de son cadre.
