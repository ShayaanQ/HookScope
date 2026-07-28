# HookScope

HookScope is a self-hosted webhook inspection and reliability backend. M1-B adds protected
endpoint management: an operator can create, retrieve, and list webhook endpoints backed by
PostgreSQL. Webhook ingestion and event storage are not implemented yet.

## Foundation

The root package is `io.hookscope`. The feature-oriented layout includes `config`, `endpoint`,
and `api.error`.

Prerequisites are Docker Compose V2 and a Docker-compatible daemon. For local Gradle
commands, the committed Gradle Wrapper provisions Gradle and the Java 21 toolchain.

Copy `.env.example` to `.env`, set a local PostgreSQL password, and generate a nonblank
`HOOKSCOPE_ADMIN_TOKEN` with at least 32 characters (for example, `openssl rand -base64 48`).
The token is temporary single-operator protection, not final user authentication.

## Verification

Run the authoritative commands exactly:

```bash
./gradlew spotlessCheck
./gradlew checkstyleMain checkstyleTest checkstyleIntegrationTest
./gradlew test
./gradlew integrationTest
./gradlew clean check bootJar
```

`integrationTest` uses Testcontainers PostgreSQL 17.10; Docker must be available for
that command. Unit tests do not require Docker. The executable artifact is written to
`build/libs/` by `./gradlew bootJar`.

## Compose smoke test

```bash
docker compose up --build -d --wait --wait-timeout 120
docker compose ps
curl --fail http://localhost:8080/actuator/health
docker compose down -v
```

Always run the final cleanup command, including after a failed smoke test. The health endpoint
is public and returns a minimal status response. Management routes require the
`X-HookScope-Admin-Token` header:

```bash
set -a
. ./.env
set +a
token="$HOOKSCOPE_ADMIN_TOKEN"
curl --fail --request POST http://localhost:8080/api/v1/endpoints \
  --header "X-HookScope-Admin-Token: $token" \
  --header 'Content-Type: application/json' \
  --data '{"name":"Payments sandbox"}'
curl --fail --header "X-HookScope-Admin-Token: $token" \
  http://localhost:8080/api/v1/endpoints
```

Each created endpoint returns an opaque 32-character `publicKey` and a relative
`ingestionPath` (`/hooks/{publicKey}`). That path is reserved for a later checkpoint.

## M1-B endpoint API

All endpoint-management requests require `X-HookScope-Admin-Token`. `POST /api/v1/endpoints`
accepts `{"name":"..."}`; names are trimmed and must be 1–120 characters. `GET
/api/v1/endpoints/{endpointId}` retrieves an endpoint. `GET /api/v1/endpoints` supports
zero-based `page` (default `0`) and `size` (default `20`, maximum `100`) and orders endpoints by
`createdAt` descending, then ID descending. Endpoint responses contain `id`, `name`, `publicKey`,
`ingestionPath`, and `createdAt`.

Application errors use `application/problem+json` with `type`, `title`, `status`, `detail`,
`instance`, and a stable `code`. M1-B exposes `VALIDATION_ERROR`, `UNAUTHORIZED`,
`ENDPOINT_NOT_FOUND`, and `MALFORMED_REQUEST` for the corresponding client-facing cases.

## Directory overview

- `src/main/java/io/hookscope` — application and configuration foundation
- `src/test/java` — Docker-free unit tests
- `src/integrationTest/java` — real PostgreSQL/Testcontainers integration tests
- `config/checkstyle` — maintainable static-analysis configuration
- `.github/workflows` — CI verification

## Current limitations

M1-B intentionally contains no `/hooks/**` route, event table, event API, endpoint update or
delete API, real user accounts, or frontend. The temporary administrator token is not a
replacement for authentication or authorization. The ingestion-size configuration remains a
future-checkpoint setting; M1-B does not read request bodies.
