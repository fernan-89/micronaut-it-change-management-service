package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.UpdateApprovalSettingsRequest;
import com.thinklab.domain.exception.ApprovalPolicyNotConfiguredException;
import com.thinklab.domain.exception.ApprovalSettingsNotPermittedException;
import com.thinklab.domain.model.ApprovalSettings;
import com.thinklab.domain.model.ChangeRequest.ChangeType;
import com.thinklab.domain.port.ApprovalServicePort;
import com.thinklab.domain.repository.ApprovalSettingsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ADR-039: the CAB and ECAB policy of a tenant, how they are set and read, and how routing picks them. */
@ExtendWith(MockitoExtension.class)
class ApprovalSettingsUseCasesTest {

    private static final String SHARED_CAB = "11111111-1111-1111-1111-111111111111";
    private static final String SHARED_ECAB = "22222222-2222-2222-2222-222222222222";

    @Mock private ApprovalSettingsRepository settings;
    @Mock private ApprovalServicePort approvals;

    private final UUID tenant = UUID.randomUUID();
    private final UUID cab = UUID.randomUUID();
    private final UUID ecab = UUID.randomUUID();

    private ApprovalSettings stored() {
        return new ApprovalSettings(tenant, cab, ecab, Instant.now(), "admin-1");
    }

    @Test
    @DisplayName("update stores the two policies once both exist in the tenant, and answers what was stored")
    void update() {
        UpdateApprovalSettingsUseCase useCase = new UpdateApprovalSettingsUseCase(settings, approvals);
        when(approvals.policyExists(tenant, cab)).thenReturn(Mono.just(true));
        when(approvals.policyExists(tenant, ecab)).thenReturn(Mono.just(true));
        when(settings.save(any(ApprovalSettings.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));

        StepVerifier.create(useCase.execute(tenant, new UpdateApprovalSettingsRequest(cab, ecab), "admin-1", "ADMIN"))
                .assertNext(r -> {
                    assertEquals(tenant, r.organisationId());
                    assertEquals(cab, r.cabPolicyId());
                    assertEquals(ecab, r.ecabPolicyId());
                    assertEquals("admin-1", r.updatedBy());
                })
                .verifyComplete();
        StepVerifier.create(useCase.execute(tenant, new UpdateApprovalSettingsRequest(cab, ecab), "svc", "SERVICE")).expectNextCount(1).verifyComplete();
        StepVerifier.create(useCase.execute(tenant, new UpdateApprovalSettingsRequest(cab, ecab), "dev", null)).expectNextCount(1).verifyComplete();
    }

    @Test
    @DisplayName("update is refused below ADMIN, and for a policy that does not exist in the tenant, never storing")
    void updateRefusals() {
        UpdateApprovalSettingsUseCase useCase = new UpdateApprovalSettingsUseCase(settings, approvals);
        StepVerifier.create(useCase.execute(tenant, new UpdateApprovalSettingsRequest(cab, ecab), "op", "OPERATOR")).expectError(ApprovalSettingsNotPermittedException.class).verify();

        when(approvals.policyExists(tenant, cab)).thenReturn(Mono.just(false));
        StepVerifier.create(useCase.execute(tenant, new UpdateApprovalSettingsRequest(cab, ecab), "admin", "ADMIN"))
                .expectErrorSatisfies(e -> assertEquals(IllegalArgumentException.class, e.getClass())).verify();

        when(approvals.policyExists(tenant, cab)).thenReturn(Mono.just(true));
        when(approvals.policyExists(tenant, ecab)).thenReturn(Mono.just(false));
        StepVerifier.create(useCase.execute(tenant, new UpdateApprovalSettingsRequest(cab, ecab), "admin", "ADMIN"))
                .expectErrorSatisfies(e -> assertEquals(IllegalArgumentException.class, e.getClass())).verify();
        verify(settings, never()).save(any());
    }

    @Test
    @DisplayName("retrieve answers the tenant's settings, or the not-configured 422 when it has none")
    void retrieve() {
        RetrieveApprovalSettingsUseCase useCase = new RetrieveApprovalSettingsUseCase(settings);
        when(settings.findByOrganisationId(tenant)).thenReturn(Mono.just(stored()));
        StepVerifier.create(useCase.execute(tenant)).assertNext(r -> assertEquals(cab, r.cabPolicyId())).verifyComplete();

        UUID other = UUID.randomUUID();
        when(settings.findByOrganisationId(other)).thenReturn(Mono.empty());
        StepVerifier.create(useCase.execute(other)).expectError(ApprovalPolicyNotConfiguredException.class).verify();
    }

    @Test
    @DisplayName("per-tenant mode: the CAB or ECAB of the change's own tenant decides it; a tenant without settings is refused and never borrows another's")
    void perTenantPolicies() {
        ApprovalPolicies policies = new ApprovalPolicies(settings, true, SHARED_CAB, SHARED_ECAB);
        when(settings.findByOrganisationId(tenant)).thenReturn(Mono.just(stored()));
        UUID other = UUID.randomUUID();
        when(settings.findByOrganisationId(other)).thenReturn(Mono.empty());

        StepVerifier.create(policies.decidingPolicy(tenant, ChangeType.NORMAL)).expectNext(cab).verifyComplete();
        StepVerifier.create(policies.decidingPolicy(tenant, ChangeType.EMERGENCY)).expectNext(ecab).verifyComplete();
        StepVerifier.create(policies.ecabPolicy(tenant)).expectNext(ecab).verifyComplete();

        StepVerifier.create(policies.decidingPolicy(other, ChangeType.NORMAL))
                .expectErrorSatisfies(e -> assertEquals("This organisation has no CAB approval policy configured: set it with PUT /it-change-management/v1/approval-settings/update before routing a change for approval.", e.getMessage())).verify();
        StepVerifier.create(policies.decidingPolicy(other, ChangeType.EMERGENCY)).expectError(ApprovalPolicyNotConfiguredException.class).verify();
        StepVerifier.create(policies.ecabPolicy(other)).verifyComplete();
    }

    @Test
    @DisplayName("shared mode (the switch off): the environment pair of ADR-031 decides for every tenant, and a missing one is a dependency failure, as before")
    void sharedPolicies() {
        ApprovalPolicies policies = new ApprovalPolicies(settings, false, SHARED_CAB, SHARED_ECAB);
        StepVerifier.create(policies.decidingPolicy(tenant, ChangeType.NORMAL)).expectNext(UUID.fromString(SHARED_CAB)).verifyComplete();
        StepVerifier.create(policies.decidingPolicy(tenant, ChangeType.EMERGENCY)).expectNext(UUID.fromString(SHARED_ECAB)).verifyComplete();
        StepVerifier.create(policies.ecabPolicy(tenant)).expectNext(UUID.fromString(SHARED_ECAB)).verifyComplete();

        ApprovalPolicies none = new ApprovalPolicies(settings, false, "", null);
        StepVerifier.create(none.decidingPolicy(tenant, ChangeType.NORMAL)).expectError(IllegalStateException.class).verify();
        StepVerifier.create(none.ecabPolicy(tenant)).verifyComplete();
        verify(settings, never()).findByOrganisationId(any());
    }
}
