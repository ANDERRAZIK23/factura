#!/usr/bin/env bash
set -Eeuo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose_file="$root_dir/docker-compose.yml"
dev_compose_file="$root_dir/docker-compose.dev.yml"
backend_pid=""

export COMPOSE_PROJECT_NAME=invoice-management-tests

if [[ -d /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ]]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
  export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"
fi

cleanup() {
  if [[ -n "$backend_pid" ]] && kill -0 "$backend_pid" 2>/dev/null; then
    kill "$backend_pid"
    wait "$backend_pid" 2>/dev/null || true
  fi
  docker compose -f "$compose_file" -f "$dev_compose_file" down --volumes --remove-orphans
}
trap cleanup EXIT INT TERM

: "${POSTGRES_PASSWORD:=invoice_test_password}"
export POSTGRES_PASSWORD
: "${POSTGRES_PORT:=55432}"
export POSTGRES_PORT
: "${DATABASE_PASSWORD:=$POSTGRES_PASSWORD}"
export DATABASE_PASSWORD
: "${DATABASE_URL:=jdbc:postgresql://localhost:$POSTGRES_PORT/invoice_management}"
export DATABASE_URL
: "${JWT_SECRET:=phase-two-test-secret-that-is-at-least-sixty-four-characters-long-and-not-for-production}"
export JWT_SECRET
: "${INITIAL_ADMIN_NAME:=System Administrator}"
export INITIAL_ADMIN_NAME
: "${INITIAL_ADMIN_EMAIL:=admin@example.com}"
export INITIAL_ADMIN_EMAIL
: "${INITIAL_ADMIN_PASSWORD:=AdminPassword123!}"
export INITIAL_ADMIN_PASSWORD
: "${INVOICE_PDF_STORAGE_PATH:=$root_dir/backend/target/e2e-invoice-pdfs}"
export INVOICE_PDF_STORAGE_PATH
docker compose -f "$compose_file" -f "$dev_compose_file" up -d --wait postgres

(cd "$root_dir/backend" && ./mvnw clean verify)
(cd "$root_dir/frontend" && npm ci && npm run lint && npm run typecheck && npm run test:run && npm run test:coverage && npm run build)

(cd "$root_dir/backend" && ./mvnw spring-boot:run > target/e2e-backend.log 2>&1) &
backend_pid=$!
for _ in {1..30}; do
  if curl --fail --silent --output /dev/null http://127.0.0.1:8080/actuator/health; then
    break
  fi
  if ! kill -0 "$backend_pid" 2>/dev/null; then
    cat "$root_dir/backend/target/e2e-backend.log"
    exit 1
  fi
  sleep 1
done
curl --fail --silent --output /dev/null http://127.0.0.1:8080/actuator/health
(cd "$root_dir/frontend" && npx playwright install chromium)
(cd "$root_dir/frontend" && npm run test:e2e)
