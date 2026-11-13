# Studio Photo IA Dibidani

Le studio existant est étendu, sans remplacer le catalogue, l'authentification JWT,
le formulaire de publication ni l'éditeur manuel. Angular envoie les originaux à
Spring Boot ; celui-ci stocke une tâche PostgreSQL puis appelle FastAPI sur le réseau
interne. Seules les images validées sont ajoutées au produit.

## Installation depuis le dossier Dibidani

Python 3.12, Docker Compose v2 et suffisamment de mémoire CPU sont nécessaires.
Les dépendances directes sont figées dans `requirements.txt`, les dépendances
transitives dans `requirements-lock.txt`. `pyreadline3` est limité à Windows.

1. Copier `.env.example` vers `.env` sans écraser une configuration existante.
2. Renseigner les paramètres habituels PostgreSQL/JWT et générer un secret aléatoire `PHOTO_STUDIO_TOKEN` commun au backend et au studio.
3. Lire [MODEL-LICENSES.md](MODEL-LICENSES.md) avant toute activation commerciale.
4. Construire et préparer le cache pendant l'installation :

```sh
docker compose build image-studio
docker compose --profile tools run --rm image-models
docker compose up -d db image-studio backend frontend
```

`image-models` est le seul service de préparation ayant accès au réseau sortant.
Le traitement utilise le volume persistant `dibidani_models` et ne télécharge pas
de poids pendant une requête. Le service IA n'a **aucun port publié** et n'appartient
qu'au réseau Docker `data`, interne. Les images ne quittent pas ce réseau.

Si le cache est absent ou corrompu, `/health` renvoie 503. Réexécuter la préparation,
puis redémarrer le studio. Ne pas supprimer le volume de modèles à chaque mise à jour.

Production : utiliser les fichiers Compose de base et de production. La licence
commerciale des poids ISNet n'étant pas explicitement établie, le profil production
refuse de démarrer sans `MODEL_LICENSE_APPROVALS` documenté. Cette livraison ne déploie
pas le modèle commercialement.

## Utilisation

Dans la création/modification d'un produit, étape **Photos**, cliquer sur
**✨ Retoucher avec l'IA**. Choisir les paramètres, photographier ou importer un ou
plusieurs fichiers. Le traitement démarre en arrière-plan. Chaque carte conserve
son original, son résultat, son état et sa taille exportée.

**✨ Supprimer le fond** relance ISNet avec les paramètres courants.
**Fond blanc** impose un fond blanc. **Appliquer aux photos importées** utilise une
requête de lot qui crée des tâches individuelles ; les erreurs sont affichées par photo.
**Restaurer l'original** recrée une sortie sans détourage ni amélioration automatique.
L'éditeur manuel existant conserve ses outils de sélection, effacement, luminosité,
contraste et annulation. L'enregistrement dans le produit exige un traitement réussi.

PNG et WebP conservent l'alpha ; JPEG compose obligatoirement sur un fond opaque.
L'export garde les proportions, avec 400/800/1200 px ou dimensions personnalisées
entre 100 et 2400 px. Le centrage automatique limite zoom et déplacements pour ne pas
couper le sujet. Il ne génère aucun détail supplémentaire.

## API applicative (authentification existante)

Toutes les routes du studio exigent `PERM_CATALOG_MANAGE` ou le rôle `SELLER`.
Les vendeurs ne peuvent traiter/consulter que leurs images et produits.

- `POST /api/v1/products/images/upload` : multipart `file`, `productId` facultatif ; retourne l'asset et préserve les octets originaux.
- `GET /api/v1/products/images/models` (alias `/api/images/models`) : modèles autorisés et état de chargement.
- `POST /api/v1/products/images/process` (alias `/api/images/process`) : crée une tâche, HTTP 202.
- `POST /api/images/remove-background` : même contrat de tâche, `assetId` requis, détourage activé par défaut.
- `POST /api/v1/products/images/batch-process` (alias `/api/images/batch-process`) : `{"images":[...requêtes de traitement...]}`, 1 à 12 images. Chaque élément retourne `assetId`, `job` ou `error`.
- `GET /api/v1/products/images/jobs/{id}` (alias `/api/images/jobs/{id}`) : état PENDING, PROCESSING, COMPLETED ou FAILED.
- `POST /api/v1/products/images/{assetId}/confirm` : `{"productId":123,"main":true}` ; associe la sortie au produit.
- `POST /api/products/{id}/images/processed?assetId=456&main=true` : alias d'association.
- `GET /api/v1/products/images/{assetId}/original` : original protégé, `private, no-store`.

Exemple de corps de traitement :

```json
{
  "assetId": 456,
  "productId": 123,
  "model": "isnet-general-use",
  "removeBackground": true,
  "background": "white",
  "backgroundColor": "#FFFFFF",
  "format": "webp",
  "width": 1200,
  "height": 1200,
  "quality": 82,
  "refineEdges": false,
  "recenter": true,
  "rotation": 0,
  "brightness": 1.0,
  "contrast": 1.0
}
```

## API FastAPI interne

- `GET /health` : 200 prêt, 503 non prêt ; sans secret pour le healthcheck.
- `GET /models` : liste autorisée, modèle par défaut, sessions chargées.
- `POST /remove-background` : multipart `file`, `options` JSON ; renvoie PNG alpha.
- `POST /process-image` : multipart `file`, `options` JSON ; renvoie l'image exportée.
- `POST /process` : contrat de stockage partagé utilisé par Spring : `inputPath`, `outputDir`, `stem`, `options`. Les sources sont limitées à `private/originals`, les sorties à `studio` et les noms à un identifiant sans chemin.

En-tête obligatoire hors healthcheck : `X-Studio-Token`.
Erreurs : `{"code":"STUDIO_400","message":"..."}` ; 400 paramètres/image invalides,
401 secret invalide, 404 original absent, 413 fichier trop lourd, 429 capacité atteinte,
503 moteur indisponible. Les erreurs de schéma utilisent HTTP 422 avec le même format code/message.
La route `/docs` interne décrit les schémas. Aucune URL distante d'image n'est acceptée.

## Ressources et stockage

Variables principales : `REMBG_MODEL`, `REMBG_ALLOWED_MODELS`, `MAX_LOADED_MODELS`,
`MAX_IMAGE_MB=10`, `MAX_IMAGE_BYTES=10485760` (Spring), `MAX_IMAGE_PIXELS=20000000`,
`IMAGE_PROCESSING_TIMEOUT=120`, `IMAGE_MAX_CONCURRENCY=1`, `IMAGE_CPU_THREADS=2`,
`IMAGE_CPU_LIMIT=2.0`, `IMAGE_MEMORY_LIMIT=3g`. Garder les limites de taille Spring/Python
cohérentes. Commencer avec un seul modèle/session et mesurer avant d'augmenter.

La migration Flyway V19 ajoute modèle, format, dimensions et taille de sortie aux
métadonnées existantes. Aucun binaire n'est stocké dans PostgreSQL. Le stockage reste
celui de Dibidani (`ProductImageStorageService`, volume uploads). Les originaux sont
privés ; seules les variantes publiées sont servies publiquement. EXIF et métadonnées
sensibles sont supprimés des sorties. Les formats sont vérifiés par décodage, les pixels
sont contrôlés avant allocation complète, les animations et formats non supportés sont refusés.

Un ordonnanceur dédié consomme les tâches persistées, une à la fois par instance
backend. PostgreSQL `FOR UPDATE SKIP LOCKED` évite la double prise d'une tâche.
Un index partiel interdit deux tâches actives pour une image. Les traitements
interrompus sont marqués FAILED après expiration et peuvent être relancés. Les
échecs IA ne deviennent jamais un faux succès par cadrage de secours.

Un timeout HTTP ne peut pas interrompre instantanément un calcul ONNX natif en cours.
Le sémaphore Python reste occupé jusqu'à la fin du calcul ; Docker borne CPU/RAM.
Prévoir un superviseur de processus isolés si une interruption dure devient nécessaire.

Les originaux non confirmés restent soumis au nettoyage existant après 14 jours.
La suppression explicite d'un asset retire son original selon le comportement existant.
Les versions déjà publiées restent disponibles pour ne pas casser les références des commandes.

## Tests

```sh
python -m venv .studio-venv
# Activer cet environnement, puis :
pip install -r image-studio/requirements.txt
cd image-studio
python -m unittest discover -s tests -v
```

Les tests utilisent six scènes locales synthétiques (chaussure, téléphone, vêtement,
appareil, transparence, parasites), des mocks de segmentation et de vrais exports.
Ils couvrent alpha, cadrage, fichiers volumineux/corrompus/interdits, EXIF, réutilisation
des sessions, authentification interne, saturation, reprise et contrôle des chemins.
Ils **ne mesurent pas la qualité visuelle sur six photographies réelles**.

Test ONNX réel, après préparation du cache : fixer `U2NET_HOME` au cache local,
`OMP_NUM_THREADS=2`, `RUN_ONNX_TEST=1`, puis lancer les mêmes tests. Une machine sans
poids garde ce test explicitement ignoré. Pour qualifier la production, ajouter un
corpus représentatif de photos réelles et inspecter les détails transparents, logos et parasites.

Backend : `mvn test` dans `backend-dibidani-1`. Frontend : `npm run build` dans
`frontend-dibidani-1`. Voir `VALIDATION.md` pour les résultats effectivement obtenus.

## Organisation des sources

Le service de référence est `image-studio/` à la racine de l'espace de travail.
Une copie identique du package `app/`, des tests et des dépendances est livrée dans
`backend-dibidani-1/image-studio/` pour le Compose autonome du dépôt backend.
Les anciennes entrées Python de ce dépôt restent présentes pour compatibilité,
mais son Dockerfile lance désormais `app.main:app`.

Évolution GPU : créer une image dédiée avec `onnxruntime-gpu`, ajouter des providers
autorisés au gestionnaire et tester leur budget mémoire. La version actuelle refuse
un `REMBG_DEVICE` autre que CPU, plutôt que de faire croire qu'un GPU est utilisé.
