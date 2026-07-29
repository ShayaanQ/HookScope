# ADR-003: Redact sensitive headers before persistence

## Status

Accepted for M1-C.

## Decision

Ingestion normalizes header names to lowercase and performs exact-name matching for the locked
sensitive-header set. Matching values are replaced with `[REDACTED]` before event persistence.
Repeated non-sensitive values remain ordered arrays. No raw sensitive values are retained or logged.
Additional names are supplied as a comma-separated configuration value, trimmed, normalized to
lowercase, and matched exactly. URL query parsing is separate from header handling and uses the raw
URL query only; it applies UTF-8 form-style decoding (`+` to space) without parsing form bodies.
Malformed percent escapes and decoded NUL characters are rejected as malformed requests before
persistence; NUL is deliberately excluded from query JSON to keep the persisted representation
safe and portable.
