# ADR-002: Protect management routes with a temporary environment token

## Status

Accepted for M1-B.

## Context

M1 needs one operator to manage endpoints before user accounts, sessions, teams, and full
authorization exist. `/actuator/health` must remain callable by infrastructure without a token.

## Decision

`HOOKSCOPE_ADMIN_TOKEN` binds to `hookscope.admin-token`. Startup rejects missing, blank, or
shorter-than-32-character values with a sanitized message; there is no production default.
`AdminTokenFilter` applies only to `/api/v1/**` and requires `X-HookScope-Admin-Token`.
`AdminTokenVerifier` converts values to UTF-8 bytes and uses `MessageDigest.isEqual`, avoiding
ordinary string equality in the authorization decision. Missing or invalid values receive a
sanitized `401 UNAUTHORIZED` Problem Detail. `/actuator/health` remains public.

This is explicitly temporary single-operator protection, not final authentication or
authorization. The token is not logged, returned, put in exceptions, or committed as a
production value. Tests provide an isolated non-production value.

## Consequences

The deployment must set a sufficiently long secret before the app starts. Future identity work
will replace this mechanism rather than expand it into a user-management system.
