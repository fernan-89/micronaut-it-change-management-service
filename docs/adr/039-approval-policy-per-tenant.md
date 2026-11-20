# ADR 039: The CAB and ECAB policies belong to the tenant

## Status
Accepted (2026-11-20). Implemented the same day. Closes ACTION-PLAN 0.8-d of the platform, the one thing that still stops `THINKLAB_APPROVAL_POLICY_TENANT_SCOPED` of workflow-approval (ADR-037) from being on by default.

## Context
Routing a change for approval needs an ApprovalPolicy of workflow-approval: the CAB's for NORMAL changes, the ECAB's for EMERGENCY ones, and the ECAB's approvers also decide who may waive a change freeze (ADR-036). The ids of that pair are two environment variables read at boot (`THINKLAB_CAB_POLICY_ID`, `THINKLAB_ECAB_POLICY_ID`, ADR-031), so **every tenant shares one CAB and one ECAB**: the approvers of one organisation sit on the committee of all the others. workflow-approval cannot be scoped to the tenant while this holds, because a policy read there must be by its own tenant.

## Decision
1. **One approval-settings record per tenant, in this service.** It holds the id of the tenant's CAB policy and of its ECAB policy, with who changed it last and when (a full audit trail was judged too heavy for two ids). workflow-approval stays the owner of the policies; this service only stores which two are the tenant's.
2. **Routes** (BIAN qualifiers, tenant in `X-Tenant-Id`): `PUT /it-change-management/v1/approval-settings/update` (needs `X-Executor`; sets the two ids) and `GET /it-change-management/v1/approval-settings/retrieve`. Setting an id first asks workflow-approval whether that policy exists in the tenant (`policyExists(organisationId, policyId)`, as procurement does for approval bands); an unknown one is a 400 and nothing is stored. Only ADMIN or SERVICE may call `update` (the same elevated set that may waive a freeze), or anyone when security is off; otherwise 403.
3. **Routing uses the change's tenant.** `route-for-approval` reads the settings of the change's organisation and picks the CAB or ECAB id from them; the freeze override reads the ECAB approvers from the same record. A tenant without settings is **refused**: 422 with `ERR-CHG-00422` and a message saying the CAB/ECAB policy must be configured. It never falls back to another tenant's policy. STANDARD changes are pre-approved and need no policy, so they keep working.
4. **Transition switch, off by default**: `thinklab.change-management.approval-policy-per-tenant` (`THINKLAB_CHANGE_APPROVAL_POLICY_PER_TENANT`). Off, the environment pair keeps working exactly as today. On, only the tenant's settings count and the environment pair is ignored.
5. **Order of the switch-over:** (a) ship this with the switch off; (b) provision settings for each tenant and for the e2e/smoke tenants (the provisioning script writes them through the new route); (c) turn this switch on by default **together with** `THINKLAB_APPROVAL_POLICY_TENANT_SCOPED` of workflow-approval; (d) remove the environment pair, the two-phase restart of the e2e job and the shared-policy provisioning.

## Consequences
- Positive: each organisation's CAB and ECAB are its own people; workflow-approval can enforce the tenant on policy reads; no tenant can borrow another's approvers.
- Negative: a new tenant cannot route a NORMAL or EMERGENCY change until someone configures its two policies (by design, and the error says so); onboarding gains a step; one more aggregate and two routes to keep.
- Out of scope: how the web app lets an administrator pick the policies (a screen is a later item), and per-change-type policies beyond CAB/ECAB.
