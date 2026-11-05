# Un outil fonctionne chez l'hôte, mais pas chez l'invité

## Conclusion

Le défaut de boutons Minecraft **1.20.1**, corrigé dans la **0.2.2**, peut produire
exactement cette différence entre l'hôte d'un monde partagé et son ami. Une
reproduction du transport local et TCP confirme le mécanisme.

La version Minecraft du joueur et le point précis où l'outil échoue ne sont
pas connus. Cette analyse ne prouve donc pas encore la cause de son ticket.

## Pourquoi l'hôte peut être épargné

Les sources Minecraft 1.20.1 utilisées par Forge 47.1.106 montrent deux chemins :

- `Connection.connectToLocalServer` utilise un `LocalChannel` Netty, sans les
  encodeurs et décodeurs des paquets TCP. Le paquet Java peut arriver avec son
  identifiant entier intact dans le serveur intégré de l'hôte.
- `Connection.connect` configure la sérialisation du canal TCP. Le paquet natif
  `ServerboundContainerButtonClickPacket` écrit et relit son identifiant de bouton
  comme un octet signé.

`MultiPlayerGameMode.handleInventoryButtonClick` ne réduit pas l'entier avant
de construire ce paquet. `ServerGamePacketListenerImpl.handleContainerButtonClick`
transmet ensuite l'identifiant reçu à `clickMenuButton`.

| Action de l'ancien mod | Hôte : transport local | Invité : transport TCP | Paquet 0.2.2 : TCP |
| --- | ---: | ---: | ---: |
| Action `1000` | 1000 | -24 | 1000 |
| Lancement Upgrader `32767` | 32767 | -1 | 32767 |
| Premier onglet `40000` | 40000 | 64 | 40000 |

Ce n'est donc pas nécessairement une restriction de permissions. Le serveur peut
recevoir une action différente selon le transport, malgré un clic identique.

## Reproduction vérifiée

`MenuButtonTransportTest` ouvre de vrais canaux Netty : un `LocalChannel` et une
connexion TCP sur `127.0.0.1` avec un port attribué automatiquement.

- Le test de l'ancien comportement transfère le véritable paquet Minecraft
  1.20.1. Les identifiants restent intacts en local et sont tronqués sur TCP.
- Le test du correctif transfère le codec `MenuButton` de la 0.2.2 sur TCP.
  Les douze identifiants de contrôle, dont `128`, `1000`, `32767` et `40008`,
  arrivent intacts.
- Le test TCP utilise un préfixe de longueur propre au banc de test pour séparer
  les messages. Il teste les vrais encodeurs de charge utile du paquet, mais ne
  reproduit pas la connexion complète d'un client Minecraft, son authentification
  ou la négociation Forge.
- Les **16 tests JUnit du port 1.20.1 passent**, dont ces deux nouvelles
  reproductions. Aucune modification supplémentaire du code de jeu n'a été
  nécessaire après la release 0.2.2.

Les JAR envoyés pour la 0.2.2 restent identiques aux empreintes de leur rapport
de vérification. Les nouveaux tests et ce diagnostic sont sur la branche
`codex/investigate-host-only-transport`, après le tag de release.

## Ce qu'il reste à confirmer auprès du joueur

1. Versions exactes de Minecraft, du chargeur et du mod, chez les deux joueurs.
2. Le menu s'ouvre-t-il, puis ses boutons ne répondent-ils pas ? Ou l'objet
   n'ouvre-t-il aucun menu ? Le défaut d'identifiant explique le premier cas,
   pas à lui seul le second.
3. Monde ouvert en LAN, outil de partage de monde ou serveur dédié.

Si les deux joueurs sont en 1.20.1, faire installer la 0.2.2 à l'hôte/serveur et
à l'invité, puis comparer les actions du même outil. Avec un serveur dédié,
les deux joueurs passent normalement par TCP : la distinction hôte/invité
du serveur intégré ne s'applique pas de la même manière.

Le paquet natif de la base Minecraft 1.21.1 utilise déjà des VarInt. Cette cause
précise ne doit pas être étendue à cette version sans nouvelle preuve. Les
versions intermédiaires 1.20.2 à 1.20.4 n'ont pas été vérifiées dans ce diagnostic.

## Refaire le test

```powershell
.\ports\gradlew.bat -p ports/forge1201 '-Pminecraft_version=1.20.1' '-Ppython=python' test --tests '*MenuButtonTransportTest'
```

Un JDK 17 doit être détectable par Gradle. Si un client de développement utilise
déjà les fichiers du port sous Windows, passer aussi un `-PreleaseBuildDir`
distinct pour éviter de modifier ses JAR ouverts.
