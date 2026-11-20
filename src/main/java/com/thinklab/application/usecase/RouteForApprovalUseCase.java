package com.thinklab.application.usecase;

import com.thinklab.domain.exception.ChangeRequestNotFoundException;
import com.thinklab.domain.model.ChangeRequest.ChangeType;
import com.thinklab.domain.port.ApprovalServicePort;
import com.thinklab.domain.repository.ChangeRequestRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Use Case for routing an assessed ChangeRequest towards approval (BIAN Behavior Qualifier:
 * {@code route-for-approval}). A {@code STANDARD} change is pre-approved with no outbound call
 * (ADR-031); {@code NORMAL}/{@code EMERGENCY} file a new {@code ApprovalRequest} on
 * workflow-approval-service against the configured CAB/ECAB policy (ADR-032) before transitioning.
 */
@Singleton
public class RouteForApprovalUseCase {

    private static final Logger log = LoggerFactory.getLogger(RouteForApprovalUseCase.class);

    private final ChangeRequestRepository changeRequestRepository;
    private final ApprovalServicePort approvalServicePort;
    private final ApprovalPolicies approvalPolicies;

    public RouteForApprovalUseCase(
            ChangeRequestRepository changeRequestRepository,
            ApprovalServicePort approvalServicePort,
            ApprovalPolicies approvalPolicies
    ) {
        this.changeRequestRepository = changeRequestRepository;
        this.approvalServicePort = approvalServicePort;
        this.approvalPolicies = approvalPolicies;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, String executor) {
        log.info("[USE CASE] Routing ChangeRequest ID: {} for approval", id);

        return changeRequestRepository.findById(id, organisationId)
                .switchIfEmpty(Mono.error(new ChangeRequestNotFoundException(id)))
                .flatMap(changeRequest -> {
                    if (changeRequest.getChangeType() == ChangeType.STANDARD) {
                        var entry = changeRequest.preApprove(executor);
                        return changeRequestRepository.updateStatus(id, changeRequest.getStatus(), entry);
                    }

                    // The tenant's own policy (ADR-039); a tenant without one is refused before anything is leased or filed.
                    return approvalPolicies.decidingPolicy(changeRequest.getOrganisationId(), changeRequest.getChangeType()).flatMap(policyId ->
                            // The lease first: only one of simultaneous requests may file an approval request; the others are refused (409) before filing any.
                            changeRequestRepository.acquireRemoteStepLease(id, changeRequest.getStatus())
                            .then(Mono.defer(() -> approvalServicePort.initiateApprovalRequest(
                                            changeRequest.getOrganisationId(), id, changeRequest.getRequesterId(), policyId, executor)
                                    .flatMap(approvalRequestId -> {
                                        var entry = changeRequest.routeForApproval(approvalRequestId, executor);
                                        return changeRequestRepository.updateRouting(id, approvalRequestId, changeRequest.getStatus(), entry);
                                    })
                                    .onErrorResume(failure -> changeRequestRepository.releaseRemoteStepLease(id).then(Mono.error(failure))))));
                });
    }
}
