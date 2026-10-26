#!/usr/bin/env bash
set -Eeuo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose=(docker compose -f "$root_dir/docker-compose.yml")
backup_dir="${1:-$root_dir/backups/$(date -u +%Y%m%dT%H%M%SZ)}"
mkdir -p "$backup_dir"
umask 077

postgres_container="$("${compose[@]}" ps -q postgres)"
backend_container="$("${compose[@]}" ps -q backend)"
if [[ -z "$postgres_container" || -z "$backend_container" ]]; then
  printf 'The postgres and backend services must be running before backup.\n' >&2
  exit 1
fi

pdf_volume="$(docker inspect "$backend_container" --format '{{range .Mounts}}{{if eq .Destination "/data/invoices"}}{{.Name}}{{end}}{{end}}')"
if [[ -z "$pdf_volume" ]]; then
  printf 'Could not locate the invoice PDF volume.\n' >&2
  exit 1
fi

postgres_user="${POSTGRES_USER:-invoice_app}"
postgres_db="${POSTGRES_DB:-invoice_management}"
"${compose[@]}" exec -T postgres pg_dump \
  -U "$postgres_user" \
  -d "$postgres_db" \
  -Fc > "$backup_dir/postgres.dump.tmp"
mv "$backup_dir/postgres.dump.tmp" "$backup_dir/postgres.dump"

docker run --rm \
  -v "$pdf_volume:/source:ro" \
  -v "$backup_dir:/backup" \
  alpine:3.22 \
  tar -C /source -czf /backup/invoice-pdfs.tgz .

printf 'Backup created in %s\n' "$backup_dir"
printf 'Restore with matching postgres.dump and invoice-pdfs.tgz files in an isolated environment.\n'
