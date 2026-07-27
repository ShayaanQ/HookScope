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
docker compose up --build -d
docker compose ps
curl --fail http://localhost:8080/actuator/health
docker compose down -v
```

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
docker compose up --build -d
docker compose ps
curl --fail http://localhost:8080/actuator/health
```

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
