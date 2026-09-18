# Gambling Items

Cible actuelle : **Minecraft Java 1.21.1 · Fabric · Java 21**.

Le projet prépare un mod avec Upgrader, Crash, Trade Up, caisses animées, roulette et battles de caisses. Accès prévu par terminal portable et bornes multijoueurs.

## État actuel

Cinq jeux sont jouables : **l'Upgrader**, le **Trade Up**, les **caisses**, le **Crash** et la **roulette**. Le terminal portable ouvre un menu d'accueil listant les six modes ; seules les battles y sont affichées mais désactivées. Chaque jeu dispose aussi de sa borne posable, dont l'écran montre le tirage aux joueurs proches.

- **Upgrader** : une mise, une cible choisie dans le catalogue, une chance affichée avant l'engagement.
- **Trade Up** : cinq objets de valeur comparable (rapport 1,25 au maximum) contre un gain tiré dans un contrat affiché avant l'échange. Chaque gain possible vaut plus qu'un objet déposé, sans forcément valoir leur somme.
- **Caisses** : un prix annoncé en objets, une table de gains pondérée et un ruban qui s'arrête sur le tirage. Les chances affichées sont calculées à partir des poids configurés ; les trois caisses par défaut rendent environ 90 % du prix en moyenne.
- **Crash** : une manche commune hébergée par une borne. Chacun mise dans la matière de la table (diamant par défaut), voit la même courbe et encaisse quand il veut. Le seuil de crash reste secret jusqu'au crash et chaque seuil de retrait rend la même part configurée (95 % par défaut). Il n'y a pas de mise maximale fixe : la limite est ce que le serveur peut te payer, c'est-à-dire la place restante dans tes gains.
- **Roulette** : une roue commune de 15 cases (7 rouges, 7 noires, 1 verte) hébergée par une borne. Rouge et noir paient 2×, vert paie 14×, mise comprise ; les trois paris rendent la même part, 14/15 soit 93,33 %. Les paris sont verrouillés avant la rotation et le résultat est commun à tous.

Le serveur décide de chaque résultat avant l'animation, consomme la mise une seule fois et garde les gains non récupérés dans un coffre par joueur et par jeu, accessible depuis n'importe quel terminal ou borne.

Les manches de Crash et de roulette appartiennent au serveur, pas au bloc : décharger le chunk d'une borne ou la casser n'interrompt pas un vol et ne libère aucune mise. Un arrêt du serveur annule une manche non réglée et rend chaque mise engagée une seule fois. Le terminal portable rejoint une table annoncée par une borne à moins de 64 blocs ; il n'en crée jamais.

Les valeurs des objets, les réglages des jeux et les caisses sont dans `config/gamblingitems/games.json`, créé au premier démarrage. Un fichier écrit par une version précédente est migré au démarrage sans perdre les valeurs ni les réglages existants ; un ancien `upgrader.json` est également repris pour conserver les prix d'un monde existant.

Chaque caisse y déclare son identifiant, son nom, son prix (objet et quantité) et sa table : objet, quantité et poids relatif. Les poids sont normalisés au chargement et les probabilités affichées en découlent. Le prix et chaque gain doivent être cotés dans `values`, sinon le fichier est refusé avec le nom de l'objet fautif.

Le d�lai de mise par d�faut de la roulette et du Crash est de **3 secondes** (60 ticks). Au premier d�marrage avec le sch�ma 8, les anciennes dur�es de 200 ticks passent � 60 ; les autres dur�es personnalis�es sont conserv�es.

La section `crash` fixe la matière des mises, la mise minimale, le rendement, le multiplicateur maximal, la croissance par tick et les durées des phases. La section `roulette` fixe la matière, la mise minimale, le nombre de cases de chaque couleur, leurs multiplicateurs et les durées des phases. Une table dont la plus petite mise ne pourrait pas être payée est refusée au chargement.

**Les battles de caisses ne sont pas encore implémentées.** Aucun support NeoForge ou 26.2 n'est livré pour le moment.

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
