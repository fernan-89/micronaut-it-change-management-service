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

    Mono<ChangeRequest> findById(UUID id);

    Flux<ChangeRequest> findAllByOrganisationId(UUID organisationId, ChangeStatus status);

    Mono<Void> updateBasicInfo(UUID id, String title, String description, ChangeRequestAuditEntry auditEntry);

    /** Covers every status-only transition: submit, pre-approve, start, approve, reject, cancel. */
    Mono<Void> updateStatus(UUID id, ChangeStatus status, ChangeRequestAuditEntry auditEntry);

    Mono<Void> updateAssessment(UUID id, RiskLevel riskLevel, ImpactLevel impactLevel, ChangeStatus status, ChangeRequestAuditEntry auditEntry);

    Mono<Void> updateRouting(UUID id, UUID approvalRequestId, ChangeStatus status, ChangeRequestAuditEntry auditEntry);

    Mono<Void> updateScheduling(UUID id, UUID operationWindowId, Instant plannedStart, Instant plannedEnd, ChangeStatus status, ChangeRequestAuditEntry auditEntry);

    Mono<Void> updateCompletion(UUID id, String implementationNotes, ChangeStatus status, ChangeRequestAuditEntry auditEntry);

    Mono<Void> updateRollback(UUID id, String rollbackReason, ChangeStatus status, ChangeRequestAuditEntry auditEntry);

    Mono<Void> updateClose(UUID id, String closeNotes, ChangeStatus status, ChangeRequestAuditEntry auditEntry);
}
