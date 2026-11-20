package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.UpdateApprovalSettingsRequest;
import com.thinklab.application.dto.response.ApprovalSettingsResponse;
import com.thinklab.application.usecase.RetrieveApprovalSettingsUseCase;
import com.thinklab.application.usecase.UpdateApprovalSettingsUseCase;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Put;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound adapter for the CAB and ECAB policies of a tenant (ADR-039): {@code approval-settings/update} sets them, {@code approval-settings/retrieve}
 * reads them. The tenant is always the caller's own (X-Tenant-Id).
 */
@Controller("/it-change-management/v1/approval-settings")
public class ApprovalSettingsController {

    private static final Logger log = LoggerFactory.getLogger(ApprovalSettingsController.class);
    static final String TENANT_HEADER = "X-Tenant-Id";
    static final String EXECUTOR_HEADER = "X-Executor";
    static final String ROLE_HEADER = "X-Role";

    private final UpdateApprovalSettingsUseCase updateApprovalSettingsUseCase;
    private final RetrieveApprovalSettingsUseCase retrieveApprovalSettingsUseCase;

    public ApprovalSettingsController(UpdateApprovalSettingsUseCase updateApprovalSettingsUseCase, RetrieveApprovalSettingsUseCase retrieveApprovalSettingsUseCase) {
        this.updateApprovalSettingsUseCase = updateApprovalSettingsUseCase;
        this.retrieveApprovalSettingsUseCase = retrieveApprovalSettingsUseCase;
    }

    /** Behavior Qualifier: {@code update}. ADMIN or SERVICE only when security is on. */
    @Put("/update")
    public Mono<HttpResponse<ApprovalSettingsResponse>> update(
            @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid UpdateApprovalSettingsRequest request
    ) {
        log.info("[ACTION: UPDATE_APPROVAL_SETTINGS] [EXECUTOR: {}] organisation: {}", executor, tenantId);
        return Mono.defer(() -> updateApprovalSettingsUseCase.execute(UUID.fromString(tenantId), request, executor, role)).map(HttpResponse::ok);
    }

    /** Behavior Qualifier: {@code retrieve}. */
    @Get("/retrieve")
    public Mono<HttpResponse<ApprovalSettingsResponse>> retrieve(@Header(TENANT_HEADER) @NotBlank String tenantId) {
        return Mono.defer(() -> retrieveApprovalSettingsUseCase.execute(UUID.fromString(tenantId))).map(HttpResponse::ok);
    }
}
