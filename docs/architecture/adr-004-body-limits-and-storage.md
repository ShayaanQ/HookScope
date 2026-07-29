# ADR-004: Bound raw body capture and store exact accepted bytes

## Status

Accepted for M1-C.

## Decision

The ingestion reader reads at most the configured body limit plus one sentinel byte, independent
of `Content-Length`. Oversize requests are rejected before persistence. Accepted bytes are stored
in `bytea` with their byte count and SHA-256 digest; management detail returns standard Base64.
