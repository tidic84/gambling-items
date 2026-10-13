# Crash au lancement de Gambling Items 0.2.0 sur Minecraft 1.20.1

## Symptôme et périmètre

Le JAR CurseForge **9085897**, nommé `gambling-items-1.20.1-neoforge-0.2.0.jar`, plante pendant l'initialisation du client sur **Forge 47.4.26** et **NeoForge 47.1.106**, avec **Java 17**. Le crash est reproductible avec Gambling Items seul, sans JEI ni Fabric API. Les mêmes installations démarrent sans le mod.

SHA-256 du fichier concerné :

```text
059c278dd269c4bc65025c3813192cb4990290511d318ad6041f63b17ba84fed
```

Ces tests identifient un défaut réel de la release ; les commentaires CurseForge ne donnent pas suffisamment d'informations pour attribuer leur crash à ce défaut avec certitude.

## Cause

Le rapport de crash indique :

```text
Mixin [gamblingitems.client.mixins.json:MouseGrabMixin] FAILED during APPLY
@Inject annotation on gamblingitems$keepCursor could not find any targets
matching 'grabMouse' in net.minecraft.client.MouseHandler. No refMap loaded.
```

`gamblingitems.client.mixins.json` omet la propriété `refmap`. Le JAR **contient déjà** `gamblingitems.refmap.json`, et ses mappings sont corrects. Une première inspection avait indiqué à tort que ce fichier était absent : c'est sa déclaration qui manque.

Les noms présents dans les annotations des mixins sont des noms de développement. En production Forge/NeoForge 1.20.1, la refmap doit les traduire :

| Cible | Nom de développement | Nom du runtime 1.20.1 |
|---|---|---|
| Injection de `MouseGrabMixin` | `grabMouse()V` | `m_91601_()V` |
| Accesseur X de `MouseHandlerAccessor` | `xpos:D` | `f_91507_:D` |
| Accesseur Y de `MouseHandlerAccessor` | `ypos:D` | `f_91508_:D` |

Les noms ont été vérifiés en croisant les mappings Mojang et SRG de Minecraft 1.20.1. Le fonctionnement de la résolution des références est décrit dans le [code de ReferenceMapper de Mixin](https://github.com/SpongePowered/Mixin/blob/master/src/main/java/org/spongepowered/asm/mixin/refmap/ReferenceMapper.java).

## Correction

Ajouter cette propriété dans le **fichier de configuration des mixins du portage 1.20.1**, au même niveau que `package` et `client` :

```json
"refmap": "gamblingitems.refmap.json"
```

Ne pas désactiver les mixins ni diminuer `defaultRequire` : l'injection et les accesseurs doivent continuer à s'appliquer. La refmap existante et tous les fichiers `.class` restent inchangés.

Après récupération de la dernière branche `main` de GitHub (`0e16e5f`), le correctif est également appliqué aux sources : `tools/port.py` ajoute la déclaration dans la configuration générée pour `neoforge` / `1.20.1`, cible construite par `ports/forge1201`. La version du projet passe à **0.2.1**. Les autres configurations générées ne reçoivent pas de déclaration vers une refmap qui n'existerait pas chez elles.

La tâche `verifyMixinRefmap` de `ports/forge1201/build.gradle`, exécutée par `check` et `build`, inspecte le JAR final : elle exige la déclaration, le fichier embarqué et les trois mappings SRG de la méthode et des accesseurs. Un test serveur seul ne pouvait pas détecter cette panne, car ces mixins ne s'appliquent qu'au client.

Le **hotfix binaire 0.2.0-hotfix.1** ci-dessous reste disponible pour reproduire un changement minimal sur le fichier exactement publié : il ne modifie aucun bytecode. Le build depuis les sources produit la release **0.2.1**.

## Générer le JAR corrigé

Python 3.9 ou plus récent suffit ; aucune bibliothèque supplémentaire n'est nécessaire :

```powershell
python scripts/hotfix-1.20.1.py `
  chemin/vers/gambling-items-1.20.1-neoforge-0.2.0.jar `
  build/hotfix/gambling-items-1.20.1-forge-0.2.0-hotfix.1.jar
```

Le script refuse un fichier d'entrée dont le SHA-256 diffère de la release auditée, conserve l'original, refuse d'écraser une sortie existante, vérifie les mappings embarqués et contrôle l'intégrité de la sortie. Tous les fichiers d'origine sont préservés octet pour octet, sauf :

- `gamblingitems.client.mixins.json` : déclaration de la refmap ;
- `META-INF/mods.toml` : version `0.2.0-hotfix.1`, pour identifier le correctif dans les logs.

SHA-256 du JAR corrigé :

```text
76c76cb77c9c159ae3726acd6c1cdd37b837791fc54c88f59fc853d300db1a8b
```

Remplacer le JAR 0.2.0 par le JAR corrigé dans `mods` ; ne pas installer les deux en même temps. Ce fichier cible uniquement Minecraft 1.20.1.

## Validation

La validation utilise un JAR de production corrigé, et pas seulement un lancement de développement : celui-ci peut utiliser les noms Mojang et masquer une déclaration de refmap manquante. La création du hotfix a été répétée : les deux sorties ont le même SHA-256. Le script refuse aussi un JAR 1.21.1 et refuse d'écraser une sortie existante.

Commande de compilation du portage depuis les sources :

```powershell
.\ports\gradlew.bat -p ports/forge1201 build '-Pminecraft_version=1.20.1' '-Ppython=python'
```

Java 17 est nécessaire. Le JAR est produit dans le dossier de build configuré par `ports/common.gradle`, actuellement `~/.cache/gambling-items-ports/gambling-items-forge1201/libs/`.

Le build **0.2.1** a réussi, y compris `reobfJar`, `verifyMixinRefmap` et `check`. Une copie du JAR compilé depuis les sources est livrée dans `build/release/0.2.1/gambling-items-1.20.1-neoforge-0.2.1.jar`. Son nom conserve la convention du projet ; il cible les deux chargeurs 1.20.1 testés.

SHA-256 de cette compilation :

```text
a0356d1de32f83d63f8ed75df3932a37325d921bf45f092f77b7716b003ecc8e
```

Tests du générateur, sans dépendances Minecraft :

```powershell
python -m unittest discover -s tools/tests -v
```

Les deux tests passent : déclaration obligatoire sur le portage 1.20.1, absence de déclaration vers cette refmap sur les autres portages vérifiés (Fabric 1.20.1 et NeoForge 1.21.1). Le test de déclaration a aussi été exécuté contre le générateur GitHub non corrigé : il échoue bien avec `None != 'gamblingitems.refmap.json'`.

| JAR de production testé | Chargeur | Résultat |
|---|---|---|
| 0.2.0 publié | Forge 47.4.26 | Crash de `MouseGrabMixin` avant le chargement du mod |
| 0.2.0 publié | NeoForge 47.1.106 | Même crash |
| 0.2.0-hotfix.1 | Forge 47.4.26 | Mod et ressources chargés, processus actif jusqu'à son arrêt après plusieurs minutes lors du dernier essai |
| 0.2.0-hotfix.1 | NeoForge 47.1.106 | Mod et ressources chargés, processus actif jusqu'à l'arrêt de l'essai |
| 0.2.1, compilé depuis les sources | Forge 47.4.26 | Mod et ressources chargés, client actif jusqu'à son arrêt, sans l'erreur initiale |
| 0.2.1, compilé depuis les sources | NeoForge 47.1.106 | Mod et ressources chargés, client actif jusqu'à son arrêt, sans l'erreur initiale |

Les logs avant/après sont conservés dans `build/hotfix/evidence/`. Un premier essai du hotfix Forge a ensuite subi une violation d'accès native dans `glfw.dll`, après le chargement réussi des mixins et des ressources. Des incidents GLFW avaient aussi eu lieu pendant les essais précédents de la 1.21.1. Sa cause exacte n'est pas établie ; le dernier essai Forge a été relancé sans automatisation de fenêtre et n'a pas reproduit cette violation avant son arrêt. Cela ne certifie pas la stabilité générale du client ni l'ensemble du gameplay.

Les logs du **JAR 0.2.1 compilé depuis les sources** et le rapport de validation sont dans `build/release/0.2.1/`. Les tests des deux chargeurs utilisent des dossiers de jeu distincts et Java 17, sans JEI ni Fabric API. Aucun crash natif GLFW n'a été observé sur ces deux essais 0.2.1 avant leur arrêt. Les avertissements Realms proviennent du profil de test hors ligne.

Avant publication d'une future release, contrôler que le JAR contient le fichier de configuration et la refmap référencée, puis effectuer un lancement client en production sur les deux chargeurs. Vérifier aussi que la refmap couvre les accesseurs, pour ne pas corriger uniquement la première erreur rencontrée.

## Compatibilité Forge

L'étiquette Forge du fichier 1.20.1 correspond à son contenu : `META-INF/mods.toml` déclare `forge` avec `versionRange="[47,)"`, et le code utilise l'API `net.minecraftforge`. Le crash initial ne provient pas de l'étiquette Forge ; il se reproduit également avec NeoForge 1.20.1. Cette conclusion ne doit pas être étendue aux autres versions de Minecraft.

Les JAR 0.2.0 NeoForge pour 1.21.1 et 26.3 ont atteint le menu principal dans les essais précédents. Aucun changement ne leur est appliqué. Les essais sont limités au démarrage du client ; ils ne certifient pas l'ensemble des mécaniques en jeu ni les modpacks des utilisateurs.
