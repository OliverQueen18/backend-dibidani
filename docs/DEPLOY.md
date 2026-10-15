# Déploiement API Dibidani

Pour le site complet (Nginx + Angular + API + PostgreSQL), utiliser le compose du dossier parent `dibidani/` : voir `../docs/DEPLOY.md` ou `frontend-dibidani-1/docs/DEPLOY.md`.

## API seule (développement)

```powershell
Copy-Item .env.example .env
docker compose up --build
```

- Health : http://127.0.0.1:8080/actuator/health
- PostgreSQL : `127.0.0.1:5434` uniquement

## API seule (production derrière un reverse proxy externe)

```powershell
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

PostgreSQL n’a aucun port publié. Redis n’est pas utilisé.

## Jenkins

Le `Jenkinsfile` pousse `oliverqueen18/dibidani-backend` sur Docker Hub, puis le VPS fait `compose pull`. Guides : [JENKINS.md](JENKINS.md), [VPS.md](VPS.md).
