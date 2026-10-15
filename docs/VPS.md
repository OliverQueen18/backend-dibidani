# Dibidani sur le VPS (nginx-proxy)

Le site public est `https://dibidani.com`. nginx-proxy termine le TLS. Le Nginx du frontend reverse-proxie `/api/` et `/uploads/` vers l’API interne — pas de sous-domaine `api.`.

## 1. Images Docker Hub

Comme ERDV / PIE / Tontine :

| Service | Image |
|---|---|
| API | `oliverqueen18/dibidani-backend` |
| Frontend | `oliverqueen18/dibidani-frontend` |
| Studio photo | `oliverqueen18/dibidani-image-studio` |

Jenkins pousse `:latest` (et un tag de build). Premier push du studio (dépôt parent, une fois) :

```bash
docker build -t oliverqueen18/dibidani-image-studio:latest ./image-studio
docker push oliverqueen18/dibidani-image-studio:latest
```

## 2. Fichiers sur le serveur

À côté du `docker-compose.yml` VPS :

- `docker-compose.dibidani.yml` (ce dossier)
- variables de `vps.env.example` dans le `.env` existant (`DIBIDANI_ADMIN_EMAIL` et `DIBIDANI_ADMIN_PASSWORD` obligatoires : compte admin créé au premier démarrage)
- dossier fichiers (API + studio photo) :

```bash
mkdir -p uploads/dibidani
chmod 777 uploads/dibidani
```

`DIBIDANI_UPLOAD_HOST_PATH=./uploads/dibidani` (monté dans les conteneurs sur `/app/uploads`).

- vhost nginx-proxy (uploads jusqu’à 14 Mo) :

```bash
cat > data/vhost.d/dibidani.com <<'EOF'
client_max_body_size 14m;
proxy_read_timeout 120s;
proxy_send_timeout 120s;
EOF
cp data/vhost.d/dibidani.com data/vhost.d/www.dibidani.com
```

## 3. DNS

Enregistrements A (et AAAA si IPv6) :

- `dibidani.com` → IP du VPS
- `www.dibidani.com` → IP du VPS

## 4. Démarrage

```bash
docker compose -f docker-compose.yml -f docker-compose.dibidani.yml pull \
  postgres-dibidani image-studio-dibidani backend-dibidani frontend-dibidani
docker compose -f docker-compose.yml -f docker-compose.dibidani.yml up -d \
  postgres-dibidani image-studio-dibidani backend-dibidani frontend-dibidani
```

Vérifications :

```bash
docker compose -f docker-compose.yml -f docker-compose.dibidani.yml ps
curl -fsS https://dibidani.com/health
curl -fsS https://dibidani.com/api/v1/public/settings
```

PostgreSQL, l’API et le studio photo n’ont **aucun port publié**. Seul le frontend est joignable via nginx-proxy (80/443 déjà ouverts).

## 5. Jenkins

Paramètres des jobs :

| Paramètre | Valeur |
|---|---|
| `DEPLOY_MODE` | `hub` |
| `DEPLOY_PATH` | dossier du compose VPS (celui qui contient `docker-compose.yml`) |
| `DEPLOY_HOST` | `user@IP` du VPS |
| Identifiant Docker Hub | `dockerhub-credentials` |
| Identifiant SSH | `ssh-server-credentials` |

Le job pousse l’image sur Docker Hub, puis sur le VPS : `docker compose pull` + `up -d --no-deps --force-recreate` du service (`backend-dibidani` ou `frontend-dibidani`).
