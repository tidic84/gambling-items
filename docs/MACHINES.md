# Machines à lots

Six machines s'ajoutent aux bornes existantes : la roue de la fortune, le pousse-pièces, la pince à lots, le pachinko, le gacha à capsules et le terminal haute mise. Chacune est un bloc à poser, fabriqué à l'établi avec sept lingots de fer, un terminal d'échange et un objet propre à la machine (horloge, pépite d'or, crochet, barreaux, verre ou diamant). Elles ne s'ouvrent qu'à leur bloc, jamais depuis le terminal ou le casino de poche.

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

## Configuration

Les machines sont réglées dans la section `machines` de `config/gamblingitems/games.json`. Un fichier existant (schéma 19) reçoit cette section à son premier chargement, sans toucher aux autres réglages. Si la monnaie du casino payait déjà `slotMachine`, elle paie aussi les six machines.

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

- Clés : `luckyWheel`, `coinPusher`, `prizeClaw`, `pachinko`, `capsuleGacha`, `highStakes`. Ce sont aussi leurs noms dans `currency.games`.
- `enabled` : `false` ferme la machine, et le joueur reçoit un message à l'ouverture.
- `minimumStake` et `maximumStake` sont des valeurs internes (1 000 = 1 à l'écran). `maximumStake` à 0 signifie sans maximum.
- `returnBasisPoints` multiplie chaque gain (10 000 = 100 %). Le terminal haute mise est réglé à 9 600 par défaut.
- `playTicks` (de 10 à 400) est la durée de l'animation ; `resultTicks` est la durée d'affichage du résultat sur le meuble.
- Roue, pousse-pièces et gacha : `prizes` avec `multiplier` et `weight`.
- Pince : `prizes` avec `multiplier` et `gripPercent` (0 à 100), 9 positions au plus.
- Pachinko : `pockets`, exactement 9 multiplicateurs ; les chances sont celles des clous, pas un réglage.
- Haute mise : `targets`, chaque cible strictement au-dessus de 1.
- Les multiplicateurs acceptent deux décimales au plus.

Le chargement refuse une table dont le retour moyen effectif dépasserait 100 % à une position, avec un message qui nomme la machine fautive.

## Préférences du joueur

Chaque joueur peut régler `config/gamblingitems/client.json` sur son propre client. Ce fichier n'est jamais envoyé au serveur.

```json
{
  "soundVolume": 1.0,
  "reducedMotion": false
}
```

- `soundVolume` (0 à 1) multiplie les sons du casino.
- `reducedMotion` montre le résultat des machines et des machines à sous sans faire tourner la roue, les rouleaux ou la bille.

Les clés manquantes sont ajoutées au fichier, les valeurs déjà présentes sont conservées.

## Jouer directement sur la borne

Chaque borne se joue aussi sans ouvrir sa fenêtre, en touchant sa commande avec un objet de valeur en main :

| Borne | Jouer | Changer de choix |
|---|---|---|
| Machine à sous | le levier, ou le gros bouton rouge | — |
| Roue de la chance | la roue, ou le bouton rouge | — |
| Pousse-pièces | le plateau avec la fente | — |
| Pince | le bouton de descente | le joystick (lot suivant) |
| Pachinko | la poignée de lancement | — |
| Gacha | la manivelle | — |
| High stakes | le bouton vert | le bouton rouge (cible suivante) |

- La pile tenue est misée en entier, ou la plus grande partie que la machine accepte (mise maximale, lot qu'elle doit pouvoir payer). Ce sont les mêmes vérifications et le même tirage serveur que la fenêtre.
- Les gains attendent dans la machine, comme derrière la fenêtre. La même commande touchée à main vide les envoie dans l'inventaire, avec le crédit laissé devant la machine.
- Pendant qu'une partie se joue, la borne refuse une nouvelle mise.
- Ailleurs sur la borne, ou à main vide sans rien à récupérer, le clic ouvre la fenêtre comme avant.

À partir de dix fois la mise, un gain est fêté : lampes en folie et « GROS GAIN ! » sur l'enseigne, étincelles et son pour les joueurs autour, pluie de pièces dans la fenêtre. L'option `reducedMotion` coupe les particules et la pluie de pièces.

## Modèles et textures

Les modèles des bornes, des écrans et des tables sont générés, pas écrits à la main. Après une modification, relancer depuis la racine du dépôt :

```
python tools/generate_cabinet_models.py   # les 7 bornes (3 blocs de haut) et leurs textures cab_*
python tools/generate_station_models.py   # le moniteur 3×2 des 8 écrans et leurs icônes d'inventaire
python tools/generate_table_models.py     # les tables de blackjack, roulette et bingo
```

Seul le décor fixe est dans les modèles. Ce qui bouge (rouleaux, roue, pièces, pince, bille, capsules, cadran, levier) est dessiné par `SlotCabinet` et `MachineCabinet`. Pour photographier toutes les bornes, tous les écrans et toutes les tables en jeu :

```
JAVA_TOOL_OPTIONS="-Dgamblingitems.clientsmoke=true -Dgamblingitems.clientsmoke.gallery=true" ./gradlew -p platforms/fabric-1.21.1 runClient --args="--quickPlaySingleplayer smoke"
```

Les captures arrivent dans `run/screenshots/gamblingitems-gallery-*.png`.

## Limites connues

- Les sons propres au mod (`ui.tick`, `ui.start`, `ui.win`, `ui.lose`) réutilisent des sons vanilla : un pack de ressources peut les remplacer.
- Depuis Minecraft 1.21.6, le décor de la machine dans la fenêtre n'est pas découpé au bord de son cadre.
