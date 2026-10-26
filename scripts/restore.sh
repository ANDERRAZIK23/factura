#!/usr/bin/env bash
set -Eeuo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose=(docker compose -f "$root_dir/docker-compose.yml")
backup_dir="${1:?Usage: scripts/restore.sh <backup-directory>}"
backup_dir="$(cd "$backup_dir" && pwd)"

if [[ ! -f "$backup_dir/postgres.dump" || ! -f "$backup_dir/invoice-pdfs.tgz" ]]; then
  printf 'Backup directory must contain postgres.dump and invoice-pdfs.tgz.\n' >&2
  exit 1
fi

printf 'This replaces data in the configured database and PDF volume. Type RESTORE to continue: '
read -r confirmation
if [[ "$confirmation" != RESTORE ]]; then
  printf 'Restore cancelled.\n'
  exit 1
fi

backend_container="$(${compose[@]} ps -q backend)"
if [[ -z "$backend_container" ]]; then
  printf 'The backend service must be running to locate the invoice PDF volume.\n' >&2
  exit 1
fi
pdf_volume="$(docker inspect "$backend_container" --format '{{range .Mounts}}{{if eq .Destination "/data/invoices"}}{{.Name}}{{end}}{{end}}')"
if [[ -z "$pdf_volume" ]]; then
  printf 'Could not locate the invoice PDF volume.\n' >&2
  exit 1
fi

"${compose[@]}" stop frontend backend
postgres_user="${POSTGRES_USER:-invoice_app}"
postgres_db="${POSTGRES_DB:-invoice_management}"
"${compose[@]}" exec -T postgres pg_restore \
  --clean --if-exists --no-owner \
  -U "$postgres_user" \
  -d "$postgres_db" < "$backup_dir/postgres.dump"

docker run --rm \
  -v "$pdf_volume:/target" \
  -v "$backup_dir:/backup:ro" \
  alpine:3.22 \
  sh -c 'rm -rf /target/* && tar -C /target -xzf /backup/invoice-pdfs.tgz'

"${compose[@]}" up -d --wait
printf 'Restore completed. Verify login, invoice counts, and PDF downloads.\n'
