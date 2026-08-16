# M1-D release-verification evidence

This document records local M1-D evidence from the uncommitted release-verification worktree.
It does not change the human-owned statuses in `docs/acceptance/M1.md`.

## Criteria matrix

| ID | Implementation or documentation | Current-run evidence | Result / remaining dependency |
|---|---|---|---|
| M1D-001 | `ReleaseVerificationIntegrationTest.migratesAnEmptyDatabaseInOrderAndProducesTheLockedFinalSchema` | `./gradlew integrationTest --tests io.hookscope.ReleaseVerificationIntegrationTest` passed against a dedicated fresh PostgreSQL 17.10 container. Before Flyway ran, the test verified that public Flyway/domain tables did not exist; it then ran exactly V1 then V2, verified successful history ranks `1:1:true` and `2:2:true`, called Flyway validation, and asserted the final locked schemas. | Local automated evidence complete. |
| M1D-002 | `ReleaseVerificationIntegrationTest.migratesAnEmptyDatabaseInOrderAndProducesTheLockedFinalSchema` | The same Testcontainers run verified the named public-key unique constraint plus exact endpoint and event ordering-index definitions through PostgreSQL catalogs. | Local automated evidence complete. |
| M1D-003 | `ReleaseVerificationIntegrationTest.capturesRepresentativeUnforcedPlansForTheRequiredListQueries`; retained plan capture | The test batches 400 endpoints and 2,500 events, with 600 events on the selected endpoint, runs `ANALYZE` on both freshly seeded tables, then runs unforced `EXPLAIN (COSTS OFF)` for both list query shapes. A PostgreSQL 17.10 capture also recorded the observed plans. | Local automated/manual evidence complete; interpretation below. |
| M1D-004 | `docs/architecture/overview.md` | Manual code/Compose/database review completed against controllers, Flyway, Dockerfile, `compose.yaml`, and `application.yaml`. | Local documentation evidence complete. |
| M1D-005 | `docs/architecture/overview.md` | ADR-001 through ADR-005 conformance table maps each decision to implementation and existing test evidence. | Local documentation evidence complete. |
| M1D-006 | `README.md`; `docs/testing.md` | Documentation now includes M1 architecture, setup, verification, release-rehearsal checks, cleanup, privacy inspection, and required limitations. | Local documentation evidence complete. |
| M1D-007 | Gradle verification commands in `docs/testing.md` | `./gradlew clean check bootJar` passed after all M1-D edits, including formatting, Checkstyle, unit tests, integration tests, and executable JAR creation. | Local automated evidence complete. |
| M1D-008 | `.github/workflows/verify.yml` | Existing workflow runs `./gradlew clean check bootJar` and retains reports on failure. | Awaiting an authorized commit/push and remote CI on the reviewed branch. |
| M1D-009 | README/testing clean-checkout guidance | Guidance states that a clean checkout must be a newly cloned final committed branch. | Awaiting the later reviewed commit/push; an uncommitted worktree is not claimed as evidence. |
| M1D-010 | Compose rehearsal; README/testing guidance | Current-worktree rehearsal made PostgreSQL and HookScope healthy and public health returned `200`. | Rehearsal complete; final clean-checkout proof depends on M1D-009's final committed branch. |
| M1D-011 | Compose rehearsal | Missing/invalid token `401`; endpoint create `201`; public ingest `204`; event list/detail `200`. Default/configured exact headers were redacted; a similarly named header remained visible. | Local manual evidence complete. |
| M1D-012 | Compose rehearsal | Exact limit `204`; oversize `413`; malformed raw query `400`; HEAD/OPTIONS/raw TRACE `405`; health remained `200`. Event count was `2` before failures and remained `2` after oversize, malformed query, and unsupported methods. | Local manual evidence complete. |
| M1D-013 | Scoped production/test review | Final search found no TODO, FIXME, placeholder, ignored/disabled test, unsupported-operation stub, sample secret, or temporary debug output in `src/main` or `src/test`/`src/integrationTest`. Historical prompts and source-of-truth wording are excluded as non-shipped implementation. | Local review evidence complete. |
| M1D-014 | This matrix; `docs/acceptance/M1.md` | M1-D rows map to current evidence without changing acceptance statuses. Cross-cutting rows remain outside this implementation scope and reviewer-owned. | Final human status review remains required. |

## Representative PostgreSQL plans

The integration test seeded exactly 400 endpoints and 2,500 events in batches, with 600 events
belonging to the endpoint used for the filtered event-list plan. A PostgreSQL 17.10 capture was
reseeded with those same cardinalities and query shapes without disabling sequential scans or
forcing an index. The observed endpoint-list plan was:

```text
Limit
  ->  Index Scan using webhook_endpoints_created_at_id_desc_idx on webhook_endpoints
```

The observed endpoint-filtered event-list plan was:

```text
Limit
  ->  Index Scan using webhook_events_endpoint_id_received_at_id_desc_idx on webhook_events
        Index Cond: (endpoint_id = '<generated endpoint UUID>'::uuid)
```

These plans are reasonable for the seeded ordering and endpoint-filter predicates. They are
observations, not an asserted execution-plan contract: PostgreSQL may choose a different valid
plan as statistics, cardinality, configuration, or PostgreSQL version changes.

## Cross-contract test review

### Pagination coverage

| Surface | Empty/default | One page/partial final | Maximum size | Invalid page/size | Timestamp tie ordering |
|---|---|---|---|---|---|
| Endpoints | `EndpointManagementIntegrationTest.listsEmptyDefaultMaximumAndPartialPages` | Same method (`25` records gives a full first page and a partial final page) | Same method (`size=100`) | `EndpointManagementIntegrationTest.rejectsInvalidPagination` | `EndpointManagementIntegrationTest.ordersTimestampTiesByDescendingId` |
| Events | `IngestionIntegrationTest.handlesEmptyDefaultMaximumAndPartialEventPages` | Same method (`25` records gives one full and one partial page) | Same method (`size=100`) | Same method (`page=-1`, `size=0`, `size=101`) | `IngestionIntegrationTest.listsEventsWithLockedFieldsPaginationAndDeterministicTieOrdering` |

Both surfaces therefore cover empty results, defaults (`page=0`, `size=20`), one page, a partial
final page, `size=100`, invalid page/size values, and deterministic descending timestamp/UUID
tie-breaking. No additional pagination test was required.

### Problem Details coverage

The endpoint and ingestion integration suites use their `assertProblem` helpers to require
`application/problem+json` and exactly `type`, `title`, `status`, `detail`, `instance`, and `code`.

| Error | Exact automated coverage |
|---|---|
| `VALIDATION_ERROR` | `EndpointManagementIntegrationTest.rejectsBlankAndOverlongNames`, `rejectsInvalidPagination`; event pagination validation in `IngestionIntegrationTest.handlesEmptyDefaultMaximumAndPartialEventPages`. |
| `UNAUTHORIZED` | `EndpointManagementIntegrationTest.protectsManagementRoutesButLeavesHealthPublic`; `IngestionIntegrationTest.problemDetailsAndCapturedOutputRemainSanitized`. |
| `ENDPOINT_NOT_FOUND` | `EndpointManagementIntegrationTest.retrievesAnEndpointAndReturnsNotFoundForAnUnknownUuid`; `IngestionIntegrationTest.rejectsUnknownKeysAndUnsupportedMethodsWithProblemDetails`. |
| `EVENT_NOT_FOUND` | `IngestionIntegrationTest.listsEventsWithLockedFieldsPaginationAndDeterministicTieOrdering`. |
| `PAYLOAD_TOO_LARGE` | `IngestionIntegrationTest.rejectsBodiesOverTheConfiguredLimitWithoutPersistence`; `rejectsChunkedUnknownLengthBodiesOverTheLimitWithoutPersistenceAndRemainsHealthy`. |
| `MALFORMED_REQUEST` | `EndpointManagementIntegrationTest.returnsMalformedRequestProblemsForMalformedUuidPaginationAndJson`; `IngestionIntegrationTest.rejectsMalformedRawQueryEncodingAndEncodedNulWithoutPersistence`; malformed UUID coverage in `problemDetailsAndCapturedOutputRemainSanitized`. |
| `METHOD_NOT_ALLOWED` | `IngestionIntegrationTest.rejectsUnknownKeysAndUnsupportedMethodsWithProblemDetails`; raw TRACE coverage in `traceIsRejectedBeforeIngestionAndCreatesNoEvent`; Compose raw HEAD/OPTIONS/TRACE rehearsal. |

### Complete M1 acceptance traceability

The following compact index names every acceptance row and points to its existing evidence. The
statuses remain exclusively in `docs/acceptance/M1.md` and were not edited.

| Rows | Evidence location and procedure |
|---|---|
| M1A-001, M1A-002, M1A-003 | `build.gradle.kts`, `compose.yaml`, Gradle version output, and final `./gradlew clean check bootJar`. |
| M1A-004, M1A-005, M1A-006, M1A-007, M1A-008, M1A-009, M1A-010, M1A-011 | `gradlew`, `build.gradle.kts`, `docs/testing.md`, Testcontainers reports, and final Checkstyle/test/integration reports. |
| M1A-012, M1A-013, M1A-014, M1A-015, M1A-016 | `compose.yaml`, `Dockerfile`, `application.yaml`, Compose health rehearsal, and public health response. |
| M1A-017, M1A-018, M1A-019, M1A-020, M1A-021 | `HookScopePropertiesTest`, `.env.example`, `docs/testing.md`, `.github/workflows/verify.yml`, and final local verification. |
| M1A-022, M1A-023, M1A-024 | M1-A review record, feature-oriented package tree, and README setup/verification documentation. |
| M1B-001, M1B-015 | `EndpointManagementIntegrationTest.migratesAnEmptyDatabaseWithTheLockedEndpointSchema`, `databaseRejectsDuplicatePublicKeys`, and `ReleaseVerificationIntegrationTest.migratesAnEmptyDatabaseInOrderAndProducesTheLockedFinalSchema`. |
| M1B-002, M1B-003, M1B-004, M1B-005, M1B-006, M1B-007 | `EndpointManagementIntegrationTest` create, boundary, retrieve, and unknown-UUID methods. |
| M1B-008, M1B-009, M1B-010, M1B-011, M1B-012 | `EndpointManagementIntegrationTest.listsEmptyDefaultMaximumAndPartialPages`, `rejectsInvalidPagination`, and `ordersTimestampTiesByDescendingId`. |
| M1B-013, M1B-014 | `SecureRandomPublicKeyGeneratorTest`, `EndpointServiceTest`, ADR-001, and implementation review. |
| M1B-016, M1B-017 | `EndpointServiceTest` bounded collision cases and `EndpointManagementIntegrationTest.createsAnEndpointWithTheLockedResponseContractAndRelativePath`. |
| M1B-018, M1B-019, M1B-020, M1B-021, M1B-022, M1B-023, M1B-024 | `AdminTokenVerifierTest`, `HookScopePropertiesTest`, endpoint/context-path integration tests, configuration review, and Compose privacy checks. |
| M1B-025 | ADR-001 and ADR-002 plus the ADR conformance table above. |
| M1C-001, M1C-002, M1C-003, M1C-004, M1C-005, M1C-006 | `IngestionIntegrationTest.migrationUsesTheLockedEventSchema`, `supportsEveryLockedIngestionMethod`, `publiclyPersistsAndReturnsAnExactRedactedDetail`, `rejectsUnknownKeysAndUnsupportedMethodsWithProblemDetails`, `traceIsRejectedBeforeIngestionAndCreatesNoEvent`, and Compose method checks. |
| M1C-007, M1C-008, M1C-009, M1C-010, M1C-011, M1C-012 | `IngestionIntegrationTest` header/query methods and `AdditionalRedactionIntegrationTest.redactsConfiguredExactNameCaseInsensitivelyButNotSubstrings`. |
| M1C-013, M1C-014, M1C-015, M1C-016, M1C-017, M1C-018, M1C-019 | `IngestionIntegrationTest.acceptsTheExactBodyLimit`, `rejectsBodiesOverTheConfiguredLimitWithoutPersistence`, `rejectsChunkedUnknownLengthBodiesOverTheLimitWithoutPersistenceAndRemainsHealthy`, binary detail, size, digest, and content-type methods. |
| M1C-020, M1C-021 | `IngestionIntegrationTest.storesDirectPeerAddressAndServerGeneratedTimestamp` and V2 schema assertions. |
| M1C-022, M1C-023, M1C-024, M1C-025, M1C-026, M1C-027, M1C-028 | `IngestionIntegrationTest` projection/detail/pagination/error-contract methods and the Problem Details review above. |
| M1C-029, M1C-030, M1C-031, M1C-032 | Captured-output tests, Compose retained-log searches, `application.yaml`, and ADR-003 through ADR-005. |
| M1D-001, M1D-002, M1D-003 | `ReleaseVerificationIntegrationTest` fresh-container migration/schema/index/plan evidence and the representative-plan section above. |
| M1D-004, M1D-005, M1D-006 | `docs/architecture/overview.md`, ADR conformance table, README, and `docs/testing.md`. |
| M1D-007 | Final local `./gradlew clean check bootJar`. |
| M1D-008 | `.github/workflows/verify.yml` is prepared; green remote CI awaits the later reviewed commit/push. |
| M1D-009 | Clean-clone procedure is documented; execution awaits the final committed branch. |
| M1D-010 | Current-worktree Compose rehearsal passed; its clean-checkout portion awaits M1D-009's true clone phase. |
| M1D-011, M1D-012 | Retained Compose procedure/results above, including create → ingest → list → detail and all failure/no-persistence checks. |
| M1D-013 | Scoped `rg` review of M1 production/test code and fixtures. |
| M1D-014 | This complete index plus the human-owned acceptance checklist. |
| SEC-001, SEC-002, SEC-003, SEC-004, SEC-005 | Secret/configuration review, ADR-001/002, bounded-reader tests, `application.yaml`, captured-output tests, and Compose log searches. Final status remains human-owned. |
| DOC-001, DOC-002 | Review of `AGENTS.md`, milestone, acceptance, testing docs, README, architecture overview, ADRs, and current implementation. Final status remains human-owned. |

## Compose rehearsal summary

The locally generated non-production token and runtime markers were kept outside the repository.
Retained Compose logs were searched before cleanup and did not contain the runtime token, public
key, complete ingestion path, raw-body marker, sensitive-header marker, or malformed-query marker.
Forged `X-Forwarded-For`, `Forwarded`, and `X-Real-IP` values were absent from detail output; the
direct peer remained authoritative. The named Compose containers, network, and volume were removed
after evidence capture.

## Scope and status ownership

M1-D adds no product feature and does not authorize cross-cutting implementation. The M1-A through
M1-C acceptance evidence remains in the existing test suites and prior human-reviewed records.
All acceptance statuses, including cross-cutting rows, remain human-owned and unchanged in this
worktree.
