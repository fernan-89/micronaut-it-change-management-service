# ADR 037: Guarded writes, and a short lease before a remote step

## Status
Accepted (2026-11-17). Found by the platform race audit (ACTION-PLAN 0.5, `thinklab-platform/docs/race-audit.md`).

## Context
Every transition read the change, validated it in the domain and wrote it by `_id` only. Two requests at once both passed the validation and both wrote.
Measured by `TransitionRaceIT` on the old code: a submit and a cancel at once both answered OK (the last write won and the trail held both);
ten routings at once filed **ten approval requests** in workflow-approval and ten schedulings at once reserved **ten windows** in operation-window, nine
of each orphaned (an orphan window blocks the calendar).

## Decision
- Every `update*` of the repository is a **guarded write**: it applies only if the change still has the status the caller read, which is the
  `fromStatus` of the audit entry it carries (so no use case changed). No match is `409 ERR-CHG-00409` when the change exists and `404` when it does not.
- Routing and scheduling act in another service, so they take a **short exclusive lease** on the change first (`acquireRemoteStepLease`, guarded on the
  status read and on no live lease): of simultaneous requests only one reaches the other service and the rest are refused (409) before creating anything.
  The lease is a field of the stored document (`remoteStepLeaseUntil`, two minutes), not part of the domain; the guarded write that finishes the step
  clears it, a failed remote call gives it back (`releaseRemoteStepLease`), and an abandoned one expires by itself.
- No new status was needed (unlike `it-discovery`, ADR 033 there): the lease does not change what the change *is*, only who may act on it right now.

## Consequences
- **Positive:** one approval request and one window per routing or scheduling, a trail that always agrees with the final status.
- **Negative / still open:** routing and scheduling still call the other service before the domain validates the transition (a request in an illegal
  status files an approval request or reserves a window and only then fails); a pre-check in the domain would close that and is a separate change.
  A process that dies between the remote call and the final write leaves the remote artefact behind until the lease expires and the step is retried.
