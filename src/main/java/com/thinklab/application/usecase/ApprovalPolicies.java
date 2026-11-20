package com.thinklab.application.usecase;

import com.thinklab.domain.exception.ApprovalPolicyNotConfiguredException;
import com.thinklab.domain.model.ApprovalSettings;
import com.thinklab.domain.model.ChangeRequest.ChangeType;
import com.thinklab.domain.repository.ApprovalSettingsRepository;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Which CAB or ECAB ApprovalPolicy decides a change (ADR-039). With {@code thinklab.change-management.approval-policy-per-tenant} off, the
 * environment pair of ADR-031 is shared by every tenant, as before. On, only the settings of the change's own tenant count; a tenant
 * without them is refused, never given another tenant's policy.
 */
@Singleton
public class ApprovalPolicies {

    private final ApprovalSettingsRepository settingsRepository;
    private final boolean perTenant;
    private final UUID sharedCabPolicyId;
    private final UUID sharedEcabPolicyId;

    public ApprovalPolicies(
            ApprovalSettingsRepository settingsRepository,
            @Value("${thinklab.change-management.approval-policy-per-tenant:false}") boolean perTenant,
            @Value("${thinklab.change-management.cab-policy-id}") String cabPolicyId,
            @Value("${thinklab.change-management.ecab-policy-id}") String ecabPolicyId
    ) {
        this.settingsRepository = settingsRepository;
        this.perTenant = perTenant;
        this.sharedCabPolicyId = parse(cabPolicyId);
        this.sharedEcabPolicyId = parse(ecabPolicyId);
    }

    private static UUID parse(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    /** The policy that decides a change of this type: the ECAB's for EMERGENCY, the CAB's otherwise. Errors when none is configured. */
    public Mono<UUID> decidingPolicy(UUID organisationId, ChangeType changeType) {
        boolean emergency = changeType == ChangeType.EMERGENCY;
        if (perTenant) {
            return settingsRepository.findByOrganisationId(organisationId)
                    .switchIfEmpty(Mono.error(new ApprovalPolicyNotConfiguredException(emergency ? "ECAB" : "CAB")))
                    .map(emergency ? ApprovalSettings::ecabPolicyId : ApprovalSettings::cabPolicyId);
        }
        UUID shared = emergency ? sharedEcabPolicyId : sharedCabPolicyId;
        return shared == null
                ? Mono.error(new IllegalStateException("Dependency Failure: no ApprovalPolicy configured for change type " + changeType))
                : Mono.just(shared);
    }

    /** The ECAB policy, empty when none is configured: whoever asks (a freeze override) answers no, it never fails. */
    public Mono<UUID> ecabPolicy(UUID organisationId) {
        if (perTenant) {
            return settingsRepository.findByOrganisationId(organisationId).map(ApprovalSettings::ecabPolicyId);
        }
        return sharedEcabPolicyId == null ? Mono.empty() : Mono.just(sharedEcabPolicyId);
    }
}
