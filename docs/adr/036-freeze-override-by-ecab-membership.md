# ADR-036: A Seat on the ECAB Is Enough to Waive a CHANGE_FREEZE

## Status
Accepted (refines ADR-035)

## Context
ADR-035 limited waiving a CHANGE_FREEZE to ADMIN or SERVICE, because the kit has no change-manager role and adding one means a kit release and a rollout to every service. That forces an ADMIN on people who only need this one power, and the people a tenant already trusts to approve an emergency - the members of its ECAB - could not use it.

## Decision
- With security on, a freeze override (`freezeOverrideJustification` on `schedule`) is permitted when the verified role is ADMIN or SERVICE (unchanged) **or when the executor is an approver of the tenant's ECAB policy**. The ECAB is the policy configured as `thinklab.change-management.ecab-policy-id`; "an approver" is anyone listed in any stage of it (`workflow-approval-service` `policy/retrieve`), so it keeps working when the ECAB becomes a chain (workflow-approval ADR-033).
- The change itself must still be an EMERGENCY and APPROVED (ADR-034); this only decides *who* may ask.
- **Fail-closed**: the check is a privilege. If the ECAB cannot be read (service down) or is not configured, the answer is no (403 `ERR-CHG-00403`); a caller with an elevated role is decided without asking anyone. The failure is logged, not shown to the caller.
- With security off there is no role and nothing is enforced, as before.
- operation-window-service keeps its own check (ADR-021 there): this service reaches it with its service identity, so the ECAB path does not widen who can call operation-window directly.

## Consequences
- Positive: no kit release, no new role, and the permission follows the approval policy the tenant already maintains; an ECAB member is not made an ADMIN.
- Negative: the permission is per tenant policy rather than a global role, and the executor is matched by user id (a header-only local sign-in that is not a user id never matches). A dedicated role can still replace this later without changing the API.
