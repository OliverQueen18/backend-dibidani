# Jenkins — backend et frontend Dibidani

Deux jobs Pipeline. Chaque build pousse une image Docker Hub (`oliverqueen18/dibidani-*`), puis le VPS fait `compose pull` + `up`.

| Job | Dépôt | Image Hub | Service VPS |
|---|---|---|---|
| `dibidani-backend` | [backend-dibidani](https://github.com/OliverQueen18/backend-dibidani) | `oliverqueen18/dibidani-backend` | `backend-dibidani` |
| `dibidani-frontend` | [frontend-dibidani](https://github.com/OliverQueen18/frontend-dibidani) | `oliverqueen18/dibidani-frontend` | `frontend-dibidani` |

Le Jenkinsfile est à la racine de chaque dépôt. Intégration VPS : [VPS.md](VPS.md).

## Prérequis

- Jenkins 2.400+ sur un agent **Linux** avec Docker
- Plugins : **Pipeline**, **Git**, **JUnit**, **SSH Agent**, **Credentials Binding**, **GitHub**
- L’utilisateur Jenkins dans le groupe `docker`

## Identifiants

| ID | Type | Usage |
|---|---|---|
| (GitHub) | PAT / GitHub App | Checkout |
| `dockerhub-credentials` | Username/password | `docker push` |
| `ssh-server-credentials` | SSH Username with private key | `compose pull` sur le VPS |

## Jobs

Pipeline script from SCM, branche `*/main`, Script Path `Jenkinsfile`, **GitHub hook trigger**.

| Paramètre | Valeur VPS |
|---|---|
| `DEPLOY` | true |
| `DEPLOY_MODE` | `hub` |
| `DEPLOY_PATH` | dossier du `docker-compose.yml` VPS |
| `DEPLOY_HOST` | `user@IP` du VPS |
| `RUN_TESTS` | true (backend) |

Webhook GitHub : `https://<jenkins>/github-webhook/` (push).

## Modes

- **hub** (VPS) — build → push Docker Hub → SSH `docker compose pull` + `up -d --force-recreate` du service
- **ssh** — `docker save \| ssh docker load` sans registry
- **same-host** — Jenkins et Docker sur la même machine

## Studio photo

Pas dans les pipelines. Une fois :

```bash
docker build -t oliverqueen18/dibidani-image-studio:latest ./image-studio
docker push oliverqueen18/dibidani-image-studio:latest
```

## Dépannage

| Symptôme | Piste |
|---|---|
| 403 Docker Hub | identifiant `dockerhub-credentials` |
| `permission denied` docker | Jenkins hors du groupe `docker` |
| `DEPLOY_HOST est obligatoire` | mode `hub`/`ssh` sans hôte |
| Conteneur `missing` | overlay pas déployé : `docker-compose.dibidani.yml` |
| Frontend unhealthy | API pas encore UP ; relancer `frontend-dibidani` |

```bash
docker compose -f docker-compose.yml -f docker-compose.dibidani.yml logs -f --tail=100 backend-dibidani
docker compose -f docker-compose.yml -f docker-compose.dibidani.yml logs -f --tail=100 frontend-dibidani
```
