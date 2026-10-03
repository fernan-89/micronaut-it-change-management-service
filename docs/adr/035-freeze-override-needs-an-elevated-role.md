# ADR-035: Waiving a CHANGE_FREEZE Needs an Elevated Role

## Status
Accepted (tightens ADR-034)

## Context
ADR-034 lets an ECAB-approved EMERGENCY change be scheduled over a CHANGE_FREEZE. The state machine already guarantees the change
was approved by the ECAB, but nothing said *who may press the button*: any caller allowed to `PUT .../schedule` could add a
`freezeOverrideJustification`. A freeze exists precisely to stop people; the people allowed to waive it should be few.

## Decision
- With security on, `PUT /{id}/schedule` with a `freezeOverrideJustification` requires the **verified role** (`X-Role`, set by the
  kit's security filter from the token and never trusted from the client) to be `ADMIN` or `SERVICE` (`FreezeOverridePolicy`).
  OPERATOR, REQUESTER and VIEWER get `403 ERR-CHG-00403` - checked first, before the change is loaded or anything is reserved - and
  can still schedule the same change *without* the override.
- With security off there is no role (`null`) and nothing is enforced, like everywhere else on the platform.
- The role check is in addition to ADR-034's rules (EMERGENCY, APPROVED, non-blank justification), not instead of them.

## Consequences
- Positive: waiving a freeze needs a deliberate, elevated identity, and the refusal is an auditable 403 on the ledger.
- Negative: **ADMIN is a blunt instrument.** An ADMIN can do everything for its tenant, so "may waive a freeze" cannot be granted
  without granting that. The right answer is a dedicated change-manager role; that needs a new value in the kit's `Role` enum, a kit
  release, issuing it from party-authentication and a rollout to every service. This ADR uses what exists today and names the gap.
- Negative: the justification is still free text; the role says who, not whether the reason was good.
