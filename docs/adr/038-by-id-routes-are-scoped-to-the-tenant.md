# ADR-038: The By-Id Routes Are Scoped to the Tenant

## Status
Accepted (2026-11-17). Part of the tenant audit of the platform (thinklab-platform `docs/TENANT-AUDIT-2026-11.md`); same rule as workflow-approval ADR-037.

## Context
Every route of a change request that takes the id (`{id}/retrieve`, `update`, `control/*`, `assess`, `route-for-approval`, `approval/capture`, `schedule`, `complete`, `rollback`, `audit-log/retrieve`) found the change by id alone. The cross-tenant probe (platform `scripts/e2e/tenant-probe.sh`) showed a change made by tenant X answering 200 to tenant Y, retrieve and audit log alike: anyone who knew a UUID could read a change, and every write route had the same lookup.

## Decision
- Every by-id route takes `X-Tenant-Id` (required, 400 if blank) and finds the change only in that tenant: another tenant's change is the same as none (404 `ChangeRequestNotFoundException`), nothing is read or written. The repository has no unscoped `findById` any more.
- The writes that follow a scoped load keep using the id (the object was already proved to be the tenant's).
- Callers: the web and the gateway already send the tenant on every call; nothing else calls these routes. This service's own calls to other services are unchanged.

## Consequences
- Positive: a UUID is no longer enough to read or change another tenant's change request; the probe lists this service as fixed.
- Negative: a client that sends no tenant on a by-id route now gets a 400.
