# Codex Execution Prompt — HookScope M1-A

Paste the text below into Codex from the HookScope repository root after placing these
source-of-truth files in the repository:

- `AGENTS.md`
- `docs/milestones/M1.md`
- `docs/acceptance/M1.md`
- `docs/testing.md`

---

You are implementing **HookScope Milestone 1, checkpoint M1-A only**.

Treat the repository documents as authoritative:

1. `AGENTS.md`
2. `docs/milestones/M1.md`
3. `docs/acceptance/M1.md`
4. `docs/testing.md`

Read all four files completely before making any change.

## Execution mode

Perform the required pre-edit analysis from `AGENTS.md`, then continue directly into
implementation if and only if there is no real blocker. Do not pause merely to request
approval for the plan. Stop only for a contradiction, unsafe repository state, missing
required capability, unavailable dependency/version that cannot be obtained safely, or
an ambiguity that would require changing a locked contract.

Do not commit, push, open a pull request, or begin M1-B.

## Assigned scope

Implement **M1-A — Repository foundation** and target only these acceptance criteria:

- `M1A-001` through `M1A-024`

Do not change any acceptance status to `PASS`.

Cross-cutting documentation consistency may be reported, but do not attempt later
checkpoint criteria.

## Required pre-edit response

Before editing, output all of the following:

1. `git status --short --branch` results and an assessment of unexpected changes.
2. Your restatement of M1-A deliverables and non-goals.
3. The exact acceptance IDs being targeted.
4. Expected files to create or modify.
5. The complete risk table required by `AGENTS.md`.
6. A criterion-to-implementation/test map for `M1A-001` through `M1A-024`.
7. Any dependency additions, with one-line justification for each.

If the repository already contains code, preserve valid work and adapt the smallest
possible implementation. Do not overwrite unrelated or unexpected changes.

## M1-A implementation requirements

Build a clean Java backend foundation with no endpoint/event domain behavior.

### Platform

- Java 21 toolchain.
- Spring Boot 3.5.16.
- Gradle Wrapper 8.14.5 using Kotlin DSL.
- PostgreSQL 17.10 in Compose and Testcontainers.
- One Spring Boot modular monolith.

Pin every explicitly declared Gradle plugin and container image. Never use `latest`.
If selecting an exact Java 21 build/runtime image tag, use an available non-floating
patch tag or immutable digest and report the selection. Do not invent a Docker tag that
cannot be pulled.

If this is an empty repository and neither a valid Gradle Wrapper nor a usable system
Gradle exists, obtain the initial wrapper/project only through an official trusted
source such as Spring Initializr or the official Gradle distribution. Do not fabricate,
base64-embed, or omit `gradle-wrapper.jar`. If trusted retrieval is unavailable, stop and
report the blocker.

### Minimum dependencies

Add only dependencies required for the approved M1 foundation. Expected categories:

- Spring Web
- Spring Actuator
- Spring Data JPA
- Bean Validation
- Flyway core and PostgreSQL database support
- PostgreSQL JDBC driver
- Spring Boot test support
- Spring Boot Testcontainers support
- JUnit Jupiter / AssertJ through Spring Boot testing
- Testcontainers JUnit and PostgreSQL modules

Do not add Spring Security, Redis, messaging, frontend, OpenAPI UI, Lombok, MapStruct,
or later-milestone dependencies in M1-A.

### Package structure

Choose one professional root package and document it in the README. Use a minimal
feature-oriented structure that can later hold:

- configuration,
- endpoint management,
- ingestion/events,
- shared API errors.

Do not create speculative domain classes, interfaces, empty layers, or future-feature
stubs.

### Gradle quality and test contract

Implement `docs/testing.md` exactly:

- Spotless with Google Java Format.
- Formatting for Gradle Kotlin DSL and supported repository text files.
- Checkstyle for `main`, `test`, and `integrationTest`.
- Dedicated `src/integrationTest/java` and `src/integrationTest/resources` source set.
- `integrationTest` task of type `Test`, using JUnit Platform.
- Integration-test dependencies/classpaths configured correctly.
- `integrationTest` runs after `test`.
- `check` depends on:
  - `spotlessCheck`,
  - `checkstyleMain`,
  - `checkstyleTest`,
  - `checkstyleIntegrationTest`,
  - `test`,
  - `integrationTest`.
- HTML and XML reports generated.
- At least one real integration test discovered and executed.

Use a reasonable, maintainable Checkstyle configuration. Do not create an enormous
custom style policy.

### Application foundation

Create:

- Spring Boot application entry point.
- Minimal configuration required to run.
- Public `/actuator/health` endpoint.
- Health details configured so secrets/configuration are not exposed.
- Typed configuration namespace for:
  - future admin token,
  - ingestion maximum body size with M1 default of 1 MiB.

M1-A must not enforce the admin token. There are no `/api/v1/**` routes yet.

The app must be able to start in M1-A without an admin token. Include an `.env.example`
with no usable token. Document that enforcement starts in M1-B.

### Flyway and PostgreSQL integration

Wire Spring Data JPA, PostgreSQL, and Flyway correctly.

Do not create endpoint/event tables or a fake connectivity/probe table.

Create a real Testcontainers integration test that:

- starts PostgreSQL 17.10,
- starts the Spring application context against it,
- proves database connectivity,
- proves Flyway initializes and validates without errors on an empty database,
- and produces visible non-zero integration-test evidence.

Use Spring Boot service-connection support where appropriate and simple.

Unit tests must not require Docker. Add at least one small meaningful unit test if the
foundation exposes testable configuration behavior; do not create a fake test solely to
inflate counts.

### Docker and Compose

Create a production-minded but simple multi-stage `Dockerfile`:

- builds using the Gradle Wrapper,
- runs the executable Boot JAR,
- uses a pinned Java 21 runtime image/tag or digest,
- runs as a non-root user,
- and contains no secret.

Create `compose.yaml` with:

- HookScope application service,
- PostgreSQL 17.10 service,
- pinned images,
- database health check,
- application health check,
- dependency/readiness ordering,
- environment variable wiring,
- persistent local database volume,
- and no hard-coded admin token.

After documented environment setup, this must work:

```bash
docker compose up --build -d
docker compose ps
curl --fail http://localhost:8080/actuator/health
docker compose down -v
```

Ensure cleanup occurs even if smoke verification fails.

### CI

Create a GitHub Actions workflow that:

- checks out the repository,
- installs Java 21,
- uses the Gradle Wrapper,
- runs `./gradlew clean check bootJar`,
- clearly executes integration tests,
- and uploads useful unit/integration test reports when verification fails.

Caching is allowed but must not alter verification semantics.

### Documentation

Create or update the README with:

- project purpose,
- current M1-A scope,
- selected root package,
- prerequisites,
- environment setup,
- exact commands from `docs/testing.md`,
- Compose startup and cleanup,
- health check,
- directory overview,
- current limitations,
- and an explicit warning that admin-token enforcement begins in M1-B.

Keep the four source-of-truth files intact and mutually consistent. Do not change their
locked decisions merely to fit an easier implementation. Do not mark acceptance rows
`PASS`.

### Repository hygiene

Include a suitable `.gitignore` for:

- Gradle/build output,
- `.env`,
- IDE metadata,
- OS files,
- logs,
- and local runtime artifacts.

Do not commit generated build output, real environment files, secrets, container data,
or test reports.

## M1-A non-goals — enforce strictly

Do not implement:

- endpoint or event entities,
- endpoint/event repositories or services,
- Flyway domain migrations,
- `/api/v1/**` controllers,
- `/hooks/**` controllers,
- admin-token filtering or enforcement,
- Problem Details domain error codes,
- public-key generation,
- body capture/redaction,
- Redis or messaging,
- retries, replay, delivery, DLQ,
- HMAC verification,
- SSE/WebSockets,
- frontend/CLI,
- real authentication,
- organizations or users.

Do not add placeholders for these features.

## Required verification

Run the exact applicable commands from `docs/testing.md`:

```bash
./gradlew spotlessCheck
./gradlew checkstyleMain checkstyleTest checkstyleIntegrationTest
./gradlew test
./gradlew integrationTest
./gradlew clean check bootJar
```

Then run the Compose smoke sequence:

```bash
docker compose up --build -d
docker compose ps
curl --fail http://localhost:8080/actuator/health
docker compose down -v
```

If network/remote-repository access prevents running a command, report that criterion as
unverified. Do not call it complete.

If GitHub credentials/remote access are available, push is still forbidden. Prepare the
workflow locally and report that `M1A-020`/remote CI cannot be marked complete until a
human explicitly approves a push and observes a run.

## Required final handoff

At the end, provide:

1. `git status --short --branch`.
2. `git diff --stat`.
3. Changed-file summary.
4. Exact commands run and pass/fail results.
5. Unit and integration test counts/report paths.
6. Compose service/health results.
7. Evidence mapping for every `M1A-001` through `M1A-024` criterion.
8. A clear list of criteria not fully verified, especially remote CI or fresh-clone
   checks that could not be completed locally.
9. Known limitations and risks.
10. Relevant diffs/excerpts for:
    - `build.gradle.kts`,
    - integration-test wiring,
    - Dockerfile,
    - `compose.yaml`,
    - GitHub Actions,
    - application configuration.
11. A proposed commit message, but do not commit.

Stop after the M1-A handoff and wait for human review.

---
