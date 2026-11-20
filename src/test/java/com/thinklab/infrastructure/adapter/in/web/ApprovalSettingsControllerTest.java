package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.UpdateApprovalSettingsRequest;
import com.thinklab.application.dto.response.ApprovalSettingsResponse;
import com.thinklab.application.usecase.RetrieveApprovalSettingsUseCase;
import com.thinklab.application.usecase.UpdateApprovalSettingsUseCase;
import io.micronaut.http.HttpStatus;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalSettingsControllerTest {

    @Mock private UpdateApprovalSettingsUseCase updateUseCase;
    @Mock private RetrieveApprovalSettingsUseCase retrieveUseCase;

    @Test
    @DisplayName("update and retrieve pass the caller's own tenant, executor and role to the use cases")
    void routes() {
        ApprovalSettingsController controller = new ApprovalSettingsController(updateUseCase, retrieveUseCase);
        UUID tenant = UUID.randomUUID();
        UpdateApprovalSettingsRequest request = new UpdateApprovalSettingsRequest(UUID.randomUUID(), UUID.randomUUID());
        ApprovalSettingsResponse response = new ApprovalSettingsResponse(tenant, request.cabPolicyId(), request.ecabPolicyId(), Instant.now(), "admin-1");
        when(updateUseCase.execute(tenant, request, "admin-1", "ADMIN")).thenReturn(Mono.just(response));
        when(retrieveUseCase.execute(tenant)).thenReturn(Mono.just(response));

        StepVerifier.create(controller.update(tenant.toString(), "admin-1", "ADMIN", request))
                .assertNext(r -> assertEquals(HttpStatus.OK, r.getStatus())).verifyComplete();
        StepVerifier.create(controller.retrieve(tenant.toString()))
                .assertNext(r -> assertEquals(request.cabPolicyId(), r.body().cabPolicyId())).verifyComplete();
        StepVerifier.create(controller.retrieve("not-a-uuid")).expectError(IllegalArgumentException.class).verify();
    }
}
