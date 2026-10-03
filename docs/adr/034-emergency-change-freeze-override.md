# ADR-034: An ECAB-Approved EMERGENCY Change May Be Scheduled Over a CHANGE_FREEZE

## Status
Accepted (supersedes the "accepted v1 gap" in ADR-033)

## Context
ADR-033 reused operation-window-service's collision detection wholesale, which means a `CHANGE_FREEZE`
window blocks every change - including an EMERGENCY one that the ECAB has already approved. It deferred
a bypass until it was clear who could authorize it safely. The authorization already exists in this
service: an EMERGENCY change only reaches `APPROVED` through the ECAB quorum (ADR-031).

## Decision
- `PUT /{id}/schedule` accepts an optional `freezeOverrideJustification` (max 500 characters).
- It is legal only for an `EMERGENCY` change in `APPROVED` with a non-blank justification
  (`ChangeRequest.validateFreezeOverride`); anything else is a 400 (non-EMERGENCY, blank) or a 409
  (not `APPROVED`), **checked before the window is reserved remotely** so a refused override never leaks a
  window on operation-window-service.
- When present, the justification is forwarded to operation-window-service's `initiate`
  (`changeFreezeOverrideJustification`, its ADR-020), which then skips `CHANGE_FREEZE` windows - and only
  those - in its collision check. The window is still a `DEPLOYMENT` window and still collides with every
  other reservation.
- The override is recorded here too: the `SCHEDULED` audit entry's detail says the window was reserved
  over a CHANGE_FREEZE and why. Without a justification nothing changes: a freeze still answers
  `ERR-CHG-00409`.

## Consequences
- Positive: an approved emergency can proceed during a freeze, with the approval, the reason and the
  exception on the record in both services.
- Positive: no new ECAB logic - the authorization is the existing state machine.
- Negative: operation-window-service trusts this caller; a different caller could claim an override. A
  dedicated role for it is the next step (operation-window ADR-020).
