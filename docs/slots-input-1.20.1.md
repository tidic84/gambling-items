# Audit des actions des neuf jeux en Minecraft 1.20.1

Le signalement décrit des objets acceptés dans Slots, puis un clic qui joue le
son du levier sans lancer les rouleaux. Les sources contiennent une cause
suffisante pour expliquer ce comportement, sur Fabric et Forge/NeoForge 1.20.1.

## Cause et périmètre

`ServerboundContainerButtonClickPacket` en Minecraft 1.20.1 écrit les identifiants
avec `FriendlyByteBuf.writeByte` et les relit avec `readByte`. Les anciens boutons
Slots `1000` et `1001` deviennent `-24` et `-23`. Le serveur ne reconnaît pas les
actions, même si le clic a déjà joué son son côté client.

L'audit des neuf jeux retrouve le même problème sur les autres actions :

| Jeu ou navigation | Identifiants concernés |
| --- | --- |
| Slots | Lancement et collecte ; désormais `0` et `1` |
| Trade Up | Échange `1000` |
| Upgrader | Sélections `128..32766` et lancement `32767` |
| Cases | Ouverture `1000` ; les sélections `0..15` tenaient déjà dans un octet |
| Crash | Mise, retrait et collecte `1000..1002` |
| Roulette | Collecte `1100` et toutes les zones encodées après `2000` |
| Blackjack | Distribution, hit, stand, double et collecte `1000..1004` |
| Case Battles | Actions `1000..1003`, choix de caisse après `1100`, manches après `1200` |
| Bingo | Achat et collecte `1000..1001` |
| Onglets des deux objets portables | `40000..40008` |

Une troncature peut aussi sélectionner un autre identifiant valide, notamment
une mauvaise cible de l'Upgrader, au lieu d'ignorer simplement le clic.

## Correction

Tous les écrans passent par `CasinoScreen.send`. En **1.20.1**, cette méthode
utilise le canal `gamblingitems:menu_button` : deux VarInt conservent le numéro du
menu et l'action. Fabric enregistre un récepteur serveur ; Forge/NeoForge 47
utilise un `SimpleChannel` limité à `PLAY_TO_SERVER`. Les deux exécutent le même
handler sur le thread serveur.

`MenuButton.handle` vérifie le joueur vivant, non spectateur, le numéro du menu,
son appartenance aux neuf jeux et `stillValid`, avant de laisser le menu appliquer
ses validations de propriétaire, de mise et de cadence. Les messages incomplets
ou contenant des octets supplémentaires sont rejetés. Après un changement
d'onglet, la synchronisation concerne le nouveau menu.

Cinq menus (Slots, Crash, Roulette, Blackjack, Bingo) traitaient les onglets avant
la vérification du propriétaire et de la validité du menu. Cette vérification
précède maintenant les onglets, comme dans les quatre autres menus.

En **1.21.1**, le paquet natif utilise déjà deux VarInt : sa transmission est
conservée. Les autres versions Minecraft n'ont pas été exécutées dans cet audit.
En multijoueur 1.20.1, mettre à jour le client et le serveur ensemble.

## Vérifications

- Builds de production **Fabric et Forge/NeoForge 1.20.1**, Java 17.
- **14 tests JUnit en 1.20.1**, dont toutes les actions des neuf jeux, les 49 zones
  de roulette, les 32767 sélections de l'Upgrader, toutes les caisses possibles,
  les manches des batailles et les neuf onglets. Reproduction de l'ancien `1000`
  tronqué en `-24`, et rejet des messages malformés.
- **122 GameTests réussis** sur la base partagée **Fabric 1.21.1** : règles de
  jeu intégrées, mises, paiements, annulations, inventaires, tables et blocs.
  Les nouveaux tests traversent le codec et le handler avec de vrais menus :
  tous les changements d'onglet de chaque famille, menus fermés/remplacés,
  spectateurs, perte d'accès, actions inconnues et joueurs étrangers.
- Un scénario Slots passe deux diamants dans une partie gagnante via le paquet,
  vérifie l'animation, collecte les six diamants de profit et refuse les doublons.
- **92 tests du moteur indépendant de Minecraft**.
- **4 tests Python** des sources et ressources générées : aucun écran 1.20.1 ne
  doit contourner le nouveau canal, et le paquet natif doit rester actif en 1.21.1.
- Vérification du JAR Forge/NeoForge : déclaration et mappings de la refmap Mixin.

Les GameTests vérifient le code de jeu partagé sur 1.21.1 ; ils ne remplacent pas
un essai interactif du client 1.20.1. Cet audit ne comprend pas une session
multijoueur réelle ni une vérification visuelle de toutes les animations.
Les JAR locaux proviennent du workspace actuel, y compris ses autres changements
en cours. Aucune publication n'a été effectuée.

## Reproduction

```powershell
# Java 17 doit être détectable par Gradle pour les ports 1.20.1.
.\ports\gradlew.bat -p ports/forge1201 '-Pminecraft_version=1.20.1' '-Ppython=python' build
.\ports\gradlew.bat -p ports/fabric120 '-Pminecraft_version=1.20.1' '-Ppython=python' build
.\gradlew.bat -p platforms/fabric-1.21.1 build runGameTest
python -m unittest discover -s tools/tests
```
