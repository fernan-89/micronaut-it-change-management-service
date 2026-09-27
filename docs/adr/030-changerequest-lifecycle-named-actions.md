# ADR-030: ChangeRequest Lifecycle — Named Actions, Not a Generic control/{status} Endpoint

## Status
Accepted

## Context
A change request (ITIL "GMUD") has more states and branches than the platform's simpler FSMs: intake,
assessment, a routing fork by change type, CAB/ECAB review, implementation (with a rollback path) and
close. Several transitions also carry data of their own - assessment records risk/impact, routing and
scheduling call out to other Service Domains, completion/rollback/close record their own notes.

The platform's simpler FSMs (`Asset`, `Site`) expose one generic `control/{action}` endpoint backed by
a `changeStatus` method and a `canTransitionTo` adjacency matrix, because every one of their
transitions is a bare status change. `it-hardware-maintenance`'s `WorkOrder` (ADR-030 of that service)
established the named-actions alternative for exactly this shape of problem; this service reuses it.

## Decision
- `ChangeRequest` has thirteen states (`DRAFT, SUBMITTED, ASSESSED, CAB_REVIEW, ECAB_REVIEW, APPROVED,
  REJECTED, SCHEDULED, IN_PROGRESS, IMPLEMENTED, CLOSED, ROLLED_BACK, CANCELLED`) and named domain
  methods (`submit, assess, routeForApproval, preApprove, approve, reject, schedule, start, complete,
  rollback, close, cancel`), each validating its own legal source status (or, for `cancel`, several)
  via the same small `requireStatus(ChangeStatus...)` helper `WorkOrder` uses.
- Routes carrying their own payload or talking to another Service Domain get their own named path
  (`assess`, `route-for-approval`, `approval/capture`, `schedule`, `complete`, `rollback`); the four
  bare status-only transitions (`submit`, `start`, `approve`/`reject` handled inside `approval/capture`,
  `cancel`) go through `ControlChangeRequestUseCase.Action`, mirroring `ControlWorkOrderUseCase`.
- `approve`/`reject` are still plain domain methods on `ChangeRequest` (no extra data), but they are
  never reached from their own controller route - only `CaptureApprovalDecisionUseCase` calls them,
  after reading the resolved outcome back from workflow-approval-service (ADR-032).

## Consequences
- Positive: every route's request/response shape matches exactly what that specific transition needs;
  the routing fork (STANDARD vs. NORMAL/EMERGENCY) and the two outbound calls (approval, scheduling)
  each have one obvious owning use case.
- Negative: more use case classes than the generic-dispatch services - a reasonable size for the
  aggregate's real complexity (13 use cases, comparable to `WorkOrder`'s own count), not accidental
  duplication.
