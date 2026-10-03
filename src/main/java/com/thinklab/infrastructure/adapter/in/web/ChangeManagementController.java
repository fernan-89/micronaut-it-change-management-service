package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.AssessChangeRequestRequest;
import com.thinklab.application.dto.request.CaptureApprovalDecisionRequest;
import com.thinklab.application.dto.request.CloseChangeRequestRequest;
import com.thinklab.application.dto.request.CompleteChangeRequestRequest;
import com.thinklab.application.dto.request.InitiateChangeRequestRequest;
import com.thinklab.application.dto.request.RollbackChangeRequestRequest;
import com.thinklab.application.dto.request.ScheduleChangeRequestRequest;
import com.thinklab.application.dto.request.UpdateChangeRequestRequest;
import com.thinklab.application.dto.response.ChangeRequestAuditEntryResponse;
import com.thinklab.application.dto.response.ChangeRequestResponse;
import com.thinklab.application.usecase.AssessChangeRequestUseCase;
import com.thinklab.application.usecase.CaptureApprovalDecisionUseCase;
import com.thinklab.application.usecase.CloseChangeRequestUseCase;
import com.thinklab.application.usecase.CompleteChangeRequestUseCase;
import com.thinklab.application.usecase.ControlChangeRequestUseCase;
import com.thinklab.application.usecase.InitiateChangeRequestUseCase;
import com.thinklab.application.usecase.RetrieveChangeRequestAuditLogUseCase;
import com.thinklab.application.usecase.RetrieveChangeRequestUseCase;
import com.thinklab.application.usecase.RetrieveChangeRequestsUseCase;
import com.thinklab.application.usecase.RollbackChangeRequestUseCase;
import com.thinklab.application.usecase.RouteForApprovalUseCase;
import com.thinklab.application.usecase.ScheduleChangeRequestUseCase;
import com.thinklab.application.usecase.UpdateChangeRequestUseCase;
import com.thinklab.domain.model.ChangeRequest.ChangeStatus;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Inbound Web Adapter for the {@code it-change-management} Service Domain (ITIL "GMUD").
 *
 * <p><b>BIAN-Aligned Resource Model (ADR-013):</b> {@link com.thinklab.domain.model.ChangeRequest} is
 * the Control Record. Every route is a named Behavior Qualifier rather than a generic
 * {@code control/{status}} endpoint (ADR-030 of {@code it-hardware-maintenance}, reused here), since
 * several transitions carry their own data or talk to another Service Domain. There is no
 * {@code DELETE}: {@code control/cancel} is a terminal, soft status transition.
 *
 * <p><b>{@code approval/capture} (ADR-032):</b> {@code X-Executor} on that one route is the approver's
 * own sovereign id (forwarded verbatim to workflow-approval-service), not an operator identity - the
 * same convention workflow-approval-service's own controller uses.
 */
@Controller("/it-change-management/v1")
public class ChangeManagementController {

    private static final Logger log = LoggerFactory.getLogger(ChangeManagementController.class);
    static final String TENANT_HEADER = "X-Tenant-Id";
    static final String EXECUTOR_HEADER = "X-Executor";
    static final String ROLE_HEADER = "X-Role";

    private final InitiateChangeRequestUseCase initiateChangeRequestUseCase;
    private final RetrieveChangeRequestUseCase retrieveChangeRequestUseCase;
    private final RetrieveChangeRequestsUseCase retrieveChangeRequestsUseCase;
    private final UpdateChangeRequestUseCase updateChangeRequestUseCase;
    private final ControlChangeRequestUseCase controlChangeRequestUseCase;
    private final AssessChangeRequestUseCase assessChangeRequestUseCase;
    private final RouteForApprovalUseCase routeForApprovalUseCase;
    private final CaptureApprovalDecisionUseCase captureApprovalDecisionUseCase;
    private final ScheduleChangeRequestUseCase scheduleChangeRequestUseCase;
    private final CompleteChangeRequestUseCase completeChangeRequestUseCase;
    private final RollbackChangeRequestUseCase rollbackChangeRequestUseCase;
    private final CloseChangeRequestUseCase closeChangeRequestUseCase;
    private final RetrieveChangeRequestAuditLogUseCase retrieveChangeRequestAuditLogUseCase;

    public ChangeManagementController(
            InitiateChangeRequestUseCase initiateChangeRequestUseCase,
            RetrieveChangeRequestUseCase retrieveChangeRequestUseCase,
            RetrieveChangeRequestsUseCase retrieveChangeRequestsUseCase,
            UpdateChangeRequestUseCase updateChangeRequestUseCase,
            ControlChangeRequestUseCase controlChangeRequestUseCase,
            AssessChangeRequestUseCase assessChangeRequestUseCase,
            RouteForApprovalUseCase routeForApprovalUseCase,
            CaptureApprovalDecisionUseCase captureApprovalDecisionUseCase,
            ScheduleChangeRequestUseCase scheduleChangeRequestUseCase,
            CompleteChangeRequestUseCase completeChangeRequestUseCase,
            RollbackChangeRequestUseCase rollbackChangeRequestUseCase,
            CloseChangeRequestUseCase closeChangeRequestUseCase,
            RetrieveChangeRequestAuditLogUseCase retrieveChangeRequestAuditLogUseCase
    ) {
        this.initiateChangeRequestUseCase = initiateChangeRequestUseCase;
        this.retrieveChangeRequestUseCase = retrieveChangeRequestUseCase;
        this.retrieveChangeRequestsUseCase = retrieveChangeRequestsUseCase;
        this.updateChangeRequestUseCase = updateChangeRequestUseCase;
        this.controlChangeRequestUseCase = controlChangeRequestUseCase;
        this.assessChangeRequestUseCase = assessChangeRequestUseCase;
        this.routeForApprovalUseCase = routeForApprovalUseCase;
        this.captureApprovalDecisionUseCase = captureApprovalDecisionUseCase;
        this.scheduleChangeRequestUseCase = scheduleChangeRequestUseCase;
        this.completeChangeRequestUseCase = completeChangeRequestUseCase;
        this.rollbackChangeRequestUseCase = rollbackChangeRequestUseCase;
        this.closeChangeRequestUseCase = closeChangeRequestUseCase;
        this.retrieveChangeRequestAuditLogUseCase = retrieveChangeRequestAuditLogUseCase;
    }

    /** Behavior Qualifier: {@code initiate}. */
    @Post("/initiate")
    public Mono<HttpResponse<ChangeRequestResponse>> initiate(
            @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Body @Valid InitiateChangeRequestRequest request
    ) {
        log.info("[ACTION: INITIATE_CHANGE_REQUEST] [EXECUTOR: {}] Received request '{}' for organisation: {}", executor, request.title(), tenantId);

        return initiateChangeRequestUseCase.execute(UUID.fromString(tenantId), request, executor).map(HttpResponse::created);
    }

    /** Behavior Qualifier: {@code retrieve}. */
    @Get("/{id}/retrieve")
    public Mono<HttpResponse<ChangeRequestResponse>> retrieveById(@PathVariable UUID id) {
        return retrieveChangeRequestUseCase.execute(id).map(HttpResponse::ok);
    }

    /** Behavior Qualifier: {@code retrieve} (collection). Filterable by {@code status}. */
    @Get("/retrieve")
    public Mono<List<ChangeRequestResponse>> retrieveAll(
            @Header(TENANT_HEADER) @NotBlank String tenantId, @QueryValue @Nullable ChangeStatus status
    ) {
        return Mono.defer(() -> retrieveChangeRequestsUseCase.execute(UUID.fromString(tenantId), status).collectList());
    }

    /** Behavior Qualifier: {@code update}. */
    @Put("/{id}/update")
    public Mono<HttpResponse<Void>> update(
            @PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor, @Body @Valid UpdateChangeRequestRequest request
    ) {
        return updateChangeRequestUseCase.execute(id, request, executor).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/submit}. DRAFT -&gt; SUBMITTED. */
    @Put("/{id}/control/submit")
    public Mono<HttpResponse<Void>> controlSubmit(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor) {
        return controlChangeRequestUseCase.execute(id, ControlChangeRequestUseCase.Action.SUBMIT, executor).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code assess}. SUBMITTED -&gt; ASSESSED. */
    @Put("/{id}/assess")
    public Mono<HttpResponse<Void>> assess(
            @PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor, @Body @Valid AssessChangeRequestRequest request
    ) {
        return assessChangeRequestUseCase.execute(id, request, executor).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code route-for-approval}. ASSESSED -&gt; APPROVED (STANDARD) or CAB_REVIEW/ECAB_REVIEW. */
    @Put("/{id}/route-for-approval")
    public Mono<HttpResponse<Void>> routeForApproval(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor) {
        return routeForApprovalUseCase.execute(id, executor).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code approval/capture}. Forwards one approver's decision synchronously. */
    @Put("/{id}/approval/capture")
    public Mono<HttpResponse<ChangeRequestResponse>> captureApprovalDecision(
            @PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor, @Body @Valid CaptureApprovalDecisionRequest request
    ) {
        log.info("[ACTION: CAPTURE_APPROVAL_DECISION] [APPROVER: {}] decision for ChangeRequest ID: {}", executor, id);

        return captureApprovalDecisionUseCase.execute(id, UUID.fromString(executor), request, executor).map(HttpResponse::ok);
    }

    /** Behavior Qualifier: {@code schedule}. APPROVED -&gt; SCHEDULED. */
    @Put("/{id}/schedule")
    public Mono<HttpResponse<Void>> schedule(
            @PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role,
            @Body @Valid ScheduleChangeRequestRequest request
    ) {
        return scheduleChangeRequestUseCase.execute(id, request, executor, role).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/start}. SCHEDULED -&gt; IN_PROGRESS. */
    @Put("/{id}/control/start")
    public Mono<HttpResponse<Void>> controlStart(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor) {
        return controlChangeRequestUseCase.execute(id, ControlChangeRequestUseCase.Action.START, executor).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code complete}. IN_PROGRESS -&gt; IMPLEMENTED. */
    @Put("/{id}/complete")
    public Mono<HttpResponse<Void>> complete(
            @PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor, @Body @Valid CompleteChangeRequestRequest request
    ) {
        return completeChangeRequestUseCase.execute(id, request, executor).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code rollback}. IN_PROGRESS -&gt; ROLLED_BACK (terminal). */
    @Put("/{id}/rollback")
    public Mono<HttpResponse<Void>> rollback(
            @PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor, @Body @Valid RollbackChangeRequestRequest request
    ) {
        return rollbackChangeRequestUseCase.execute(id, request, executor).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/close}. IMPLEMENTED -&gt; CLOSED (terminal). */
    @Put("/{id}/control/close")
    public Mono<HttpResponse<Void>> controlClose(
            @PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor, @Body @Valid CloseChangeRequestRequest request
    ) {
        return closeChangeRequestUseCase.execute(id, request, executor).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/cancel}. Terminal, replaces DELETE. */
    @Put("/{id}/control/cancel")
    public Mono<HttpResponse<Void>> controlCancel(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor) {
        return controlChangeRequestUseCase.execute(id, ControlChangeRequestUseCase.Action.CANCEL, executor).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code audit-log/retrieve}. Immutable forensic ledger of the ChangeRequest. */
    @Get("/{id}/audit-log/retrieve")
    public Mono<List<ChangeRequestAuditEntryResponse>> retrieveAuditLog(@PathVariable UUID id) {
        return retrieveChangeRequestAuditLogUseCase.execute(id);
    }
}
