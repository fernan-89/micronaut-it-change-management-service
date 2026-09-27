# ADR-031: STANDARD Changes Are Pre-Approved; NORMAL/EMERGENCY Route to CAB/ECAB

## Status
Accepted

## Context
ITIL distinguishes standard changes (pre-authorized, low-risk, repeatable - e.g. a routine firmware
bump against a known-good template) from normal and emergency changes, which genuinely need a human
approval board before proceeding. Routing every change through workflow-approval-service regardless of
type would force even the most routine, pre-authorized work through a CAB queue for no safety benefit,
and would need a policy configured for a category of change that, by definition, doesn't need one.

## Decision
- `ChangeRequest.ChangeType` is fixed at creation (`STANDARD`, `NORMAL`, `EMERGENCY`) and never changes.
- `route-for-approval` branches on it: a `STANDARD` change calls the domain's own `preApprove` (ASSESSED
  -&gt; APPROVED directly, no outbound call, ADR-030); `NORMAL`/`EMERGENCY` call `routeForApproval`,
  which files a new `ApprovalRequest` on workflow-approval-service against the CAB or ECAB policy
  (`thinklab.change-management.cab-policy-id`/`ecab-policy-id`, operator-provisioned configuration, not
  discovered at runtime) and transitions to `CAB_REVIEW`/`ECAB_REVIEW` respectively.
- Both policies are resolved by `RouteForApprovalUseCase` from configuration, not looked up by name -
  a missing configuration value surfaces as a dependency-failure error rather than silently picking a
  wrong policy.

## Consequences
- Positive: the common, low-risk case (a `STANDARD` change) never touches another Service Domain and
  never blocks on human availability.
- Positive: `NORMAL` and `EMERGENCY` reuse the exact same quorum/veto machinery
  (workflow-approval-service, ADR-030/031 of that service) with no duplicated approval logic here.
- Negative: this service has no way to promote a `STANDARD` change into a reviewed one after the fact,
  or demote a `NORMAL`/`EMERGENCY` one - the type is a creation-time decision. Filing a new request with
  the intended type is the only correction path in v1.
