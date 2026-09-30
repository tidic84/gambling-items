# Publication CurseForge — Gambling Items

## Fichiers préparés

| Fichier | Usage |
| --- | --- |
| `gambling-items-fabric-1.21.1-0.2.0-beta.2.jar` | Fichier du mod à envoyer dans l'onglet Files |
| `project-icon.png` | Logo du projet |
| `description-en.md` | Description à coller dans l'éditeur Markdown |
| `changelog-en.md` | Notes du fichier à coller dans le changelog |
| `project-fields.json` | Valeurs à reporter dans le formulaire, pas un fichier d'API |
| `LICENSE` | All Rights Reserved, Romalaure |
| `SHA256SUMS.txt` | Empreinte du JAR |
| `verification.json` | Résultat des contrôles du paquet |
| `ITEMS_AND_VALUES.md` | Guide français des recettes et valeurs |

## Créer le projet

Ouvrir le [portail des auteurs CurseForge](https://authors.curseforge.com/#/projects/create/choose-game), puis choisir Minecraft et la classe **Mods**.

- Nom : **Gambling Items**.
- Auteur du mod : **Romalaure**. L'identité du compte CurseForge dépend du compte utilisé pour publier.
- Résumé : copier `summary` depuis `project-fields.json`.
- Logo : envoyer `project-icon.png`.
- Catégorie principale proposée : **Miscellaneous**, si ce choix est proposé dans le formulaire.
- Description : copier `description-en.md` dans le mode Markdown.
- Licence : **All Rights Reserved**.
- Source, facultative : `https://github.com/tidic84/gambling-items`. Le dépôt est public ; les changements locaux de cette préparation n'ont pas été poussés automatiquement.
- Laisser l'option **Experimental** désactivée : la version du fichier sera déjà marquée Beta.

La disponibilité du nom et du slug sera vérifiée par CurseForge lors de la création.

## Envoyer le fichier

Dans **Files / Upload File**, sélectionner uniquement le **JAR du mod** indiqué ci-dessus.

- Nom affiché : **Gambling Items 0.2.0-beta.2 - Fabric 1.21.1**.
- Type : **Beta**.
- Version Minecraft : **1.21.1**.
- Mod loader : **Fabric**.
- Java : **21**, si un champ Java est proposé.
- Changelog : copier `changelog-en.md`.
- Dépendance : ajouter le projet [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api) comme **Required Dependency**.

Le cœur Java est déjà embarqué dans le JAR. Le JAR `-sources`, le JAR du cœur, le ZIP de préparation éventuel et les fichiers du serveur de développement ne sont pas des fichiers de mod à publier séparément.

Une beta n'est pas distribuée automatiquement comme une version Release. Le guide CurseForge indique qu'un projet doit posséder au moins un fichier Release pour être synchronisé avec son application. Cette première version est volontairement préparée en Beta ; la passer en Release après validation du client si la distribution par défaut dans l'application est souhaitée.

## Vérifications restant à faire dans Minecraft

La compilation, les tests unitaires et les tests d'intégration serveur sont exécutés par la commande de préparation. Le rapport ne prétend pas valider un essai visuel du client.

Avant de soumettre la beta, faire un essai du JAR dans une instance Fabric 1.21.1 avec Fabric API : ouverture des interfaces, une partie solo, affichage de la machine à sous et connexion à un serveur possédant le même mod. Les captures en jeu peuvent ensuite enrichir la galerie. Le logo fourni est une illustration de marque, pas une capture du rendu en jeu.

## Régénérer le dossier

À la racine du projet :

```powershell
.\gradlew.bat prepareCurseForge
```

Le dossier de sortie est `dist/curseforge/0.2.0-beta.2/`. La tâche compile la version, exécute les tests, inspecte le JAR et produit son empreinte SHA-256. Pour une version suivante, mettre à jour `gradle.properties`, le changelog et les champs du formulaire avant de relancer.

Aucun fichier n'est envoyé à CurseForge par cette commande.

## Références officielles

- [Création et soumission d'un projet](https://support.curseforge.com/support/solutions/articles/9000197241) : contenu de la fiche en anglais, soumission via l'envoi d'un fichier ; galerie supplémentaire facultative pour les mods.
- [Guide de présentation](https://support.curseforge.com/support/solutions/articles/9000199552) : logo PNG carré original d'au moins 400 × 400 pixels, choix de licence et option Experimental.
