package com.thinklab.application.usecase;

import com.thinklab.domain.exception.ApprovalPolicyNotConfiguredException;
import com.thinklab.domain.model.ApprovalSettings;
import com.thinklab.domain.model.ChangeRequest.ChangeType;
import com.thinklab.domain.repository.ApprovalSettingsRepository;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Which CAB or ECAB ApprovalPolicy decides a change (ADR-039). Only the settings of the change's own tenant count; a tenant without them is refused,
 * never given another tenant's policy.
 */
@Singleton
public class ApprovalPolicies {

    private final ApprovalSettingsRepository settingsRepository;

    public ApprovalPolicies(ApprovalSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    /** The policy that decides a change of this type: the ECAB's for EMERGENCY, the CAB's otherwise. Errors when the tenant has none configured. */
    public Mono<UUID> decidingPolicy(UUID organisationId, ChangeType changeType) {
        boolean emergency = changeType == ChangeType.EMERGENCY;
        return settingsRepository.findByOrganisationId(organisationId)
                .switchIfEmpty(Mono.error(new ApprovalPolicyNotConfiguredException(emergency ? "ECAB" : "CAB")))
                .map(emergency ? ApprovalSettings::ecabPolicyId : ApprovalSettings::cabPolicyId);
    }

    /** The tenant's ECAB policy, empty when it has none configured: whoever asks (a freeze override) answers no, it never fails. */
    public Mono<UUID> ecabPolicy(UUID organisationId) {
        return settingsRepository.findByOrganisationId(organisationId).map(ApprovalSettings::ecabPolicyId);
    }
}
