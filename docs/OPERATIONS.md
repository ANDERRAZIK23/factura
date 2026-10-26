# Operations runbook

## Prerequisites and configuration

Install Docker Engine with Compose v2. Copy `.env.example` to `.env`, replace every placeholder, and restrict file permissions. `POSTGRES_PASSWORD`, `JWT_SECRET`, and `INITIAL_ADMIN_PASSWORD` are secrets. The JWT secret must be high-entropy and at least 64 characters; the administrator password must contain upper-case, lower-case, numeric, and special characters and be 12 to 128 characters long.

For an HTTPS deployment set `AUTH_COOKIE_SECURE=true` and set `CORS_ALLOWED_ORIGINS` to the exact public origin. Terminate TLS at a trusted ingress or reverse proxy and forward traffic to the frontend container only. Do not publish the backend or database ports publicly.

## Deploy and verify

```bash
docker compose config --quiet
docker compose build --pull
docker compose up -d --wait
docker compose ps
curl --fail http://127.0.0.1:${FRONTEND_PORT:-5173}/
```

PostgreSQL is intentionally not published to the host by the production Compose
file. For local development, use the development override:

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --wait postgres
```

The backend exposes readiness, liveness, and Prometheus metrics on its private
network. Keep those endpoints behind the internal network or an authenticated
monitoring path; do not publish the backend port publicly.

The frontend health check covers Nginx and the static application. Backend and PostgreSQL have separate internal health checks. Inspect startup or runtime failures with `docker compose logs --tail=200 backend` and the equivalent service name for `frontend` or `postgres`.

## Upgrade

1. Create a coordinated database/PDF backup.
2. Review new Flyway migrations and release notes.
3. Build immutable images and run `./scripts/test-all.sh` in CI.
4. Run `docker compose up -d --build --wait`.
5. Verify login, health, invoice lookup, and a representative PDF download.

Flyway runs automatically before the API accepts traffic. Never edit an already-applied migration. Add a new forward migration for every schema change.

## Backup

Choose a maintenance window that prevents invoice validation while the two data stores are captured.

```bash
./scripts/backup.sh
```

The script creates a timestamped directory under `backups/` containing both the
PostgreSQL dump and the invoice PDF archive. Encrypt backups, restrict access,
copy them off-host, define retention, and regularly test restoration.

## Restore

Restore into an empty, isolated environment first. Stop the frontend and backend, restore PostgreSQL, then restore the matching PDF archive before restarting the services.

```bash
./scripts/restore.sh backups/20261026T120000Z
```

The restore script requires typing `RESTORE` and should only be used in an
isolated environment or an approved maintenance window. After restoration,
verify invoice counts, the latest invoice number, and PDF downloads for recent
and older invoices.

## Rollback and incidents

Application rollback means redeploying the previously tested image versions. Database migrations in this project are forward-only; if a release has applied an incompatible migration, restore the coordinated pre-deployment backup rather than manually reversing production DDL.

On suspected credential exposure, rotate the database and JWT secrets, restart the stack, revoke active refresh tokens in a controlled database operation, and require users to authenticate again. Preserve logs and database evidence before destructive remediation.

## Routine maintenance

- Monitor container restarts, health status, authentication failures, HTTP 5xx rates, PostgreSQL capacity, and PDF-volume capacity.
- Authentication endpoints are throttled by Nginx at 10 requests per minute per client address, with a burst of 5. Tune this at the trusted ingress for multi-instance deployments.
- Back up PostgreSQL and PDFs together and test recovery on a schedule.
- Remove expired/revoked refresh-token records according to an approved retention policy.
- Review dependency advisories and rebuild images regularly, including base-image security updates.
- Run `./scripts/test-all.sh` before every release.
