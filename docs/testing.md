# Testing and Verification Commands

This file defines the authoritative commands for HookScope. Coding agents must not
replace these commands with approximate equivalents when claiming acceptance evidence.

## Tooling contract

The repository must use:

- Gradle Wrapper committed to the repository.
- Spotless with Google Java Format for Java sources.
- Spotless formatting for Gradle Kotlin DSL and supported repository text files.
- Checkstyle for `main`, `test`, and `integrationTest` Java source sets.
- JUnit Platform for unit and integration tests.
- A dedicated `integrationTest` source set backed by Testcontainers and PostgreSQL.
- Spring Boot's `bootJar` task for the production artifact.

## Required source-set contract

Integration tests live in:

```text
src/integrationTest/java
src/integrationTest/resources
```

The Gradle build must define an `integrationTest` task with these properties:

- task type `Test`,
- JUnit Platform enabled,
- integration-test implementation/runtime classpaths extend the corresponding unit-test
  configurations where appropriate,
- Testcontainers and PostgreSQL test dependencies are available,
- `integrationTest` runs after `test`,
- `check` depends on `integrationTest`,
- `check` also depends on `spotlessCheck`, `checkstyleMain`, `checkstyleTest`, and
  `checkstyleIntegrationTest`,
- HTML and XML test reports are generated,
- and CI evidence must show at least one integration test was discovered and executed.

A successful Gradle task with zero discovered integration tests does not satisfy the
integration-test acceptance criteria.

## Formatting

### Check formatting

```bash
./gradlew spotlessCheck
```

### Apply formatting

```bash
./gradlew spotlessApply
```

`spotlessApply` modifies files and must not be used as a substitute for reporting what
was incorrectly formatted.

## Static analysis

```bash
./gradlew checkstyleMain checkstyleTest checkstyleIntegrationTest
```

Checkstyle configuration must be maintainable and appropriate for a small Spring Boot
service. Do not introduce a large custom ruleset unrelated to correctness or readability.

## Unit tests

```bash
./gradlew test
```

Unit tests must not require Docker or external services.

## Integration tests

```bash
./gradlew integrationTest
```

Integration tests may require Docker because they run against disposable PostgreSQL
containers through Testcontainers.

Migration-from-empty-database verification belongs in this integration-test suite.

## Full verification

```bash
./gradlew clean check bootJar
```

This is the authoritative local verification command. Through `check`, it must execute:

- `spotlessCheck`,
- `checkstyleMain`,
- `checkstyleTest`,
- `checkstyleIntegrationTest`,
- `test`,
- and `integrationTest`.

No smaller command may be reported as equivalent full verification.

## Production artifact

```bash
./gradlew bootJar
```

The resulting executable Spring Boot JAR must be created under `build/libs/`.

## Compose smoke test

After completing the documented environment setup:

```bash
docker compose up --build -d --wait --wait-timeout 120
docker compose ps
curl --fail http://localhost:8080/actuator/health
docker compose down -v
```

For M1-C smoke verification, create an endpoint with the administrator token, send a public
request to its returned relative `ingestionPath`, then use the protected event-list and
event-detail routes to inspect the persisted event. Confirm redacted headers remain
`[REDACTED]`, forged forwarding headers do not replace the direct source IP, oversized bodies
receive `413`, and `HEAD`, `OPTIONS`, and `TRACE` do not create events.
Tomcat rejects raw `CONNECT` before Spring with `501`; M1 does not promise an application Problem
Detail for container-rejected methods.

For the optional Compose redaction setting, set
`HOOKSCOPE_ADDITIONAL_SENSITIVE_HEADERS=X-Custom-Secret` before startup, send an
`x-CUSTOM-secret` request header, and verify the event detail contains `[REDACTED]` while a
similarly named header remains visible. Also inspect retained Compose logs for the runtime admin
token, endpoint key, full hook path, raw-body marker, and sensitive-header marker before cleanup.

Requirements:

- Report the output or meaningful result of every command.
- Both PostgreSQL and HookScope must become healthy.
- The health request must succeed without an admin token.
- Cleanup must run even if startup or the health request fails.
- Do not leave containers or volumes running after a verification session unless the
  human explicitly requests it.

A shell-safe manual pattern is:

```bash
set -o pipefail
cleanup() { docker compose down -v; }
trap cleanup EXIT
docker compose up --build -d --wait --wait-timeout 120
docker compose ps
curl --fail http://localhost:8080/actuator/health
```

## M1-D release rehearsal

M1-D repeats the Compose flow from the final worktree and preserves sanitized logs before cleanup.
Use a generated local token and runtime markers rather than a committed value. Verify, in order:

1. PostgreSQL and HookScope are healthy, and health succeeds without a token.
2. Missing and invalid management tokens return `401`.
3. A token-protected endpoint create returns `201`; public ingestion returns `204`; protected event
   list and detail return `200`.
4. Default and configured exact sensitive headers are `[REDACTED]`; similarly named headers remain
   visible; forged forwarding headers do not replace the direct source IP.
5. Exactly 1,048,576 bytes is accepted; 1,048,577 bytes returns `413` without a new event.
6. A raw malformed query returns `400 MALFORMED_REQUEST` without a new event. HEAD, OPTIONS, and
   raw TRACE create no event. Health remains available after failure cases.
7. Retained Compose logs do not contain the runtime token, endpoint key, complete ingestion path,
   body marker, sensitive-header marker, or malformed-query marker.

Use `docker compose exec postgres psql` only to inspect the local disposable Compose database for
event counts or captured `EXPLAIN (COSTS OFF)` output. Do not disable sequential scans or force an
index when capturing representative plans. Planner choices vary with PostgreSQL version, data
volume, and statistics.

A clean-checkout verification is separate from this rehearsal: it must use a newly cloned final
committed branch, not an uncommitted directory or copied worktree.

## CI contract

GitHub Actions must:

1. check out the repository,
2. install the documented Java version,
3. use the Gradle Wrapper,
4. run `./gradlew clean check bootJar`,
5. preserve useful test reports when verification fails,
6. and clearly show that integration tests executed rather than being silently skipped.

CI may add caching, but caching must not change verification behavior.

## Evidence reporting

At handoff, report:

- exact commands run,
- pass/fail status,
- relevant test counts,
- generated report locations,
- Compose service health,
- and the acceptance IDs each result supports.

Never claim a command passed if it was not executed in the current repository state.
