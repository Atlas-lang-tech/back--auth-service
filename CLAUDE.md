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

## Testing

- `make test` (or `./mvnw verify`) runs the full suite **and** enforces an **80% JaCoCo line-coverage gate** (build fails below it). `make coverage` opens the HTML report (`target/site/jacoco`).
- **Docker is required** — integration tests spin up real Postgres + Redis via **Testcontainers** (`org.atlas.test.InfraTestResource`), and the `%test` profile runs the real Flyway migrations against them (no H2).
- Two test styles: plain **Mockito** unit tests for service logic (`AuthServiceTest`, `ForwardAuthServiceTest`, `GlobalExceptionMapperTest`) and **`@QuarkusTest`** integration tests (REST via RestAssured, gRPC via `@GrpcClient`, repository, `JwtService`). Integration tests extend `AbstractIntegrationTest` (registers the global Testcontainers resource) and must annotate `@QuarkusTest` directly.
- Test-only JWT keys live in `src/test/resources/META-INF/resources/` and shadow the gitignored prod keys, so tests/CI need no `make gen-keys`.
- Coverage excludes generated gRPC stubs, `EmailService` (hardcoded key), and `GreetingResource`.
- CI: `.github/workflows/docker.yml` has a `test` job that gates `build-and-push` — the image is not built/pushed if tests or the coverage gate fail.

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
