# Architecture et portages

## Décision

Développer d'abord **Fabric 1.21.1**. Faciliter ensuite les portages vers **26.2** et **NeoForge**, sans imposer au premier mod les dépendances de toutes les autres cibles.

Deux axes doivent rester distincts :

- Le chargeur de mods : points d'entrée, enregistrement, événements, réseau et intégrations Fabric/NeoForge.
- La version Minecraft : inventaires, composants d'objets, recettes, persistance, écrans et rendu.

Isoler uniquement Fabric ne suffit donc pas à rendre un mod indépendant de la version Minecraft.

## Frontières

| Partie | Dépendances autorisées | Contenu |
| --- | --- | --- |
| Cœur | Java standard | Règles, probabilités, valeurs, futurs états de session et contrats |
| Adaptateur de cible | Cœur, Minecraft, API du chargeur | Inventaires, réseau, sauvegardes, blocs, commandes et raccordement au serveur |
| Client de cible | Cœur, Minecraft client, API client du chargeur | GUI, animations, sons et rendu des bornes |
| Ressources | Format adapté à chaque version | Traductions, textures, modèles et définitions de données |

Le cœur ne reçoit pas de `ItemStack`, `ServerPlayer`, `Level`, NBT, classe Fabric ou classe NeoForge. Il reçoit des données métier immuables : identifiant, valeur dans une unité commune, quantité, identifiant de joueur/session et temps logique.

La représentation fidèle d'un objet Minecraft reste dans l'adaptateur serveur. Un identifiant et une quantité ne suffisent pas à sauvegarder des enchantements, composants et inventaires embarqués.

Les interfaces de stockage, d'horloge et de paiement seront introduites avec leurs premiers usages réels. Ne pas construire une abstraction générale de toutes les API Minecraft dès maintenant.

## Compilation

Le build racine produit `dev.gamblingitems:gambling-items-core`. Il n'applique pas Loom et n'a pas de dépendance d'exécution autre que Java.

Il expose aussi trois raccourcis explicites : `runClient`, `runServer` et `buildFabric`. Lorsqu'un de ces noms complets est demandé à la racine, les settings incluent le build Fabric et le raccourci délègue à sa tâche correspondante. Une compilation ou un test du cœur seul n'inclut pas Fabric et ne configure pas Loom.

`platforms/fabric-1.21.1` est un build indépendant qui inclut le cœur avec `includeBuild('../..')`. Fabric Loom inclut le JAR du cœur dans le JAR final. Les tests du cœur font partie de la tâche `check` du build Fabric.

Cette séparation évite de charger différentes générations de Loom, de Gradle et des outils NeoForge dans le même build. Lorsqu'une cible 26.2 sera ajoutée, elle pourra avoir son propre wrapper et son propre JDK ; le cœur conservera une cible de bytecode Java 21 tant que possible.

Les versions de dépendances de chaque adaptateur sont épinglées. Le numéro de version du mod vient du fichier `gradle.properties` racine.

## Pourquoi les noms officiels Mojang sur 1.21.1

Le build Fabric utilise `loom.officialMojangMappings()`. Cela réduit une source de divergence de noms lors d'un portage NeoForge 1.21.1. Cela ne remplace pas l'adaptation des API propres au chargeur ou les changements entre versions Minecraft.

## Préparer le rendu

L'état affichable doit rester séparé du dessin : phase, multiplicateur public, liste des lots affichés, résultat public et progression d'animation.

Les commandes de dessin Minecraft restent dans le client de la cible. Un futur écran 26.2 pourra consommer le même modèle d'affichage avec un autre code de rendu. Éviter les accès directs OpenGL et les mixins de rendu qui ne sont pas nécessaires.

Les animations utilisent la phase et le temps fournis par le serveur. Leur fluidité locale ne doit jamais modifier les mises ou le résultat. Les données publiques ne contiennent pas le seuil secret de crash.

Le moteur de rendu a évolué entre 1.21.1 et 26.2 : la documentation Fabric actuelle décrit une extraction d'état et indique que l'OpenGL brut n'est pas pris en charge en 26.2 avec son backend Vulkan optionnel. Ce portage demandera du code spécifique, même avec un cœur partagé. [Documentation du rendu Fabric](https://docs.fabricmc.net/develop/rendering/basic-concepts).

## Préparer les données et le réseau

Employer des identifiants explicites stables pour les modes et les objets, jamais les ordinaux d'enum ni les identifiants numériques temporaires des registres.

Versionner séparément le format de sauvegarde, la configuration et le protocole réseau lorsqu'ils seront ajoutés. Une migration de données doit être explicite et testée sur une copie d'une ancienne sauvegarde. Un client et un serveur incompatibles doivent refuser la session avec une explication.

Les messages métier peuvent être partagés ; leur encodage et leur transport restent dans la cible. Le serveur valide chaque action. Les opérations qui touchent Minecraft sont exécutées sur le thread serveur approprié.

Les JSON de recettes, modèles et composants peuvent aussi changer de format entre versions : partager leur contenu conceptuel ne garantit pas que les mêmes fichiers fonctionnent partout.

## Portage NeoForge

1. Choisir la version Minecraft à porter.
2. Créer un build `platforms/neoforge-<version>` avec l'outillage officiel correspondant.
3. Réutiliser le cœur et ses tests sans import Fabric.
4. Adapter l'enregistrement, les événements, le réseau, la configuration et les branchements client/serveur.
5. Si du code Minecraft identique est réellement partagé à une même version, l'extraire dans un module `minecraft-common-<version>`. Aucun code Fabric ou NeoForge n'y entre.
6. Vérifier les six jeux sur client et serveur dédié avant d'annoncer le support.

Architectury n'est pas ajouté à ce stade : la séparation Java couvre le besoin immédiat. Il pourra être évalué lors du second chargeur si la duplication réelle le justifie. Il ne garantit pas à lui seul la compatibilité entre versions Minecraft.

## Portage 26.2

1. Créer un build distinct de la cible 1.21.1 ; conserver le build existant fonctionnel.
2. Configurer le JDK et le Gradle requis par les outils 26.2. Fabric documente déjà Java 25 minimum et le passage à un Loom sans remappage depuis 26.1. [Annonce Fabric 26.1](https://www.fabricmc.net/2026/03/14/261.html).
3. Inclure le cœur Java depuis les mêmes sources. Corriger une règle dans le cœur doit bénéficier aux deux cibles.
4. Adapter les API Minecraft, le réseau, les composants, les sauvegardes, les ressources et le rendu.
5. Tester les migrations de données et les mêmes scénarios multijoueurs sur cette cible.

Le [guide Fabric 26.2](https://docs.fabricmc.net/develop/porting/) couvre le passage de 26.1 à 26.2 ; il ne suffit pas à lui seul pour sauter depuis 1.21.1.

## Ce qui existe aujourd'hui

- Identifiants stables des six modes dans le cœur, lisibles depuis une sauvegarde par leur identifiant.
- Règles pures et testées de l'Upgrader, du Trade Up, des caisses, du Crash, de la roulette, du blackjack, des battles et du rendu de monnaie en objets, sans dépendance Minecraft.
- Catalogue de valeurs commun aux jeux, chargé côté serveur et envoyé à l'ouverture d'un menu.
- Coffres persistants par joueur et par jeu, avec migration explicite depuis le premier format.
- Terminal portable menant à un menu d'accueil, et une borne par jeu avec son écran public.
- Manches partagées de Crash, de roulette et de battles tenues par le serveur, indépendantes des blocs et des chunks chargés.
- Mises en valeur d'objets : un pari est un ensemble d'objets cotés, un gain est rendu en objets du catalogue.
- Build Fabric 1.21.1, inclusion du cœur, tests unitaires du cœur et tests en jeu des sept jeux et des bornes.

La table du Trade Up est reconstruite des deux côtés à partir des mêmes règles pures et des mêmes objets déposés : l'écran affiche donc les chances réelles sans protocole supplémentaire. Le serveur la reconstruit lui-même avant d'accepter l'échange ; l'affichage n'est jamais une source de vérité.

Les tables pondérées des caisses sont normalisées une seule fois au chargement de la configuration : le serveur tire dedans et l'écran affiche exactement les mêmes chances. Les battles de caisses réutiliseront ces tables sans les recalculer.

Le Crash sépare nettement ce qui est public de ce qui ne l'est pas : le multiplicateur et la mise totale sont envoyés à tous, le seuil de crash reste dans le serveur jusqu'au crash. La mise engagée reste dans le coffre du joueur jusqu'au règlement, si bien qu'un arrêt brutal la laisse à son propriétaire au lieu de la perdre. La roulette reprend ce moteur de phases : mises, verrouillage, rotation, paiement. Son tirage est fait au verrouillage et envoyé aux clients, dont la roue ne fait que le rejouer ; seul le Crash garde une valeur secrète, parce qu'il est le seul où l'on peut encore agir pendant la manche.

Les sept jeux sont en place ; ce qui reste porte sur l'équilibrage, les visuels et les portages. Cette architecture facilite leur réutilisation ; elle n'est pas une promesse de portage automatique ni un support déjà testé de NeoForge ou de 26.2.
