# ADR-001: Generate endpoint public keys with 192-bit `SecureRandom` values

## Status

Accepted for M1-B.

## Context

Each endpoint needs an opaque public path segment that cannot be inferred from an ID, name,
time, counter, or request input. PostgreSQL remains the final authority for uniqueness when
concurrent creates happen.

## Decision

`SecureRandomPublicKeyGenerator` obtains exactly 24 random bytes from Java `SecureRandom` and
encodes them with URL-safe Base64 without padding. The result is a 32-character value matching
`[A-Za-z0-9_-]{32}`, carrying 192 bits of entropy. `webhook_endpoints.public_key` has the named
database unique constraint `webhook_endpoints_public_key_key`. `EndpointService` attempts
insertion at most three times. Each insertion is in its own transaction; only a violation of that
exact constraint causes another generated key to be tried. Other integrity violations are
preserved and are never misclassified as collisions. After the third collision, creation returns
a sanitized server error and terminates.

The public response derives `ingestionPath` only as `/hooks/{publicKey}`. It does not consult
the request host or forwarding headers. Keys are never logged.

## Consequences

The collision path is extraordinarily unlikely but deterministic in tests through the generator
and persistence boundary. The bounded retry avoids an infinite loop while the unique constraint
maintains correctness under races.
