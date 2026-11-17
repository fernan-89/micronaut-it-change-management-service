package com.thinklab.domain.repository;

import com.thinklab.domain.model.ChangeRequest;
import com.thinklab.domain.model.ChangeRequest.ChangeRequestAuditEntry;
import com.thinklab.domain.model.ChangeRequest.ChangeStatus;
import com.thinklab.domain.model.ChangeRequest.ImpactLevel;
import com.thinklab.domain.model.ChangeRequest.RiskLevel;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbound Port for ChangeRequest persistence operations (IT Change Management Service Domain).
 *
 * <p>ARCHITECTURAL RULE: Partial State Mutations (ADR-002, the same rule every other repository port
 * on the platform follows). {@link #create(ChangeRequest)} is the only whole-document write; every
 * transition is a granular update that atomically appends its forensic {@link ChangeRequestAuditEntry}
 * to the ledger. There is no {@code deleteById} - {@code control/cancel} and the other terminal
 * transitions reach their status via {@link #updateStatus}/the other granular updates, never a
 * physical deletion.
 */
public interface ChangeRequestRepository {

    Mono<ChangeRequest> create(ChangeRequest changeRequest);

    Mono<ChangeRequest> findById(UUID id, UUID organisationId);

    Flux<ChangeRequest> findAllByOrganisationId(UUID organisationId, ChangeStatus status);

    /**
     * Every {@code update*} below is a <b>guarded write</b> (platform race audit): it applies only if the change still has the status the caller read,
     * which is the {@code fromStatus} of the audit entry, and a miss is {@link com.thinklab.domain.exception.InvalidChangeRequestStatusException} (409)
     * when the change exists and {@link com.thinklab.domain.exception.ChangeRequestNotFoundException} when it does not. Two requests acting on one
     * change at once cannot both win.
     */
    Mono<Void> updateBasicInfo(UUID id, String title, String description, ChangeRequestAuditEntry auditEntry);

    /** Covers every status-only transition: submit, pre-approve, start, approve, reject, cancel. */
    Mono<Void> updateStatus(UUID id, ChangeStatus status, ChangeRequestAuditEntry auditEntry);

    Mono<Void> updateAssessment(UUID id, RiskLevel riskLevel, ImpactLevel impactLevel, ChangeStatus status, ChangeRequestAuditEntry auditEntry);

    Mono<Void> updateRouting(UUID id, UUID approvalRequestId, ChangeStatus status, ChangeRequestAuditEntry auditEntry);

    Mono<Void> updateScheduling(UUID id, UUID operationWindowId, Instant plannedStart, Instant plannedEnd, ChangeStatus status, ChangeRequestAuditEntry auditEntry);

    Mono<Void> updateCompletion(UUID id, String implementationNotes, ChangeStatus status, ChangeRequestAuditEntry auditEntry);

    Mono<Void> updateRollback(UUID id, String rollbackReason, ChangeStatus status, ChangeRequestAuditEntry auditEntry);

    Mono<Void> updateClose(UUID id, String closeNotes, ChangeStatus status, ChangeRequestAuditEntry auditEntry);

    /**
     * Routing files an approval request and scheduling reserves a window in other services, so a second request must be turned away <b>before</b> it
     * does either. Takes a short exclusive lease on the change if it still has {@code expectedStatus} and nobody holds a live lease; a miss is 409 (or 404).
     * The lease is released by the guarded write that finishes the step ({@code updateRouting}, {@code updateScheduling}), by {@link #releaseRemoteStepLease},
     * or by expiring on its own.
     */
    Mono<Void> acquireRemoteStepLease(UUID id, ChangeStatus expectedStatus);

    /** Gives the lease back after a remote step failed, so the step can be tried again at once. Never fails. */
    Mono<Void> releaseRemoteStepLease(UUID id);
}
