# Gambling Items

Cible actuelle : **Minecraft Java 1.21.1 · Fabric · Java 21**.

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

Les manches partag�es (Crash, roulette, battles) appartiennent au serveur, pas au bloc : d�charger le chunk d'une borne ou la casser n'interrompt pas une manche et ne lib�re aucune mise. Un arr�t du serveur annule une manche non r�gl�e et rend chaque mise engag�e une seule fois. Le terminal portable rejoint une table annonc�e par une borne � moins de 64 blocs ; il n'en cr�e jamais.

Aucun support NeoForge ou 26.2 n'est livr� pour le moment.

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
```

Sur Linux/macOS, employer `bash ./gradlew` à la place de `.\gradlew.bat`.

`runClient`, `runServer` et `buildFabric` sont des raccourcis à la racine vers le projet Fabric 1.21.1. La forme explicite `.\gradlew.bat -p platforms/fabric-1.21.1 runClient` reste disponible. Les commandes `build` et `test` à la racine concernent uniquement le cœur Java et ne chargent pas Loom.

Le JAR du mod se trouve dans `platforms/fabric-1.21.1/build/libs/`. Le fichier sans suffixe `-sources` embarque le cœur Java ; seul Fabric API doit être installé en plus dans une instance Fabric Minecraft 1.21.1. Ne pas installer le JAR du cœur séparément.

## Organisation

```text
core/src/                     Règles Java et tests, sans Minecraft
platforms/fabric-1.21.1/       Intégration Fabric et API Minecraft 1.21.1
build.gradle                  Compilation indépendante du cœur
gradle/                       Wrapper de la cible de développement actuelle
CONCEPTION.md                 Mécaniques et périmètre des six jeux
ARCHITECTURE.md               Frontières techniques et futurs portages
```

Chaque adaptateur référence le cœur depuis ses sources via un build composite Gradle. Le cœur est compilable sans configurer Loom. Le projet Fabric sépare déjà les sources communes (`src/main/java`) et les futures sources client (`src/client/java`).

Dans IntelliJ IDEA, ouvrir le build `platforms/fabric-1.21.1` pour développer le mod ; le cœur est inclus automatiquement. Ouvrir le dossier racine suffit pour travailler uniquement sur les règles.

## Documents

- [Conception](CONCEPTION.md)
- [Architecture et portages](ARCHITECTURE.md)

Le wrapper Gradle provient du [projet officiel Gradle, version 8.12.1](https://github.com/gradle/gradle/tree/v8.12.1). La somme SHA-256 de sa distribution est fixée dans `gradle-wrapper.properties`.
