# HookScope M1 architecture

## Deployed system and data flow

```mermaid
flowchart LR
  sender[External webhook sender]
  operator[Single temporary operator]
  healthCaller[Health caller]

  subgraph compose[Docker Compose]
    subgraph app[Spring Boot HookScope application]
      health[/Public actuator health endpoint/]
      ingest[/Public /hooks/{publicKey} ingestion/]
      reader[Bounded body reader]
      processing[Query and header processing]
      redaction[Sensitive-header redaction before persistence]
      peer[Direct servlet/socket peer-IP handling]
      resolve[Resolve webhook endpoint]
      management[/Protected /api/v1/** management APIs/]
      token[Temporary admin-token verification]
      flyway[Flyway startup migration V1 then V2]
    end
    subgraph postgres[PostgreSQL 17.10]
      endpoints[(webhook_endpoints)]
      events[(webhook_events)]
    end
  end

  sender --> ingest
  ingest --> reader --> processing --> redaction --> peer --> resolve
  resolve --> endpoints
  resolve --> events
  operator -->|temporary admin token| token --> management
  management --> endpoints
  management --> events
  healthCaller --> health
  flyway --> endpoints
  flyway --> events
```

`/hooks/{publicKey}` is a public ingestion boundary. It accepts only the five locked M1 methods,
reads at most the configured limit plus one sentinel byte, parses the URL query, lowercases header
names, redacts configured exact sensitive names before storage, and stores the direct servlet peer
address. `Forwarded`, `X-Forwarded-For`, and `X-Real-IP` never replace that address.

`/api/v1/**` is the temporary single-operator management boundary. It requires the configured
admin-token header using a constant-time comparison. It manages endpoints and reads events.
`/actuator/health` remains public for deployment health checks. Flyway applies V1
(`webhook_endpoints`) and V2 (`webhook_events`) before Hibernate validates the mapped schema.

M1 deliberately has no Redis, workers, delivery/retry/replay/DLQ pipeline, user authentication,
UI, CLI, live event stream, trusted-proxy configuration, or provider-specific handshake/signature
logic.

## ADR implementation conformance

| ADR | Decision | Implementation | Verification evidence | Current limitation | Consistent? |
|---|---|---|---|---|---|
| ADR-001 | Generate 192-bit URL-safe public keys and retry a named uniqueness collision at most three times. | `SecureRandomPublicKeyGenerator`, `EndpointService`, `EndpointPersistence`, V1 migration. | `SecureRandomPublicKeyGeneratorTest`, `EndpointServiceTest`, `EndpointManagementIntegrationTest`, release catalog assertions. | The key is an opaque path capability, not a provider signature. | Yes |
| ADR-002 | Protect management APIs with a temporary environment token and constant-time comparison. | `HookScopeProperties`, `AdminTokenFilter`, `AdminTokenVerifier`, `ProblemDetails`. | `AdminTokenVerifierTest`, `HookScopePropertiesTest`, endpoint/context-path integration tests, Compose rehearsal. | This is temporary single-operator protection, not final user authentication or authorization. | Yes |
| ADR-003 | Redact exact sensitive header names before persistence; parse only raw query parameters. | `EventService`, `HookScopeProperties`, `MalformedWebhookRequestException`. | Ingestion and additional-redaction integration tests; malformed-query smoke check. | Raw bodies remain stored for inspection; query NUL is rejected to keep JSON persistence safe. | Yes |
| ADR-004 | Bound body reading and store accepted raw bytes exactly. | `BoundedBodyReader`, `IngestionController`, `WebhookEvent`, V2 migration. | Exact-limit, oversize, and chunked-body integration tests; Compose oversize rehearsal. | Default limit is 1 MiB; no streaming or external object storage exists in M1. | Yes |
| ADR-005 | Store the direct peer IP and ignore client forwarding headers. | `EventService`, `WebhookEvent`, V2 migration. | Source-IP integration test and forged-header Compose rehearsal. | M1 has no trusted-proxy support. | Yes |
