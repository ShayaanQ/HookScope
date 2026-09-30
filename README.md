# HookScope

HookScope is a self-hosted webhook observability and reliability platform. It captures incoming
webhook events, preserves them for inspection, forwards them to configured destinations, and
provides replay plus bounded Redis Streams-backed retries for failed deliveries.

Java 21 · Spring Boot · PostgreSQL · Redis Streams · Docker · Testcontainers

## Why HookScope?

Webhook failures are difficult to debug because delivery crosses system boundaries and a transient
downstream error can be hard to reproduce. HookScope provides a self-hosted place to receive
events, inspect what arrived, forward events, review delivery results, replay stored events, and
retry failed deliveries.

## Features

### Event capture and inspection

- Durable PostgreSQL persistence for received webhook events.
- Exact raw request-body storage with body size and SHA-256 metadata.
- Request method, content type, direct source IP, headers, and query-parameter inspection.
- Paginated event listing and detail retrieval with deterministic ordering.
- Event filtering by method and exclusive `receivedAfter` / `receivedBefore` timestamps.
- Bounded request bodies and sensitive-header redaction before persistence.

### Delivery and replay

- Configured destinations attached to webhook endpoints.
- Synchronous initial delivery after durable event persistence.
- Replay of a stored event to a configured destination.
- Persisted logical delivery and per-attempt history.
- Recorded downstream HTTP and transport failures.

### Reliability

- Redis Streams retry processing after a failed first delivery.
- A maximum of three total delivery attempts.
- One retry stream, consumer group, and dead-letter stream.
- PostgreSQL remains the source of truth for events, destinations, deliveries, and attempts.

### Security and defensive behavior

- Management APIs protected by `X-HookScope-Admin-Token`.
- Destination URL validation and exact host allowlisting for SSRF defense.
- Outbound redirects disabled.
- Sanitized error responses and logging protections for sensitive request data.

## Architecture

```text
Webhook sender
      |
      v
+-----------------------+
| HookScope API          |
| Spring Boot            |
+-----------------------+
      | persist event, destination, delivery, attempts
      v
+-----------------------+
| PostgreSQL             |
| source of truth        |
+-----------------------+
      |
      +--> synchronous attempt #1 --> destination HTTP endpoint
                    |
                    | failure only
                    v
       +---------------------------------------------+
       | Redis Streams                               |
       | hookscope:delivery-retries                   |
       | group: hookscope-retry-workers               |
       +---------------------------------------------+
                    |
                    v
           retry worker performs attempts #2 and #3
                    |
                    +--> exhausted --> hookscope:delivery-dlq
```

1. HookScope durably stores an incoming event in PostgreSQL.
2. It performs the initial destination delivery synchronously.
3. A failed initial delivery records its attempt and queues lightweight retry metadata in Redis.
4. The retry worker reloads the delivery, event, and destination from PostgreSQL.
5. Delivery has at most three total attempts; an exhausted third failure is recorded and sent to
   the DLQ.

## Retry lifecycle

| Attempt | Executor | Success | Failure |
|---|---|---|---|
| 1 | Synchronous request path | Delivery becomes `SUCCEEDED` | Record failure; queue attempt 2 |
| 2 | Redis retry worker | Delivery becomes `SUCCEEDED` | Record failure; queue attempt 3 |
| 3 | Redis retry worker | Delivery becomes `SUCCEEDED` | Delivery becomes `FAILED`; publish DLQ entry |

- Retry stream: `hookscope:delivery-retries`
- Consumer group: `hookscope-retry-workers`
- Dead-letter stream: `hookscope:delivery-dlq`
- Maximum total attempts: `3`

Redis messages contain lightweight delivery metadata only. Request bodies, headers, secrets, and
raw exception text remain out of the streams.

## Tech stack

| Technology | Role |
|---|---|
| Java 21 | Runtime and toolchain |
| Spring Boot | HTTP API, configuration, scheduling, and persistence integration |
| PostgreSQL | Durable source of truth |
| Redis Streams | Failed-delivery retry and DLQ transport |
| Flyway | Database migrations |
| Gradle | Build and verification |
| Docker / Docker Compose | Local self-hosted stack |
| Testcontainers | PostgreSQL and Redis integration infrastructure |
| GitHub Actions | CI verification with `./gradlew clean check bootJar` |

## Running locally

Prerequisites:

- Docker Desktop or Docker Engine with Docker Compose V2
- Java 21 only when running Gradle outside Docker

```bash
git clone https://github.com/ShayaanQ/HookScope.git
cd HookScope
cp .env.example .env
```

Set a locally generated administrator token of at least 32 characters in `.env`; do not commit
that file. Then start the complete stack:

```bash
docker compose up --build -d
docker compose ps
curl --fail http://localhost:8080/actuator/health
```

The verified health response is:

```json
{"status":"UP"}
```

To stop the local stack while retaining local database data:

```bash
docker compose down
```

## Quick demo

Set the management token from your local `.env` file:

```bash
set -a
. ./.env
set +a
export ADMIN_TOKEN="$HOOKSCOPE_ADMIN_TOKEN"
```

### 1. Create a webhook endpoint

```bash
curl --fail -X POST http://localhost:8080/api/v1/endpoints \
  -H "X-HookScope-Admin-Token: $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  --data '{"name":"Payments"}'
```

Retain the returned `id`, `publicKey`, and `ingestionPath` as `$ENDPOINT_ID`, `$PUBLIC_KEY`, and
`$INGESTION_PATH`.

### 2. Create a destination

Destinations must use an `http` or `https` URL whose host is present in the application's
`hookscope.delivery.allowed-hosts` configuration. With an allowlisted receiver host, create one:

```bash
curl --fail -X POST "http://localhost:8080/api/v1/endpoints/$ENDPOINT_ID/destinations" \
  -H "X-HookScope-Admin-Token: $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  --data '{"url":"https://receiver.example.com/webhooks"}'
```

Retain the returned destination `id` as `$DESTINATION_ID`.

### 3. Send a public webhook

```bash
curl --fail -X POST "http://localhost:8080$INGESTION_PATH?source=demo" \
  -H 'Content-Type: application/json' \
  --data '{"payment":"received"}'
```

The public ingestion route supports `GET`, `POST`, `PUT`, `PATCH`, and `DELETE`; accepted
requests return `204 No Content` after event persistence and the synchronous initial delivery.

### 4. List and inspect events

```bash
curl --fail -H "X-HookScope-Admin-Token: $ADMIN_TOKEN" \
  "http://localhost:8080/api/v1/endpoints/$ENDPOINT_ID/events?page=0&size=20"

curl --fail -H "X-HookScope-Admin-Token: $ADMIN_TOKEN" \
  "http://localhost:8080/api/v1/endpoints/$ENDPOINT_ID/events/$EVENT_ID"
```

### 5. Filter events and inspect delivery status

```bash
curl --fail -H "X-HookScope-Admin-Token: $ADMIN_TOKEN" \
  "http://localhost:8080/api/v1/endpoints/$ENDPOINT_ID/events?method=POST&receivedAfter=2026-01-01T00:00:00Z&receivedBefore=2027-01-01T00:00:00Z"

curl --fail -H "X-HookScope-Admin-Token: $ADMIN_TOKEN" \
  "http://localhost:8080/api/v1/events/$EVENT_ID/deliveries"
```

### 6. Replay a stored event

```bash
curl --fail -X POST "http://localhost:8080/api/v1/events/$EVENT_ID/replay" \
  -H "X-HookScope-Admin-Token: $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  --data "{\"destinationId\":\"$DESTINATION_ID\"}"
```

## Replay

`POST /api/v1/events/{eventId}/replay` accepts:

```json
{
  "destinationId": "<uuid>"
}
```

Replay uses the stored event's method, exact body bytes, and content type with the selected
configured destination. It creates a new `REPLAY` delivery and records a new attempt; it is not a
bulk or scheduled replay facility.

## Filtering

`GET /api/v1/endpoints/{endpointId}/events` supports optional `method`, `receivedAfter`, and
`receivedBefore` filters. Time bounds are exclusive. Results are paginated with zero-based pages,
a default size of 20, and a maximum size of 100.

```bash
curl --fail -H "X-HookScope-Admin-Token: $ADMIN_TOKEN" \
  "http://localhost:8080/api/v1/endpoints/$ENDPOINT_ID/events?method=POST&receivedAfter=2026-01-01T00:00:00Z"
```

## Testing

```bash
./gradlew test
./gradlew integrationTest
./gradlew clean check bootJar
```

The current verified suite contains 16 unit tests and 64 integration tests. Testcontainers-backed
coverage includes ingestion, management APIs, delivery persistence, replay, filtering, failure
isolation, retry success, retry exhaustion, and DLQ behavior.

## Local load test

Run the standard-library load script against a created public ingestion path:

```bash
python3 scripts/load_ingestion.py \
  --url "http://localhost:8080/hooks/$PUBLIC_KEY" \
  --requests 200 --concurrency 20
```

Verified local development-machine measurement, not a production benchmark:

| Metric | Result |
|---|---:|
| Requests | 200 |
| Concurrency | 20 |
| Successful / failed | 200 / 0 |
| Elapsed | 0.520 seconds |
| Throughput | 384.51 requests/sec |
| p50 latency | 32.18 ms |
| p95 latency | 187.49 ms |

The script also accepts `--body` to change the request body.

## Project structure

```text
src/main/java/io/hookscope/
  config/       # configuration and management-token protection
  endpoint/     # endpoint and destination management
  event/        # ingestion, inspection, and filtering
  delivery/     # outbound delivery, replay, retries, and DLQ handling
src/main/resources/db/migration/  # Flyway migrations
src/integrationTest/               # Testcontainers integration tests
scripts/                           # local load tooling
docs/                              # architecture and portfolio scope
```

## Design decisions

1. **PostgreSQL is the source of truth.** Events, destinations, deliveries, and attempts are
   persisted before Redis retry metadata is used, keeping durable state in one relational store.
2. **The first delivery is synchronous.** It keeps the initial request path observable and simple;
   Redis is reserved for failures rather than becoming a general delivery queue.
3. **Redis Streams handles only retries.** Stream entries identify a persisted delivery and next
   attempt number, while workers reload all event data from PostgreSQL.
4. **Retries are bounded.** Three total attempts prevent an infinite failure loop and make the
   retry policy easy to reason about.
5. **The DLQ marks exhausted delivery work.** A final failed attempt is preserved in PostgreSQL
   and emits concise metadata to the dead-letter stream for later inspection.
6. **Destinations are deliberately constrained.** URL validation, host allowlisting, and disabled
   redirects reduce SSRF exposure from outbound delivery.

## Scope and limitations

HookScope is intentionally a focused self-hosted backend platform. It has no user-account or
multi-tenant system, frontend dashboard, cloud/SaaS deployment model, worker crash-recovery or
`XAUTOCLAIM` flow, advanced distributed idempotency layer, or configurable retry policy. The
fixed retry limit and local Docker Compose deployment keep the project bounded and demonstrable.

## License

No license has been selected for this repository.
