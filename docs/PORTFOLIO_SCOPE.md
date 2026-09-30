# HookScope portfolio scope

This file is the current source of truth for HookScope portfolio completion. Historical milestone
and acceptance documents remain preserved as records, but do not define current completion scope.

- Built and deployed a self-hosted webhook platform with durable event ingestion, request
  inspection and filtering, replay, and SSRF-resistant outbound delivery.
- Implemented Redis Streams-based delivery processing with bounded retries and dead-letter
  handling; validated ingestion and failure paths with Testcontainers-backed integration tests and
  local load testing.

In scope: durable ingestion; request inspection/filtering; replay; SSRF-resistant outbound
delivery; Redis Streams; bounded retries; DLQ; integration tests; local load test; Docker
self-hosting.

Out of scope: HMAC; React; worker recovery/XAUTOCLAIM; advanced idempotency; multi-tenancy;
OAuth/accounts; enterprise observability; exhaustive acceptance matrices; and old-roadmap
features not required by the two bullets above.
