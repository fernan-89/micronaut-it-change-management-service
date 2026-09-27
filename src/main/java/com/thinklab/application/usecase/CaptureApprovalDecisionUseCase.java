package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.CaptureApprovalDecisionRequest;
import com.thinklab.application.dto.response.ChangeRequestResponse;
import com.thinklab.application.mapper.ChangeRequestMapper;
import com.thinklab.domain.exception.ChangeRequestNotFoundException;
import com.thinklab.domain.exception.InvalidChangeRequestStatusException;
import com.thinklab.domain.model.ChangeRequest;
import com.thinklab.domain.port.ApprovalServicePort;
import com.thinklab.domain.port.ApprovalServicePort.ApprovalOutcome;
import com.thinklab.domain.repository.ChangeRequestRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Use Case for forwarding one approver's decision to workflow-approval-service (BIAN Behavior
 * Qualifier: {@code approval/capture}). Reads the resolved status back in the same response
 * (ADR-032): only an {@code APPROVED}/{@code REJECTED} outcome mutates this ChangeRequest - a still
 * {@code PENDING} quorum leaves it in CAB_REVIEW/ECAB_REVIEW untouched, since workflow-approval-service
 * already keeps that decision's own ledger.
 */
@Singleton
public class CaptureApprovalDecisionUseCase {

    private static final Logger log = LoggerFactory.getLogger(CaptureApprovalDecisionUseCase.class);

    private final ChangeRequestRepository changeRequestRepository;
    private final ApprovalServicePort approvalServicePort;

    public CaptureApprovalDecisionUseCase(ChangeRequestRepository changeRequestRepository, ApprovalServicePort approvalServicePort) {
        this.changeRequestRepository = changeRequestRepository;
        this.approvalServicePort = approvalServicePort;
    }

    public Mono<ChangeRequestResponse> execute(UUID id, UUID approverId, CaptureApprovalDecisionRequest request, String executor) {
        log.info("[USE CASE] Capturing approval decision [{}] from approver [{}] for ChangeRequest ID: {}", request.outcome(), approverId, id);

        return changeRequestRepository.findById(id)
                .switchIfEmpty(Mono.error(new ChangeRequestNotFoundException(id)))
                .flatMap(changeRequest -> {
                    if (changeRequest.getApprovalRequestId() == null) {
                        return Mono.error(new InvalidChangeRequestStatusException(
                                "Illegal transition: no approval is currently in progress for this ChangeRequest."));
                    }
                    return approvalServicePort.captureDecision(changeRequest.getApprovalRequestId(), approverId, request.outcome(), request.comment(), executor)
                            .flatMap(outcome -> applyOutcome(changeRequest, outcome, executor));
                });
    }

    private Mono<ChangeRequestResponse> applyOutcome(ChangeRequest changeRequest, ApprovalOutcome outcome, String executor) {
        UUID id = changeRequest.getId();
        if (outcome == ApprovalOutcome.APPROVED) {
            var entry = changeRequest.approve(executor);
            return changeRequestRepository.updateStatus(id, changeRequest.getStatus(), entry)
                    .thenReturn(ChangeRequestMapper.toResponse(changeRequest));
        }
        if (outcome == ApprovalOutcome.REJECTED) {
            var entry = changeRequest.reject(executor);
            return changeRequestRepository.updateStatus(id, changeRequest.getStatus(), entry)
                    .thenReturn(ChangeRequestMapper.toResponse(changeRequest));
        }
        return Mono.just(ChangeRequestMapper.toResponse(changeRequest));
    }
}
