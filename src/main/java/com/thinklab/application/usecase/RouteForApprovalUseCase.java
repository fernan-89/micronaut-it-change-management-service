package com.thinklab.application.usecase;

import com.thinklab.domain.exception.ChangeRequestNotFoundException;
import com.thinklab.domain.model.ChangeRequest.ChangeType;
import com.thinklab.domain.port.ApprovalServicePort;
import com.thinklab.domain.repository.ChangeRequestRepository;
import io.micronaut.context.annotation.Value;
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
    private final UUID cabPolicyId;
    private final UUID ecabPolicyId;

    public RouteForApprovalUseCase(
            ChangeRequestRepository changeRequestRepository,
            ApprovalServicePort approvalServicePort,
            @Value("${thinklab.change-management.cab-policy-id}") String cabPolicyId,
            @Value("${thinklab.change-management.ecab-policy-id}") String ecabPolicyId
    ) {
        this.changeRequestRepository = changeRequestRepository;
        this.approvalServicePort = approvalServicePort;
        this.cabPolicyId = parsePolicyId(cabPolicyId);
        this.ecabPolicyId = parsePolicyId(ecabPolicyId);
    }

    private static UUID parsePolicyId(String value) {
        return (value == null || value.isBlank()) ? null : UUID.fromString(value);
    }

    public Mono<Void> execute(UUID id, String executor) {
        log.info("[USE CASE] Routing ChangeRequest ID: {} for approval", id);

        return changeRequestRepository.findById(id)
                .switchIfEmpty(Mono.error(new ChangeRequestNotFoundException(id)))
                .flatMap(changeRequest -> {
                    if (changeRequest.getChangeType() == ChangeType.STANDARD) {
                        var entry = changeRequest.preApprove(executor);
                        return changeRequestRepository.updateStatus(id, changeRequest.getStatus(), entry);
                    }

                    UUID policyId = changeRequest.getChangeType() == ChangeType.EMERGENCY ? ecabPolicyId : cabPolicyId;
                    if (policyId == null) {
                        return Mono.error(new IllegalStateException(
                                "Dependency Failure: no ApprovalPolicy configured for change type " + changeRequest.getChangeType()));
                    }

                    return approvalServicePort.initiateApprovalRequest(
                                    changeRequest.getOrganisationId(), id, changeRequest.getRequesterId(), policyId, executor)
                            .flatMap(approvalRequestId -> {
                                var entry = changeRequest.routeForApproval(approvalRequestId, executor);
                                return changeRequestRepository.updateRouting(id, approvalRequestId, changeRequest.getStatus(), entry);
                            });
                });
    }
}
