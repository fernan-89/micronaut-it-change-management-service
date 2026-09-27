# ADR-032: Synchronous HTTP to workflow-approval-service and operation-window-service, Not Events

## Status
Accepted

## Context
This service has two hard cross-Service-Domain dependencies: it needs an approval outcome
(workflow-approval-service) and an implementation-window reservation (operation-window-service) before
it can proceed, and it needs to react to both in the same operator-facing request rather than polling
or waiting on an event. The platform's default integration pattern (kit ADR-003: outbox + NATS
JetStream) is built for one producer feeding an unknown number of eventually-consistent consumers, not
for a caller that needs the answer inline to decide its own next state.

## Decision
- `ApprovalServicePort`/`OperationWindowServicePort` are outbound ports implemented by declarative
  Micronaut HTTP clients (`WorkflowApprovalServiceAdapter`, `OperationWindowServiceAdapter`), the same
  shape as the existing `HashServicePort`/`HashServiceAdapter`.
- `RouteForApprovalUseCase` calls workflow-approval-service's `initiate` and stores the returned
  `ApprovalRequest` id; `CaptureApprovalDecisionUseCase` forwards each decision to `decision/capture`
  and reads the resolved status back in the same response to decide whether to call `approve`/`reject`
  on this aggregate (workflow-approval-service's own ADR-032 documents the same choice from its side).
- `ScheduleChangeRequestUseCase` calls operation-window-service's `initiate` directly, reusing its
  existing collision detection wholesale rather than re-implementing it (ADR-033).
- This service publishes and consumes no events; the kit's `OutboxRelay`/`NatsConnectionFactory` beans
  stay present but disabled (`thinklab.events.enabled=false`), the same as workflow-approval-service.

## Consequences
- Positive: both cross-service calls resolve inline; the caller (an operator driving a ChangeRequest
  through its lifecycle) never needs to poll or subscribe to know whether routing/scheduling succeeded.
- Positive: no event schema, no NATS subject, no idempotent-consumer bookkeeping for either integration.
- Negative: this service has a hard runtime dependency on both services' availability for
  `route-for-approval`/`approval/capture`/`schedule` to succeed - accepted for v1, consistent with the
  platform's other direct-call integrations.
- Negative: a transient failure on either downstream service surfaces as a 500-class dependency error
  with no automatic retry; the operator retries the same route-for-approval/schedule call by hand.
