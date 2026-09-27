package com.thinklab.domain.port;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound Port for the workflow-approval Service Domain (ADR-032: synchronous HTTP integration, not
 * events). This service is the sole client of an {@code ApprovalRequest} filed against this
 * ChangeRequest (subjectType {@code "ChangeRequest"}) - the adapter owns that constant, this port
 * stays domain-pure.
 */
public interface ApprovalServicePort {

    /**
     * BIAN Behavior Qualifier {@code initiate} on workflow-approval-service. Files a new
     * ApprovalRequest against the given policy (CAB or ECAB, resolved by the caller from the
     * ChangeRequest's {@code ChangeType}) and returns its sovereign id.
     */
    Mono<UUID> initiateApprovalRequest(UUID organisationId, UUID changeRequestId, UUID requesterId, UUID policyId, String executor);

    /**
     * BIAN Behavior Qualifier {@code decision/capture} on workflow-approval-service. Forwards one
     * approver's decision and returns the ApprovalRequest's post-decision {@link ApprovalOutcome},
     * read back in the same request/response cycle so the caller can react immediately.
     */
    Mono<ApprovalOutcome> captureDecision(UUID approvalRequestId, UUID approverId, DecisionOutcome outcome, String comment, String executor);

    enum DecisionOutcome { APPROVE, REJECT }

    /** Mirrors workflow-approval-service's own {@code ApprovalStatus}, kept as this service's own copy. */
    enum ApprovalOutcome { PENDING, APPROVED, REJECTED, CANCELLED }
}
