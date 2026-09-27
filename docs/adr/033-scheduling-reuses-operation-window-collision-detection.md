# ADR-033: Scheduling Reuses operation-window-service's Collision Detection Wholesale

## Status
Accepted

## Context
Reserving a change's implementation window must not silently collide with another team's reservation
on the same assets, and must respect a change-freeze blackout period. operation-window-service already
implements exactly this: its own `initiate` behavior qualifier collides ANY two windows that overlap in
time AND share at least one target asset - including a `CHANGE_FREEZE`-typed window, since its
collision check has no special case that exempts that type. Re-implementing any part of that logic here
would duplicate a rule this platform already has one owner for.

## Decision
- `ScheduleChangeRequestUseCase` calls `POST /it-operation-window/v1/initiate` directly through
  `OperationWindowServicePort`, reserving a `DEPLOYMENT`-typed window covering the ChangeRequest's own
  `targetAssetIds` and the caller-supplied `plannedStart`/`plannedEnd`. This service never queries for
  existing windows itself and never computes an overlap.
- A 409 response from that call (its own `ERR-COL-00409`) is translated by
  `OperationWindowServiceAdapter` into this service's `SchedulingConflictException` (`ERR-CHG-00409`) -
  the same code every other ChangeRequest conflict uses (ADR-019). "Blocked by an active CHANGE_FREEZE"
  and "blocked by another team's reservation" are, from this service's perspective, the identical
  outcome: the window collided, try a different time or asset set.
- **Accepted v1 gap, documented honestly rather than built around:** operation-window-service has no
  override/bypass mechanism for an `EMERGENCY` change to jump a `CHANGE_FREEZE`. An emergency change
  that needs to proceed during a freeze has no automated path in v1; the freeze window must be adjusted
  or lifted by whoever owns it, out of band. Building a bypass would mean operation-window-service
  granting a privilege this service cannot itself authorize safely - deferred until a real incident
  demonstrates the need and shape of that override.

## Consequences
- Positive: one collision rule for the whole platform; this service inherits every future improvement
  to it (e.g. a smarter overlap check) with zero code change here.
- Positive: `CHANGE_FREEZE` blocking a non-emergency change is enforced for free - no new logic needed,
  no CHANGE_FREEZE-aware branch to test.
- Negative: an `EMERGENCY` change genuinely blocked by a `CHANGE_FREEZE` cannot be scheduled through
  this API at all in v1 - the only recourse is a human adjusting the freeze window directly.
