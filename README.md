# Gambling Items

Cible actuelle : **Minecraft Java 1.21.1 · Fabric · Java 21**.

Le projet prépare un mod avec Upgrader, Crash, Trade Up, caisses animées, roulette et battles de caisses. Accès prévu par terminal portable et bornes multijoueurs.

## État actuel

Socle technique uniquement : cœur Java indépendant, règle de probabilité d'upgrade testable, identifiants des six modes et initialisation Fabric avec commande `/gamblingitems info`.

**Les jeux, GUI, blocs, mises, paiements et sauvegardes ne sont pas encore implémentés.** La commande d'information liste les modes prévus ; elle n'ouvre pas d'interface et ne consomme aucun objet. Aucun support NeoForge ou 26.2 n'est livré pour le moment.

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
