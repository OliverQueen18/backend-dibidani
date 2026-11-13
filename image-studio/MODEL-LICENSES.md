# Modèles et licences — vérification du 13 novembre 2026

Seul `isnet-general-use` est sélectionné par défaut, pour les essais locaux.
Aucun modèle n'est considéré ici comme autorisé automatiquement pour la production commerciale.
Le profil Compose de production et `STUDIO_ENV=production` exigent une liste
`MODEL_LICENSE_APPROVALS` dont chaque entrée doit correspondre à une preuve de licence conservée par l'exploitant.
Cette variable n'accorde aucun droit : ne pas la renseigner pour contourner une absence de licence.

## ISNet general use

- Source officielle : https://github.com/xuebinqin/DIS
- Licence du dépôt : https://github.com/xuebinqin/DIS/blob/main/LICENSE.md (Apache-2.0).
- Le README limite explicitement sa déclaration aux **code et métrique d'évaluation**.
- Les poids `isnet-general-use.pth` sont distribués séparément. Aucune autorisation commerciale explicite des poids n'a été trouvée dans les sources officielles consultées.
- Une demande de clarification est encore ouverte : https://github.com/xuebinqin/DIS/issues/150
- Conversion ONNX distribuée par Rembg 2.0.67 : https://github.com/danielgatis/rembg/releases/download/v0.0.0/isnet-general-use.onnx
- Empreinte MD5 publiée dans le chargeur Rembg : `fc16ebd8b0c10d971d3513d564d01e29`.
- Décision : implémenté et testable localement, **activation commerciale en attente de clarification des poids**. Ne pas assimiler la licence du code à celle des poids ni aux conditions du jeu DIS5K.

## Modèles préparés mais non activés

- `u2net` : dépôt officiel https://github.com/xuebinqin/U-2-Net, licence Apache-2.0. Vérifier le périmètre couvrant les poids précis avant ajout à la liste commerciale.
- `silueta` : variante U2Net distribuée par Rembg. Licence des poids spécifiques non établie dans cette livraison ; reste désactivée.
- `birefnet-general` et `birefnet-general-lite` : dépôt https://github.com/ZhengPeng7/BiRefNet sous MIT ; modèle officiel https://huggingface.co/ZhengPeng7/BiRefNet également étiqueté MIT. Vérifier et archiver la licence du checkpoint exact et de sa conversion ONNX avant activation. Non téléchargés et non activés ici.

Rembg est sous MIT, ONNX Runtime sous MIT. La licence de Rembg ne remplace pas
celle de ses modèles : https://github.com/danielgatis/rembg#models

## Procédure d'ajout

1. Conserver la source officielle, la révision, les conditions des poids et les notices applicables.
2. Vérifier que le checkpoint choisi correspond exactement à celui chargé par la version figée de Rembg.
3. Ajouter son identifiant à `REMBG_ALLOWED_MODELS` et augmenter `MAX_LOADED_MODELS` uniquement après mesure de la mémoire.
4. Préparer le volume avec `image-models`, exécuter les tests locaux et vérifier les contours sur les photos représentatives.
5. Ajouter l'identifiant à `MODEL_LICENSE_APPROVALS` uniquement après vérification documentée pour le déploiement concerné.

Aucun backend cloud de Rembg n'est accepté par le registre. Aucun appel de détourage externe n'est effectué.
