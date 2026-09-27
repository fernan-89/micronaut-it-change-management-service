# Thinklab IT Change Management Service

**Version:** v1.0.0-BIAN

**Status:** Reference implementation (ThinkLab portfolio project)

## Overview

The Thinklab IT Change Management Service is the platform's ITIL-aligned change management Service
Domain (BIAN `it-change-management`, "GMUD"): from a drafted change through risk/impact assessment,
approval routing, implementation-window scheduling, execution and close - or rejection, rollback and
cancellation along the way. It implements the BIAN Control Record `ChangeRequest`, scoped to an
Organisation from the Party Reference Data Directory.

A `STANDARD` change is a pre-approved template and skips human review entirely; `NORMAL` and
`EMERGENCY` changes are routed to a CAB or ECAB quorum on workflow-approval-service and resolved
synchronously in the same request/response cycle (ADR-031/032). Scheduling a change's implementation
window reuses operation-window-service's existing collision detection wholesale, including its
`CHANGE_FREEZE` blackout enforcement, rather than duplicating that logic here (ADR-033).

Built with Java 21 and Micronaut 4.4.2 on a strict Hexagonal Architecture and a fully reactive stack
(Project Reactor, reactive MongoDB driver).

## Technology Stack

* **Runtime:** Java 21 LTS
* **Framework:** Micronaut 4.4.2 (AOT optimized, reflection-free DI and Serde)
* **Reactive Engine:** Project Reactor (Mono / Flux)
* **Persistence:** Reactive MongoDB (`thinklab_it_change_management_db`, collection `change_requests`), BSON UUID standard representation, compound `(organisationId, status)` index
* **Cross-service integration:** synchronous declarative HTTP clients to workflow-approval-service (approval routing/decision) and operation-window-service (implementation-window scheduling) - no events (ADR-032)
* **Observability:** W3C Trace Context, SLF4J/Logback, Reactor MDC bridge
* **Containerization:** Google Distroless (nonroot), read-only root filesystem
* **Testing:** JUnit 5, Mockito, Reactor Test (FSM, use cases, controller, adapters, mapper, index initializer)
* **Documentation:** OpenAPI 3.0 / Swagger generated at compile time

## Domain Model

```text
ChangeRequest {
  id, organisationId, requesterId, title, description,
  changeType, targetAssetIds[], riskLevel?, impactLevel?, status,
  approvalRequestId?, operationWindowId?, plannedStart?, plannedEnd?,
  implementationNotes?, rollbackReason?, closeNotes?, createdAt, updatedAt,
  auditTrail[ { occurredAt, action, executor, fromStatus?, toStatus, detail } ]
}
changeType: STANDARD | NORMAL | EMERGENCY
riskLevel, impactLevel: LOW | MEDIUM | HIGH
```

### Lifecycle (ADR-030/031)

```text
DRAFT -> SUBMITTED -> ASSESSED -+-> APPROVED (STANDARD: pre-approved)          -> SCHEDULED
                                 |-> CAB_REVIEW   (NORMAL)    -> APPROVED ->---+  -> IN_PROGRESS -+-> IMPLEMENTED -> CLOSED (terminal)
                                 |                              |-> REJECTED (terminal)             |-> ROLLED_BACK (terminal)
                                 |-> ECAB_REVIEW  (EMERGENCY) -> APPROVED ->---+
                                                                 |-> REJECTED (terminal)
DRAFT, SUBMITTED, ASSESSED, CAB_REVIEW, ECAB_REVIEW, APPROVED, SCHEDULED -> CANCELLED (terminal)
```

Each transition is its own named route (`control/submit`, `assess`, `route-for-approval`,
`approval/capture`, `schedule`, `control/start`, `complete`, `rollback`, `control/close`,
`control/cancel`) rather than a generic `control/{status}` endpoint, since several carry their own
payload or talk to another Service Domain (ADR-030).

## BIAN Behavior Qualifier Contract (`/it-change-management/v1`)

`X-Tenant-Id` is mandatory on `initiate` and the collection `retrieve`; `X-Executor` is mandatory on
every mutation. On `approval/capture`, `X-Executor` is the approver's own sovereign id, forwarded
verbatim to workflow-approval-service - not an operator identity (ADR-032). There is no `DELETE`.

| Behavior Qualifier | Method & Path |
|---|---|
| initiate | `POST /it-change-management/v1/initiate` |
| retrieve (single) | `GET /it-change-management/v1/{id}/retrieve` |
| retrieve (collection, filter `status`) | `GET /it-change-management/v1/retrieve` |
| update | `PUT /it-change-management/v1/{id}/update` |
| control/submit | `PUT /it-change-management/v1/{id}/control/submit` |
| assess | `PUT /it-change-management/v1/{id}/assess` |
| route-for-approval | `PUT /it-change-management/v1/{id}/route-for-approval` |
| approval/capture | `PUT /it-change-management/v1/{id}/approval/capture` |
| schedule | `PUT /it-change-management/v1/{id}/schedule` |
| control/start | `PUT /it-change-management/v1/{id}/control/start` |
| complete | `PUT /it-change-management/v1/{id}/complete` |
| rollback | `PUT /it-change-management/v1/{id}/rollback` |
| control/close | `PUT /it-change-management/v1/{id}/control/close` |
| control/cancel | `PUT /it-change-management/v1/{id}/control/cancel` |
| audit-log/retrieve | `GET /it-change-management/v1/{id}/audit-log/retrieve` |

### Error catalog (RFC 7807, `error_code` field)

| error_code | HTTP | Meaning |
|---|---|---|
| `ERR-CHG-00404` | 404 | ChangeRequest not found |
| `ERR-CHG-00409` | 409 | Illegal lifecycle transition, missing approval-in-progress, or a scheduling collision surfaced by operation-window-service (ADR-019/ADR-033) |
| `ERR-VALIDATION-00400` | 400 | Payload/header/identifier validation failure |
| `ERR-INTERNAL-00500` | 500 | Unexpected technical failure |

Example:

```bash
curl -X POST http://localhost:8086/it-change-management/v1/initiate \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 6f1c7a52-3d0b-4a44-9c3e-0a7d1f6e2b10" \
  -H "X-Executor: admin-user-01" \
  -d '{"requesterId":"...","title":"Upgrade firmware","description":"...","changeType":"NORMAL","targetAssetIds":["..."]}'
```

## Operational Procedures

```bash
# Build, run AOT optimizations and test
./gradlew clean build

# Start the service (default port 8086)
./gradlew run

# Container image
docker build -t thinklab-it-change-management-service:latest .
```

* **Health:** `http://localhost:8086/health`
* **Swagger UI:** `http://localhost:8086/swagger-ui`
* **Postman suite:** `docs/postman/` (STANDARD/NORMAL/EMERGENCY lifecycles + rollback/cancel + negative/409 scenarios)

### Configuration

| Variable | Default | Purpose |
|---|---|---|
| `MICRONAUT_SERVER_PORT` | `8086` | HTTP port |
| `MONGODB_URI` | `mongodb://localhost:27017/thinklab_it_change_management_db` | MongoDB connection |
| `HASH_SERVICE_URL` | `http://localhost:8080` | Hash Token Registry base URL |
| `WORKFLOW_APPROVAL_SERVICE_URL` | `http://localhost:8090` | workflow-approval-service base URL |
| `OPERATION_WINDOW_SERVICE_URL` | `http://localhost:8084` | operation-window-service base URL |
| `THINKLAB_CAB_POLICY_ID` | *(none)* | ApprovalPolicy id on workflow-approval-service for `NORMAL` changes |
| `THINKLAB_ECAB_POLICY_ID` | *(none)* | ApprovalPolicy id on workflow-approval-service for `EMERGENCY` changes |

## Architecture Decision Records

`docs/adr/`: 001 hexagonal reactive stack · 005 UUID identity sovereignty · 013 BIAN service domain
conventions · 019 HTTP 409 for state conflicts · 030 lifecycle (named actions) · 031 STANDARD changes
pre-approved · 032 synchronous integration with approval/scheduling · 033 scheduling reuses
operation-window's collision detection.

### Automated Tests

```bash
./gradlew test                          # unit suite + 100% line/branch coverage gate (no Docker needed)
./gradlew integrationTest               # Testcontainers suite against a real MongoDB (needs Docker)
./gradlew check                         # both, as CI runs it
```

## License

Licensed under the [PolyForm Strict License 1.0.0](LICENSE): you may read and use this software for noncommercial purposes only. Modifying it, creating derivative works, redistributing it and any commercial use are not permitted without a separate written license. This software is not open source.
