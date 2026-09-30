# Gambling Items

Cibles actuelles : **Minecraft Java 1.21.1 · Fabric et NeoForge · Java 21**.

Le projet prépare un mod avec Upgrader, Crash, Trade Up, caisses animées, roulette et battles de caisses. Accès prévu par terminal portable et bornes multijoueurs.

## �tat actuel

Les sept jeux sont jouables : **l'Upgrader**, le **Trade Up**, les **caisses**, le **Crash**, la **roulette**, le **blackjack** et les **battles de caisses**. Le terminal portable ouvre une galerie d'ic�nes ; chaque jeu a aussi sa borne posable, dont l'�cran se manipule directement dans le monde, sans ouvrir d'inventaire, et montre aux joueurs proches qui joue et ce qu'il a mis�.

- **Upgrader** : une mise, une cible choisie dans le catalogue, une chance affich�e avant l'engagement.
- **Trade Up** : cinq objets de valeur comparable (rapport 1,25 au maximum) contre un gain tir� dans un contrat affich� avant l'�change.
- **Caisses** : six caisses, une par raret� de cl�. Les cl�s se trouvent sur les cr�atures tu�es par un joueur : mobs ordinaires pour les cl�s communes et peu communes, Nether et End pour les rares et �piques, champions (wither squelette, brute piglin, �vocateur, ravageur) pour les l�gendaires, boss (gardien ancien, warden, wither, dragon) pour les mythiques. Une cl� n'a pas de valeur marchande : elle ouvre sa caisse, et rien d'autre.
- **Crash** : une manche commune h�berg�e par une borne. Chacun mise les objets cot�s qu'il veut, voit la m�me courbe et encaisse quand il veut. Le seuil de crash reste secret jusqu'au crash et chaque seuil de retrait rend la m�me part configur�e (95 % par d�faut). Pas de mise maximale : la seule limite est ce que le serveur peut te payer.
- **Roulette** : la vraie roue europ�enne, 0 � 36, avec sa table. On pose les objets que l'on veut sur les cases que l'on veut (num�ro plein, rouge, noir, pair, impair, manque, passe, douzaines, colonnes) et la roue tourne pour tout le monde. Chaque pari rend 36/37, soit 97,3 %.
- **Blackjack** : le croupier reste � 17, un blackjack paie 3:2, l'�galit� rend la mise. La carte cach�e du croupier n'est jamais envoy�e au client avant d'�tre retourn�e.
- **Battles de caisses** : deux � quatre places, la m�me caisse ouverte manche apr�s manche, le meilleur total rafle tous les objets ouverts. L'entr�e se paie en cl�s et revient une seule fois si la place est quitt�e avant le lancement.

Toute mise se fait en objets cot�s, jamais dans une mati�re impos�e : c'est leur valeur qui est jou�e. Un gain est rendu en objets du catalogue, du plus cher au moins cher ; ce qui reste sous l'objet le moins cher ne peut pas �tre pay� en objets et constitue l'arrondi annonc�.

Le serveur d�cide de chaque r�sultat avant l'animation, consomme la mise une seule fois et garde les gains non r�cup�r�s dans un coffre par joueur et par jeu, accessible depuis n'importe quel terminal ou borne.

Les manches partagées (Crash, roulette, bingo, battles) appartiennent au serveur. Les items portables rejoignent une table à moins de 64 blocs ou en ouvrent une sur place sans bloc. Fermer une interface ou casser une borne n'interrompt pas une manche engagée. Un arrêt du serveur annule une manche non réglée et rend chaque mise engagée une seule fois.

Aucun support NeoForge ou 26.2 n'est livr� pour le moment.

## Items autonomes et catalogue de valeurs

Chaque jeu possède son item craftable, utilisable directement au clic droit sans borne. L'Upgrader propose les objets des recettes vanilla et modées, avec recherche par nom ou `@mod` et une chance recalculée pour chaque cible. Les prix sont estimés à partir des recettes et peuvent être corrigés dans `config/gamblingitems/games.json`.

Voir [Items portables et valeurs](ITEMS_AND_VALUES.md) pour les recettes, les limites du calcul automatique et les réglages d'un modpack.

## Développement

Installer un JDK 21 et exécuter les commandes suivantes depuis ce dossier. Le wrapper télécharge Gradle ; le premier build Fabric télécharge aussi les dépendances Minecraft. Les dépendances de développement sont épinglées dans les fichiers Gradle.

```powershell
# Compiler le cœur et exécuter ses tests, sans dépendances Minecraft
.\gradlew.bat build

# Compiler le mod Fabric et vérifier également le cœur
.\gradlew.bat buildFabric

# Démarrer le client de développement
.\gradlew.bat runClient

# Démarrer le serveur de développement (son EULA doit être accepté par l'utilisateur)
.\gradlew.bat runServer

# Les mêmes pour NeoForge
.\gradlew.bat buildNeoForge
.\gradlew.bat runNeoForgeClient
.\gradlew.bat runNeoForgeServer

# Démarrer un serveur NeoForge, lancer ses GameTests de démarrage puis l'arrêter
.\gradlew.bat -p platforms/neoforge-1.21.1 runGameTestServer
```

Sur Linux/macOS, employer `bash ./gradlew` à la place de `.\gradlew.bat`.

`runClient`, `runServer` et `buildFabric` sont des raccourcis à la racine vers le projet Fabric 1.21.1. La forme explicite `.\gradlew.bat -p platforms/fabric-1.21.1 runClient` reste disponible. Les commandes `build` et `test` à la racine concernent uniquement le cœur Java et ne chargent pas Loom.

Le JAR du mod se trouve dans `platforms/fabric-1.21.1/build/libs/`. Le fichier sans suffixe `-sources` embarque le cœur Java ; seul Fabric API doit être installé en plus dans une instance Fabric Minecraft 1.21.1. Ne pas installer le JAR du cœur séparément.

Le JAR NeoForge se trouve dans `platforms/neoforge-1.21.1/build/libs/` et embarque lui aussi le cœur ; il ne demande que NeoForge 21.1 pour Minecraft 1.21.1.

### Fabric et NeoForge

Le code du jeu est écrit contre Minecraft vanilla et vit dans `platforms/fabric-1.21.1/src`. Le projet NeoForge compile ces mêmes sources, sans copie, et remplace seulement les fichiers propres au loader :

- `GamblingItemsFabric` et `GamblingItemsClient` (points d'entrée) : `GamblingItemsNeoForge` et `GamblingItemsNeoForgeClient` ;
- `platform/Platform` (dossier de config, types de menus, ouverture d'un menu avec données) : une version NeoForge de même nom et mêmes signatures.

Hors de ces fichiers, le code partagé ne doit importer aucune API Fabric ou NeoForge. Toute nouvelle dépendance au loader passe par `Platform`, avec une implémentation dans chaque projet. La liste des fichiers exclus est dans `platforms/neoforge-1.21.1/build.gradle`.

## Organisation

```text
core/src/                     Règles Java et tests, sans Minecraft
platforms/fabric-1.21.1/       Intégration Fabric et code de jeu Minecraft 1.21.1 partagé
platforms/neoforge-1.21.1/     Intégration NeoForge, compile les sources partagées du projet Fabric
build.gradle                  Compilation indépendante du cœur
gradle/                       Wrapper de la cible de développement actuelle
CONCEPTION.md                 Mécaniques et périmètre des six jeux
ARCHITECTURE.md               Frontières techniques et futurs portages
```

Chaque adaptateur référence le cœur depuis ses sources via un build composite Gradle. Le cœur est compilable sans configurer Loom. Le projet Fabric sépare déjà les sources communes (`src/main/java`) et les futures sources client (`src/client/java`).

Dans IntelliJ IDEA, ouvrir le build `platforms/fabric-1.21.1` pour développer le mod ; le cœur est inclus automatiquement. Ouvrir le dossier racine suffit pour travailler uniquement sur les règles.

## Documents

- [Préparation et publication CurseForge](release/curseforge/UPLOAD-FR.md) — générer le dossier avec `.\gradlew.bat prepareCurseForge`.

- [Conception](CONCEPTION.md)
- [Architecture et portages](ARCHITECTURE.md)

Le wrapper Gradle provient du [projet officiel Gradle, version 8.12.1](https://github.com/gradle/gradle/tree/v8.12.1). La somme SHA-256 de sa distribution est fixée dans `gradle-wrapper.properties`.
