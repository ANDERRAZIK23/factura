# Security review

Review scope: application source, Spring Security configuration, authentication and authorization flows, Flyway migrations, browser client, PDF storage, Dockerfiles, and Docker Compose deployment. Review date: 2026-08-04.

## Implemented controls

- Every non-health business endpoint requires authentication. Method-level roles make `VIEWER` read-only and limit mutations to `ADMIN` and `INVOICE_MANAGER` as appropriate.
- Access JWTs have explicit issuer, audience, and short expiration and are kept in browser memory. The API rechecks the current user, enabled state, and role on requests.
- Refresh tokens use cryptographically random values, store SHA-256 hashes only, rotate on use, and revoke a token family on detected reuse. The cookie is HttpOnly, SameSite Strict, narrowly scoped to authentication endpoints, and configurable as Secure.
- BCrypt uses cost 12. API-created users and the initial administrator share the same strong-password policy. Authentication errors do not reveal whether an email exists.
- CORS uses an explicit origin allowlist with credential support. CSRF is disabled because business API authorization requires an access token in the `Authorization` header; refresh-cookie endpoints are constrained by SameSite Strict. This assumption must be revisited if cookie-authenticated business endpoints are introduced.
- Validation, allowlisted sorting, optimistic locking, database constraints, immutable snapshots, and server-authoritative calculations protect data integrity.
- PDFs use server-generated normalized relative paths, atomic publication, traversal and symlink defenses, and authenticated downloads. Client-supplied filesystem paths are not accepted.
- Error responses suppress stack traces and infrastructure details. Swagger/OpenAPI is exposed only in the `dev` profile. Actuator exposes health only and hides details.
- Application containers run non-root with read-only root filesystems, all Linux capabilities dropped, and privilege escalation disabled. Only Nginx is host-published. PostgreSQL and PDFs use distinct durable volumes.
- Nginx supplies a restrictive Content Security Policy, clickjacking, MIME-sniffing, and referrer controls. Hashed static assets are immutable; the application shell is not cached.

## Deployment requirements

- Terminate TLS before Nginx, redirect HTTP to HTTPS at the ingress, and set `AUTH_COOKIE_SECURE=true`.
- Inject secrets through the deployment platform. Do not bake them into images, source files, shell history, or Compose configuration committed to version control.
- Use exact trusted origins in `CORS_ALLOWED_ORIGINS`; never use a wildcard with credentials.
- Restrict access to the Docker host, database, volumes, backups, and logs. Encrypt backups and off-host copies.
- Keep the backend and PostgreSQL private. If network policy is available, permit only frontend-to-backend and backend-to-database traffic.

## Residual risks and follow-up

No critical source-level vulnerability was found in this review. The following operational risks remain and should be tracked:

- Login rate limiting and edge denial-of-service controls are not implemented in-process. Apply rate limits at the trusted ingress and alert on repeated authentication failures.
- Expired and revoked refresh-token cleanup is not scheduled. Define retention and add a tested maintenance job before token-table growth becomes material.
- Application audit fields exist, but there is no centralized append-only security audit log or SIEM integration. Forward structured container logs and protect their retention.
- Compose tags are versioned but not digest-pinned. Release automation should resolve, scan, and pin approved image digests for reproducible production deployments.
- Automated Java CVE scanning is not part of the Maven lifecycle. Run an approved SCA scanner in CI and treat high/critical reachable findings as release blockers.
- The frontend dependency audit may report React Router advisories affecting React Server Components. This application is client-only and does not use RSC/server actions, reducing exposure; reassess and upgrade when an upstream fixed release is available.
- Availability depends on one Compose host. Production recovery objectives may require managed PostgreSQL, replicated object storage for PDFs, and an orchestrator with rolling deployment support.

## Verification checklist

- Run backend unit/integration tests and coverage gates with Java 21.
- Run frontend lint, type checking, unit/component coverage, production build, and Playwright workflows.
- Run `npm audit --omit=dev` and the organization-approved Java/image scanners.
- Validate `docker compose config --quiet`, build both images, and verify all three health checks.
- Confirm unauthenticated API denial, role restrictions, Secure refresh cookies over HTTPS, security headers, and authenticated PDF download in the deployed environment.
