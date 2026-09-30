# Items portables et valeurs

## Un item par jeu

Un clic droit avec l'item ouvre directement le jeu, en main principale ou secondaire. Aucun bloc ni terminal supplémentaire n'est nécessaire. Les items se trouvent dans l'onglet créatif des blocs fonctionnels et possèdent chacun une recette.

| Jeu | Identifiant | Ingrédient central |
| --- | --- | --- |
| Upgrader | `gamblingitems:upgrader_item` | Diamant |
| Trade Up | `gamblingitems:trade_up_item` | Enclume |
| Caisses | `gamblingitems:case_opening_item` | Coffre |
| Crash | `gamblingitems:crash_item` | Fusée de feu d'artifice |
| Roulette | `gamblingitems:roulette_item` | Boussole |
| Blackjack | `gamblingitems:blackjack_item` | Papier |
| Battles de caisses | `gamblingitems:case_battle_item` | Épée en fer |
| Bingo | `gamblingitems:bingo_item` | Boule de slime |
| Machine à sous | `gamblingitems:slot_machine_item` | Lingot d'or |

La recette commune utilise sept lingots de fer (`F`), une redstone (`R`) et l'ingrédient du jeu (`J`) :

```text
F R F
F J F
F F F
```

Le terminal conserve l'accès à tous les jeux. Les bornes et les items utilisent les mêmes coffres de gains persistants par joueur et par jeu. Pour garder une interface portable ouverte, conserver l'item correspondant ou le terminal dans son inventaire.

Crash, roulette, bingo et battles rejoignent une table du même jeu située à 64 blocs maximum, dans la même dimension. S'il n'y en a aucune, l'item crée une table à l'emplacement du joueur, sans poser de bloc. Les joueurs proches peuvent la rejoindre avec leur propre item. Les battles demandent toujours plusieurs participants. La table conserve son emplacement jusqu'à la fin de la session ; une manche engagée continue même si son interface est fermée.

## Choisir la cible de l'Upgrader

1. Déposer une pile d'objets cotés dans la mise.
2. Chercher la cible par son nom, son identifiant (`minecraft:diamond`) ou son mod (`@nom_du_mod`).
3. Choisir l'objet dans la liste. Sa valeur et la chance correspondant à la mise sont affichées avant de lancer.

Le gain est **un exemplaire** de la cible sélectionnée. Sa valeur doit dépasser celle de la pile misée. Le serveur calcule :

```text
chance = min(plafond configuré, rendement × valeur de la mise / valeur de la cible)
```

Avec un rendement de 90 %, une mise de valeur 1 visant une cible de valeur 10 donne **9 %**. La même mise visant une cible de valeur 20 donne **4,5 %**. Le nombre d'objets misés et leur usure comptent dans la valeur. Une chance très faible reste faible : aucun minimum artificiel n'est appliqué.

## Catalogue automatique, y compris les mods

Au démarrage du serveur et après `/reload`, le catalogue est reconstruit à partir des recettes chargées. Cela comprend les recettes des mods et des datapacks qui exposent leurs résultats et ingrédients, ainsi que les transformations de forge. Les nouveaux items de jeu et blocs craftables du mod figurent aussi dans le catalogue.

La valeur estimée d'une sortie est la somme des ingrédients divisée par le nombre d'objets produits, arrondie à l'entier inférieur avec un minimum de 1. Pour les tags ou recettes alternatives, la route connue la moins chère est retenue. Les chaînes de fabrication sont résolues automatiquement. Les valeurs fixées dans la configuration restent prioritaires, même si une recette donnerait un autre prix.

Les matières premières inconnues et les cycles sans matière première cotée reçoivent une valeur de secours configurable. Ce sont des **estimations d'économie de jeu** : le calcul ne connaît pas la difficulté d'obtention, l'énergie, les fluides ou le temps des machines d'un autre mod. Les contenants rendus par une recette ne sont pas soustraits. Ajuster les prix des ressources importantes d'un modpack avant de s'y fier.

Les objets avec enchantements, noms personnalisés, contenu ou composants supplémentaires ne sont pas convertis en objets ordinaires. Les recettes dynamiques sans sortie identifiable sont signalées dans le rapport. Une recette de machine sans liste d'ingrédients peut fournir une cible, mais sa valeur reste une estimation. Les clés des caisses restent exclues du marché.

## Régler les valeurs

Fichier : `config/gamblingitems/games.json`, dans l'instance du serveur. Les fichiers existants sont migrés automatiquement en conservant leurs prix personnalisés.

Les valeurs sont des entiers : **1 000 unités internes = une valeur affichée de 1**, soit le lingot de fer par défaut. Exemple de propriétés à ajouter ou modifier dans le fichier existant :

```json
{
  "values": {
    "minecraft:iron_ingot": 1000,
    "monmod:lingot_titane": 12000,
    "monmod:machine": 150000
  },
  "automaticValues": {
    "enabled": true,
    "fallbackValue": 1000,
    "namespaceFallbackValues": {
      "minecraft": 100,
      "monmod": 3000
    },
    "excludedItems": ["monmod:objet_creatif"]
  }
}
```

Cet extrait ne remplace pas le fichier complet. Les identifiants de `values` doivent appartenir à des mods installés. Les prix autorisés vont de 1 à 1 000 000 000. Un objet modé sans recette peut être ajouté directement dans `values`. `excludedItems` retire un objet du catalogue même s'il possède une recette ou une valeur explicite.

Après modification, utiliser `/reload` puis rouvrir l'interface. Les manches déjà engagées gardent les valeurs avec lesquelles elles ont commencé.

Le fichier généré **`config/gamblingitems/resolved-values.json`** donne le prix final de chaque item et sa source (`configured`, `recipe` ou `fallback`). Il liste aussi les recettes non interprétées et indique si le calcul a convergé. Ce rapport est réécrit automatiquement : les corrections de prix doivent être faites dans `games.json`.

Le catalogue peut contenir jusqu'à 32 767 objets. Dépasser cette limite produit une erreur explicite au chargement ; aucune cible n'est supprimée silencieusement.

## Monnaie du casino

Par défaut, les jeux de casino acceptent n'importe quel objet coté et paient leurs gains en objets du catalogue, du plus cher au moins cher. La section `currency` de `games.json` les fait jouer avec **une monnaie** : un ou plusieurs items, vanilla ou modés, chacun avec sa valeur. Seuls ces items sont acceptés comme mise, et tous les gains sont payés avec eux. Il n'y a plus de gains en objets aléatoires.

```json
{
  "currency": {
    "enabled": true,
    "items": {
      "monmod:coin_silver": 1,
      "monmod:coin_gold": 10,
      "monmod:coin_platinum": 100
    },
    "games": ["crash", "roulette", "blackjack", "bingo", "slotMachine"]
  }
}
```

- `enabled` : `false` par défaut, ce qui conserve le fonctionnement en objets cotés.
- `items` : chaque identifiant avec sa valeur, exprimée en unités de monnaie. Les décimales sont acceptées jusqu'au millième (`0.5` vaut une demi-unité). Les items doivent appartenir à des mods installés, et les clés de caisse sont refusées. Seuls les exemplaires ordinaires comptent : un item renommé, enchanté ou porteur de données en plus n'est pas accepté.
- `games` : les jeux concernés, parmi `crash`, `roulette`, `blackjack`, `bingo` et `slotMachine`. Un jeu retiré de la liste garde les objets cotés.

L'Upgrader, le Trade Up, les caisses et les battles de caisses ne sont jamais concernés : ils continuent d'utiliser le catalogue de valeurs.

**Une unité de monnaie vaut 1 000 unités internes**, soit 1 à l'écran. Les réglages en valeur des jeux concernés se lisent donc en unités de monnaie : `"minimumStake": 5000` demande une mise d'au moins 5 unités (5 pièces d'argent dans l'exemple), et `"cardPrice": 10000` fait payer une carte de bingo 10 unités, soit une pièce d'or.

Un gain est rendu en commençant par la pièce la plus chère, puis la suivante : 15 unités donnent une pièce d'or et cinq pièces d'argent. Ce qui reste sous la pièce la moins chère ne peut pas être payé et constitue l'arrondi annoncé. Par exemple, un Crash encaissé à 1,5x sur une mise d'une seule pièce d'argent rend la mise sans profit. Des valeurs multiples les unes des autres (1, 10, 100…) donnent toujours la monnaie la plus simple.

Les fichiers de la version précédente, qui n'avaient qu'un champ `item`, sont convertis automatiquement : cet item devient la pièce de valeur 1.

Les tables partagées (Crash, roulette, bingo) gardent la configuration avec laquelle elles ont été créées. Pour qu'un changement de monnaie s'applique partout, redémarrer le serveur plutôt que d'utiliser `/reload`. Les objets déjà rangés dans les coffres de gains restent récupérables.
