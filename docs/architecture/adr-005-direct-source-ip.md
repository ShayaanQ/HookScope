# ADR-005: Store only the direct connection source IP

## Status

Accepted for M1-C.

## Decision

Events persist `HttpServletRequest.getRemoteAddr()` in PostgreSQL `inet`. Client-supplied
forwarding headers are ignored because trusted-proxy configuration is outside M1-C.
