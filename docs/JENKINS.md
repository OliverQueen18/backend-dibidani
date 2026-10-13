# Jenkins — backend et frontend Dibidani

Deux dépôts GitHub, deux jobs Pipeline. Chaque build produit une image Docker taguée, puis Compose bascule le service correspondant **sans reconstruire** sur le serveur.

| Job | Dépôt | Image | Service Compose |
|---|---|---|---|
| `dibidani-backend` | [OliverQueen18/backend-dibidani](https://github.com/OliverQueen18/backend-dibidani) | `dibidani-backend:<BUILD>-<sha>` | `backend` |
| `dibidani-frontend` | [OliverQueen18/frontend-dibidani](https://github.com/OliverQueen18/frontend-dibidani) | `dibidani-frontend:<BUILD>-<sha>` | `frontend` |

Le Jenkinsfile est à la racine de chaque dépôt (`Jenkinsfile` de ce dépôt pour l’API).

## Prérequis

- Jenkins 2.400+ sur un agent **Linux** (les étapes `sh` et `docker` ne tournent pas sur un agent Windows)
- Docker Engine 24+ et Docker Compose v2 sur l’agent (et sur le serveur si déploiement SSH)
- L’utilisateur Jenkins membre du groupe `docker` (`sudo usermod -aG docker jenkins` puis redémarrer Jenkins)
- Plugins : **Pipeline**, **Git**, **JUnit**, **SSH Agent** (uniquement si `DEPLOY_MODE=ssh`), **GitHub** (webhook)

## Identifiants Jenkins

| ID | Type | Usage |
|---|---|---|
| (GitHub) | Username/password ou GitHub App / PAT | Checkout des dépôts privés |
| `dibidani-deploy-ssh` | SSH Username with private key | Transfert d’image + `docker compose` en mode `ssh` |

Créer `dibidani-deploy-ssh` : Manage Jenkins → Credentials → Global → SSH Username with private key. L’utilisateur distant doit pouvoir exécuter `docker` et `docker compose` sans mot de passe.

## Bootstrap du serveur (`/opt/dibidani`)

Le parent `dibidani/` n’est pas un dépôt Git. Une fois, copier sur le serveur :

```text
/opt/dibidani/
  docker-compose.yml
  docker-compose.prod.yml
  .env                 # depuis .env.example, secrets réels
  backend-dibidani-1/  # clone (context de build / image-studio voisin)
  frontend-dibidani-1/
  image-studio/
```

```bash
sudo mkdir -p /opt/dibidani
# copier compose + .env + image-studio
cd /opt/dibidani
cp .env.example .env
# éditer POSTGRES_PASSWORD, JWT_SECRET, CORS_ORIGINS, PUBLIC_URL
# BACKEND_IMAGE=dibidani-backend:local
# FRONTEND_IMAGE=dibidani-frontend:local

docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

Ensuite Jenkins ne fait plus que `up -d --no-deps --no-build` du service modifié, et écrit le tag dans `.env` (`BACKEND_IMAGE` / `FRONTEND_IMAGE`) pour que le prochain `compose up` ne revienne pas à `:local`.

## Créer les jobs

Pour **chaque** dépôt :

1. New Item → **Pipeline** → nom `dibidani-backend` ou `dibidani-frontend`
2. GitHub project : URL du dépôt
3. Pipeline → Definition : **Pipeline script from SCM**
4. SCM : Git  
   - Backend : `https://github.com/OliverQueen18/backend-dibidani.git`  
   - Frontend : `https://github.com/OliverQueen18/frontend-dibidani.git`
5. Branches to build : `*/main`
6. Script Path : `Jenkinsfile`
7. Cocher **GitHub hook trigger for GITScm polling** (en plus du `pollSCM` du Jenkinsfile)

Paramètres au premier build (Build with Parameters) :

| Paramètre | Valeur typique |
|---|---|
| `DEPLOY` | true |
| `DEPLOY_MODE` | `same-host` si Jenkins tourne **sur** le serveur Docker ; `ssh` sinon |
| `DEPLOY_PATH` | `/opt/dibidani` |
| `DEPLOY_HOST` | `deploy@IP` (mode ssh uniquement) |
| `RUN_TESTS` | true (backend seulement) |

Les déploiements automatiques ciblent `main` / `master`. Un job Pipeline classique (sans `BRANCH_NAME`) déploie aussi si `DEPLOY=true`.

## Webhook GitHub

Sur chaque dépôt GitHub → Settings → Webhooks :

- Payload URL : `https://<jenkins>/github-webhook/`
- Content type : `application/json`
- Events : **Just the push event**

Un push sur `main` lance le job du dépôt concerné (backend et frontend restent indépendants).

## Modes de déploiement

**same-host** — l’agent Jenkins a accès au démon Docker du serveur. L’image construite est déjà locale ; Compose la démarre.

**ssh** — Jenkins construit l’image, puis :

```text
docker save IMAGE | gzip | ssh HOTE 'gunzip | docker load'
ssh HOTE  →  mise à jour .env + docker compose up -d --no-deps --no-build SERVICE
```

Pas de registry obligatoire. Pour un registry (GHCR, Docker Hub), ajouter un `docker tag` / `docker push` dans le Jenkinsfile plus tard.

## Santé

Après le bascule, le job attend `healthy` :

- backend : `GET /actuator/health` (healthcheck Compose / Dockerfile)
- frontend : `GET /health` (Nginx)

En production, seuls Nginx (port 80) et éventuellement le reverse proxy TLS sont publics.

## Studio photo

`image-studio` n’est **pas** dans ces pipelines. Le reconstruire à la main sur le serveur :

```bash
cd /opt/dibidani
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build image-studio
```

## Dépannage

| Symptôme | Piste |
|---|---|
| `permission denied` docker | Jenkins pas dans le groupe `docker` |
| `DEPLOY_HOST est obligatoire` | Mode `ssh` sans paramètre |
| `Fichier .env introuvable` | `DEPLOY_PATH` ne pointe pas vers le dossier Compose |
| Conteneur `missing` | Premier bootstrap Compose pas fait, ou mauvais `name:` de projet |
| Tests Maven échouent | Relancer avec `RUN_TESTS=false` le temps de corriger ; le Dockerfile skip déjà les tests au package |
| Frontend unhealthy | Le backend n’est pas UP (`depends_on` au premier `up`, pas aux updates `--no-deps`) |
| Image `:local` au reboot | `.env` n’a pas `BACKEND_IMAGE` / `FRONTEND_IMAGE` (le job les écrit) |

Journaux :

```bash
cd /opt/dibidani
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f --tail=100 backend
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f --tail=100 frontend
```
