# CLAUDE.md

Auth microservice for the **Atlas** platform. Built on **Quarkus 3.31** / **Java 21**.
Handles registration, login, JWT issuance/refresh, gRPC token validation, and Traefik
ForwardAuth verification.

## Build & run

```bash
make dev          # Quarkus dev mode with live reload (local, needs Postgres + Redis)
make up           # Build image + start full stack via docker-compose
make down         # Stop stack
make test         # Run tests in container
./mvnw test       # Run tests locally (uses H2 in-memory under %test profile)
./mvnw quarkus:dev
make gen-keys     # Generate RSA keypair for JWT signing (required before first run)
```

JWT signing needs `src/main/resources/META-INF/resources/privateKey.pem` (and `publicKey.pem`).
`make gen-keys` / `generate-keys.sh` produce them; they are gitignored. Without them the app won't sign tokens.

## Architecture

- **resource/** — JAX-RS REST endpoints (Quarkus REST, not RESTEasy Classic).
  - `AuthResource` — `/api/auth` register/login/refresh/logout/email. Sets `refreshToken` as an HttpOnly cookie.
  - `ForwardAuthResource` — `/verify`, called by Traefik ForwardAuth. Reactive (`Uni`). Reads `Authorization`, `X-Forwarded-Uri`, `X-Forwarded-Method`; returns 200 + `X-User-Id`/`X-User-Role` headers or 401.
- **service/**
  - `AuthService` — core register/login/refresh/logout/profile logic. `@Transactional` on write paths.
  - `JwtService` — issues access (15 min) and refresh (7 day) tokens via SmallRye `Jwt`. Access token carries `groups` (role), `email`, `id`.
  - `RedisService` — blocking refresh-token storage (`refresh:<userId>`) and token blacklist (`blacklist:<jti>`).
  - `AuthCacheService` — reactive Redis cache for ForwardAuth decisions, keyed by SHA-256 of `token|path|method`.
  - `ForwardAuthService` — parses/validates JWT, caches the decision (TTL = min(60s, token-remaining)).
  - `EmailService` — Resend email sending.
- **grpc/** — `AuthGrpcServiceImpl` implements `auth.proto` (`ValidateToken`, `GetUser`). Reactive (`Uni`), runs on port 9000.
- **entity/User** — Panache entity (public fields, `PanacheEntityBase`), UUID id, `Role` enum (USER/ADMIN).
- **repository/UserRepository** — `PanacheRepositoryBase<User, UUID>`.
- **exception/GlobalExceptionMapper** — maps JAX-RS exceptions to `{status, message, timestamp}` JSON.

## Infrastructure

- **PostgreSQL** — schema via **Flyway** (`src/main/resources/db/migration`), `migrate-at-start=true`. Hibernate generation is `none` (Flyway owns the schema). Add new migrations as `V2__*.sql` etc.
- **Redis** — refresh tokens, blacklist, ForwardAuth decision cache.
- **gRPC** — port 9000, reflection enabled.
- Ports: REST 8080, gRPC 9000. Swagger UI at `/q/swagger-ui`, health at `/q/health`.
- Test profile uses H2 in-memory with `drop-and-create` and Flyway disabled.

## Conventions

- DTOs are Java records nested in `AuthDto`, validated with Bean Validation (`@Email`, `@Size`, `@NotBlank`).
- Passwords hashed with `BcryptUtil` (quarkus-elytron-security).
- Code/comments mix Ukrainian and Russian — match the surrounding language of the file you edit.
- Indentation is **tabs** in the Java service/entity files.

## Known issues / gotchas

- `EmailService.main()` has a **hardcoded Resend API key** and hardcoded from/to addresses — should move to config/env and rotate the key.
- `AuthService.extractUserIdFromRefreshToken` decodes the refresh JWT **without verifying the signature** (validity is enforced only via the Redis lookup). Same pattern in `AuthGrpcServiceImpl.validateToken`.
- Cookies are set with `secure(false)` / `SameSite=LAX` for localhost; `AuthResource.buildRefreshCookie` documents the prod (HTTPS) variant to switch to.
- `.env.example` references **Kafka** config but `quarkus-kafka` is not yet a dependency in `pom.xml`.
