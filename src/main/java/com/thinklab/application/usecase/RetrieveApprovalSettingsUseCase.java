package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.ApprovalSettingsResponse;
import com.thinklab.domain.exception.ApprovalPolicyNotConfiguredException;
import com.thinklab.domain.repository.ApprovalSettingsRepository;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Reads the CAB and ECAB policy of the tenant (ADR-039); a tenant without them gets the same 422 as routing a change would. */
@Singleton
public class RetrieveApprovalSettingsUseCase {

    private final ApprovalSettingsRepository settingsRepository;

    public RetrieveApprovalSettingsUseCase(ApprovalSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    public Mono<ApprovalSettingsResponse> execute(UUID organisationId) {
        return settingsRepository.findByOrganisationId(organisationId)
                .switchIfEmpty(Mono.error(new ApprovalPolicyNotConfiguredException("CAB and ECAB")))
                .map(UpdateApprovalSettingsUseCase::toResponse);
    }
}
