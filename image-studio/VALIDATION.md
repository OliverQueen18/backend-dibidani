# Rapport de livraison et de validation

Validation locale du 13 novembre 2026. Aucun déploiement de production effectué.

## Modifications livrées

- Microservice FastAPI/Rembg : modèle explicite ISNet, sessions CPU réutilisées,
  cache persistant préparé séparément, traitement sans accès réseau en Compose,
  détourage, cadrage sans déformation, fonds, réglages et exports PNG/WebP/JPEG.
- Spring Boot : file de tâches PostgreSQL, traitement hors transaction HTTP,
  lots, suivi, reprise après échec, contrôle du propriétaire, originaux privés,
  validation des fichiers, association des photos aux produits existants ou nouveaux.
- Angular : studio intégré à l'étape Photos, réglages communs, import multiple,
  prévisualisation, téléchargement, restauration et enregistrement dans le produit.
  L'éditeur manuel existant est conservé.
- Migration V19 : métadonnées d'export et contrainte d'une seule tâche active par image.
- Docker, exemples d'environnement, dépendances figées, préparation des modèles,
  documentation des API et état des licences.
- Les demandes précédentes restent incluses : code-barres avant le nom, OCR pour
  résumé/description, suppression définitive administrateur avec conservation des
  historiques de commande, téléphone obligatoire lors d'une commande (migration V18).

## Vérifications exécutées

- `mvn test -q` : **23 tests réussis**, aucun échec, après la dernière correction
  d'association des photos préparées avant la création du produit.
- `npm run build` : **réussi**. Avertissements non bloquants : chaîne optionnelle
  dans l'administration et dépendance CommonJS de Tesseract.
- Construction Docker Linux : **réussie**, avec **14 tests Python réussis** et
  un test ONNX explicitement ignoré pendant la construction sans poids.
- Test ONNX réel sur Windows : **9 tests réussis**, dont l'inférence ISNet et
  la vérification de l'alpha, après téléchargement du modèle dans le cache local.
- Même suite dans Docker Linux avec `--network none`, cache en lecture seule,
  limite de 3 Gio et 2 CPU : **9 tests réussis**, en 163,57 s au total
  (initialisation et ensemble de la suite inclus).
- Tests du service Python dans le dépôt backend : **16 réussis**, un ONNX ignoré,
  incluant les deux tests de compatibilité de l'ancien pipeline.
- Les trois configurations Compose de base + production ont été validées avec
  des valeurs de test. Aucun secret réel n'a été ajouté aux exemples.
- PostgreSQL 16 isolé : migrations V1 à V19 appliquées et schéma validé par Hibernate.
- `git diff --check` : aucune erreur dans les deux dépôts.

## Intégration réelle Spring Boot → FastAPI → ONNX → PostgreSQL

Le script `tests/integration_flow.py` utilise une base jetable et une photographie
synthétique de téléphone de 240 × 180 pixels. Il vérifie : connexion administrateur,
import, réponse asynchrone, inférence réelle, export WebP 800 × 800 sur fond blanc,
original conservé octet pour octet, refus de son accès anonyme, confirmation puis
relecture du produit enregistré. Un second parcours traite deux images en lot et
vérifie l'alpha des sorties PNG 400 × 400.

Dernière exécution :

- Mise en file d'une image : **1,12 s** ; résultat complet : **12,03 s**.
- Export WebP : **2 302 octets**.
- Mise en file du lot : **0,46 s** ; deux résultats : **17,06 s**.
- Première exécution isolée : 0,24 s de mise en file et 9,88 s au total.
- Processus FastAPI Windows : mémoire résidente observée **799 Mio**, pic **839 Mio**,
  CPU cumulé **20,30 s**, incluant initialisation et inférence lors de la mesure.

Ces mesures locales avec deux threads CPU ne constituent pas un benchmark de
photographies haute résolution ni une garantie de capacité en production.

## Limites de validation

Le navigateur automatisé était indisponible : aucune recette visuelle du formulaire,
de la capture caméra mobile ou du téléchargement dans le navigateur n'est revendiquée.
Les six scènes de tests sont synthétiques. Une recette sur de vraies photographies
(chaussure, vêtement, appareil, transparence et objets parasites) reste nécessaire
pour juger la qualité des contours et la sélection du sujet.

Les sources officielles distinguent la licence du code ISNet de celle des poids.
L'autorisation commerciale explicite des poids n'a pas pu être établie. La configuration
production exige donc une approbation documentée ; voir [MODEL-LICENSES.md](MODEL-LICENSES.md).
ISNet est le seul modèle autorisé par défaut pour cette validation locale.

Le cache ONNX et l'environnement Python de validation sont conservés localement dans
des dossiers ignorés. Les services temporaires et la base jetable sont arrêtés après
les tests. Les sources ne sont ni commitées ni poussées automatiquement.
