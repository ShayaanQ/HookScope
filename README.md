# HookScope

HookScope is a self-hosted webhook inspection and reliability backend. M1-A establishes
the Java/Spring Boot foundation only: it has PostgreSQL/Flyway connectivity and public
health, but no endpoint management, webhook ingestion, or persisted domain data yet.

## Foundation

The root package is `io.hookscope`. The feature-oriented package layout begins with
`io.hookscope.config`; later checkpoints add endpoint management, ingestion/events,
and shared API-error features only when their checkpoint begins.

Prerequisites are Docker Compose V2 and a Docker-compatible daemon. For local Gradle
commands, the committed Gradle Wrapper provisions Gradle and the Java 21 toolchain.

Copy `.env.example` to `.env` and set a local PostgreSQL password. `HOOKSCOPE_ADMIN_TOKEN`
must remain empty for M1-A: admin-token enforcement explicitly begins in M1-B.

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

Always run the final cleanup command, including after a failed smoke test. The health
endpoint is public and returns a minimal status response.

## Directory overview

- `src/main/java/io/hookscope` — application and configuration foundation
- `src/test/java` — Docker-free unit tests
- `src/integrationTest/java` — real PostgreSQL/Testcontainers integration tests
- `config/checkstyle` — maintainable static-analysis configuration
- `.github/workflows` — CI verification

## Current limitations

M1-A intentionally contains no `/api/v1/**` or `/hooks/**` routes, endpoint/event
entities, schema migrations, authentication, or admin-token enforcement. The ingestion
size configuration defaults to 1 MiB for a future checkpoint; M1-A does not read bodies.
