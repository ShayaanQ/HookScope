# HookScope

HookScope is a self-hosted webhook inspection and reliability backend. M1-C adds durable public
webhook ingestion and protected event inspection backed by PostgreSQL.

## Foundation

The root package is `io.hookscope`. The feature-oriented layout includes `config`, `endpoint`,
`event`, and `api.error`.

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
`ingestionPath` (`/hooks/{publicKey}`). Send `GET`, `POST`, `PUT`, `PATCH`, or `DELETE` to that
path without an administrator token. Accepted request bodies are limited to 1 MiB by default;
sensitive headers are redacted before storage. Configure additional exact header names with the
comma-separated `HOOKSCOPE_ADDITIONAL_SENSITIVE_HEADERS` value; HookScope never trusts forwarding
headers for a request source IP.

For a complete M1-C flow, retain the endpoint response and use its fields as follows:

```bash
curl --fail --request POST http://localhost:8080/api/v1/endpoints \
  --header "X-HookScope-Admin-Token: $token" \
  --header 'Content-Type: application/json' --data '{"name":"Payments sandbox"}'
# Copy id and ingestionPath from the response into these shell variables.
endpoint_id='replace-with-response-id'
ingestion_path='/hooks/replace-with-response-public-key'
curl --fail --request POST "http://localhost:8080$ingestion_path?attempt=1" \
  --header 'Authorization: sender-secret' --data-binary 'payment event'
curl --fail --header "X-HookScope-Admin-Token: $token" \
  "http://localhost:8080/api/v1/endpoints/$endpoint_id/events?page=0&size=20"
```

Event detail is available at `/api/v1/endpoints/{endpointId}/events/{eventId}`. Event-list pages
contain only summary fields, use zero-based pagination (`page=0`, `size=20`, maximum `100`), and
order by receipt time then ID descending. Detail returns redacted headers, URL query parameters,
standard-Base64 body bytes, byte count, SHA-256, direct socket source IP, and receipt timestamp.

Ingestion accepts `GET`, `POST`, `PUT`, `PATCH`, and `DELETE` and returns `204` after persistence.
Bodies above the default 1 MiB limit receive `413 PAYLOAD_TOO_LARGE` without an event record.
Forwarding headers are intentionally ignored: M1-C has no trusted-proxy support.

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
- `src/main/java/io/hookscope/event` — public ingestion and protected event querying
- `src/test/java` — Docker-free unit tests
- `src/integrationTest/java` — real PostgreSQL/Testcontainers integration tests
- `config/checkstyle` — maintainable static-analysis configuration
- `.github/workflows` — CI verification

## Current limitations

M1-C intentionally contains no endpoint update/delete API, delivery, replay, retries, retention,
live updates, provider-specific handshakes, trusted-proxy support, real user accounts, or frontend.
The temporary administrator token is not a replacement for authentication or authorization. Events
are retained indefinitely in M1; the configurable ingestion-size limit is a safety limit, not a
provider-specific policy.
