# Dibidani — backend

API Spring Boot (Java 21) du marché Dibidani, Bamako.

## Démarrage

**Stack complète (recommandé)** — depuis le dossier parent :

```bash
cd ..
cp .env.example .env
docker compose up --build
```

Site : http://localhost  
Documentation : [docs/DEPLOY.md](../docs/DEPLOY.md)

**API seule :**

```bash
cp .env.example .env
docker compose up --build
```

- Health : http://127.0.0.1:8080/actuator/health
- PostgreSQL : `127.0.0.1:5434` (localhost uniquement)

## Production

```bash
cd ..
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

PostgreSQL et l’API ne sont **pas** publiés. Seul Nginx (port 80) est accessible.

CI/CD Jenkins (Docker Hub + VPS) : [docs/JENKINS.md](docs/JENKINS.md). Overlay nginx-proxy : [docs/VPS.md](docs/VPS.md).

## Comptes de démonstration (seed local uniquement)

- Admin : `admin@dibidani.ml` / `Admin123!`
- Client : `client@dibidani.ml` / `Client123!`

Désactivés en production (`SEED_DEMO_DATA=false`).
Le référentiel catalogue (catégories, marques, vendeurs, zones, publicités) est chargé même en production, une seule fois.
