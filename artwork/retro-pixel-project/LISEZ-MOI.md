# Gambling Items — icone retro

Recreation de la troisieme proposition en pixel art : caisse bordeaux, bordures dorees et trois 7. Dessin construit sur une grille de 96 x 96 pixels, 16 couleurs, 12 calques independants. Auteur du projet : Romalaure. Licence : All Rights Reserved (LICENSE a la racine du depot).

## Ouvrir et modifier

- **Krita : gambling-items-retro.kra**, enregistre avec Krita installe sur cet ordinateur.
- **Aseprite / LibreSprite : gambling-items-retro.aseprite**.
- **OpenRaster : gambling-items-retro.ora**, version intermediaire avec les memes calques.

Choisir un calque, utiliser un crayon de 1 pixel sans anticrenelage, puis enregistrer le projet. Les trois symboles 7 ont chacun leur propre calque. Masquer le calque « 01 Fond bleu nuit » pour obtenir la transparence.

## Exports

- retro-icon-96.png : taille native, avec fond.
- retro-icon-transparent.png : taille native, sans fond.
- retro-icon-preview.png : agrandissement entier x10 (960 x 960), sans lissage.
- retro-palette.gpl : palette reutilisable.

Pour agrandir un export, choisir un multiple entier et l'interpolation « plus proche voisin ». Garder le projet source a 96 x 96.

## Construction et verification

Le dessin est construit par le script tools/create_retro_pixel_project.py avec des formes alignees sur la grille, puis importe et enregistre par Krita en ligne de commande. Ce n'est pas un dessin realise manuellement dans l'interface de Krita. Le fichier KRA conserve les 12 calques ; son image composite est identique au PNG source. Le fichier Aseprite a ete relu et decompresse pour verifier ses calques et son rendu, mais n'a pas ete ouvert dans Aseprite.

Specification du format Aseprite : https://github.com/aseprite/aseprite/blob/main/docs/ase-file-specs.md

Le script regenere les fichiers ORA, Aseprite et PNG. Pour actualiser le KRA apres modification du script, ouvrir le nouvel ORA dans Krita puis l'enregistrer au format KRA.
