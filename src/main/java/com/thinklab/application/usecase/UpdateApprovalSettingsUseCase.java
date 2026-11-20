package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.UpdateApprovalSettingsRequest;
import com.thinklab.application.dto.response.ApprovalSettingsResponse;
import com.thinklab.domain.exception.ApprovalSettingsNotPermittedException;
import com.thinklab.domain.model.ApprovalSettings;
import com.thinklab.domain.model.FreezeOverridePolicy;
import com.thinklab.domain.port.ApprovalServicePort;
import com.thinklab.domain.repository.ApprovalSettingsRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Sets the CAB and ECAB policy of a tenant (ADR-039). Each id must be a policy that exists in that tenant at workflow-approval; an unknown
 * one is a 400 and nothing is stored. Only ADMIN or SERVICE may do it (the same elevated set that may waive a freeze), or anyone when
 * security is off.
 */
@Singleton
public class UpdateApprovalSettingsUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateApprovalSettingsUseCase.class);

    private final ApprovalSettingsRepository settingsRepository;
    private final ApprovalServicePort approvalServicePort;

    public UpdateApprovalSettingsUseCase(ApprovalSettingsRepository settingsRepository, ApprovalServicePort approvalServicePort) {
        this.settingsRepository = settingsRepository;
        this.approvalServicePort = approvalServicePort;
    }

    public Mono<ApprovalSettingsResponse> execute(UUID organisationId, UpdateApprovalSettingsRequest request, String executor, String role) {
        log.info("[USE CASE] Setting the approval policies of organisation {}", organisationId);
        if (!FreezeOverridePolicy.permits(role)) {
            return Mono.error(new ApprovalSettingsNotPermittedException(role));
        }
        return approvalServicePort.policyExists(organisationId, request.cabPolicyId())
                .flatMap(cabExists -> cabExists
                        ? approvalServicePort.policyExists(organisationId, request.ecabPolicyId())
                        : Mono.error(new IllegalArgumentException("The CAB policy " + request.cabPolicyId() + " does not exist in this organisation.")))
                .flatMap(ecabExists -> ecabExists
                        ? settingsRepository.save(new ApprovalSettings(organisationId, request.cabPolicyId(), request.ecabPolicyId(), Instant.now(), executor))
                        : Mono.error(new IllegalArgumentException("The ECAB policy " + request.ecabPolicyId() + " does not exist in this organisation.")))
                .map(UpdateApprovalSettingsUseCase::toResponse);
    }

    static ApprovalSettingsResponse toResponse(ApprovalSettings settings) {
        return new ApprovalSettingsResponse(settings.organisationId(), settings.cabPolicyId(), settings.ecabPolicyId(), settings.updatedAt(), settings.updatedBy());
    }
}
