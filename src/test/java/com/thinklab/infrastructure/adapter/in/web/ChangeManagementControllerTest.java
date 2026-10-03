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
import com.thinklab.domain.model.ChangeRequest.ChangeType;
import com.thinklab.domain.port.ApprovalServicePort.DecisionOutcome;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeManagementControllerTest {

    @Mock private InitiateChangeRequestUseCase initiateChangeRequestUseCase;
    @Mock private RetrieveChangeRequestUseCase retrieveChangeRequestUseCase;
    @Mock private RetrieveChangeRequestsUseCase retrieveChangeRequestsUseCase;
    @Mock private UpdateChangeRequestUseCase updateChangeRequestUseCase;
    @Mock private ControlChangeRequestUseCase controlChangeRequestUseCase;
    @Mock private AssessChangeRequestUseCase assessChangeRequestUseCase;
    @Mock private RouteForApprovalUseCase routeForApprovalUseCase;
    @Mock private CaptureApprovalDecisionUseCase captureApprovalDecisionUseCase;
    @Mock private ScheduleChangeRequestUseCase scheduleChangeRequestUseCase;
    @Mock private CompleteChangeRequestUseCase completeChangeRequestUseCase;
    @Mock private RollbackChangeRequestUseCase rollbackChangeRequestUseCase;
    @Mock private CloseChangeRequestUseCase closeChangeRequestUseCase;
    @Mock private RetrieveChangeRequestAuditLogUseCase retrieveChangeRequestAuditLogUseCase;

    private ChangeManagementController controller;
    private UUID id;
    private static final String TENANT = UUID.randomUUID().toString();
    private static final String EXECUTOR = "op-1";

    @BeforeEach
    void setUp() {
        controller = new ChangeManagementController(initiateChangeRequestUseCase, retrieveChangeRequestUseCase, retrieveChangeRequestsUseCase,
                updateChangeRequestUseCase, controlChangeRequestUseCase, assessChangeRequestUseCase, routeForApprovalUseCase,
                captureApprovalDecisionUseCase, scheduleChangeRequestUseCase, completeChangeRequestUseCase, rollbackChangeRequestUseCase,
                closeChangeRequestUseCase, retrieveChangeRequestAuditLogUseCase);
        id = UUID.randomUUID();
    }

    private ChangeRequestResponse sampleResponse() {
        return new ChangeRequestResponse(id, UUID.randomUUID(), UUID.randomUUID(), "t", "d", "NORMAL",
                Set.of(UUID.randomUUID()), null, null, null, "DRAFT", null, null, null, null, null, null, null,
                Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("initiate returns 201 Created")
    void initiate() {
        InitiateChangeRequestRequest request = new InitiateChangeRequestRequest(UUID.randomUUID(), "t", "d", ChangeType.NORMAL, Set.of(UUID.randomUUID()), null, null);
        when(initiateChangeRequestUseCase.execute(any(), eq(request), eq(EXECUTOR))).thenReturn(Mono.just(sampleResponse()));

        var response = controller.initiate(TENANT, EXECUTOR, request).block();
        assertEquals(HttpStatus.CREATED, response.getStatus());
    }

    @Test
    @DisplayName("retrieveById returns 200 OK")
    void retrieveById() {
        when(retrieveChangeRequestUseCase.execute(id)).thenReturn(Mono.just(sampleResponse()));

        var response = controller.retrieveById(id).block();
        assertEquals(HttpStatus.OK, response.getStatus());
    }

    @Test
    @DisplayName("retrieveAll delegates with the optional status filter")
    void retrieveAll() {
        when(retrieveChangeRequestsUseCase.execute(any(), any())).thenReturn(Flux.just(sampleResponse()));

        var result = controller.retrieveAll(TENANT, null).block();
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("update returns 204 No Content")
    void update() {
        UpdateChangeRequestRequest request = new UpdateChangeRequestRequest("t", "d");
        when(updateChangeRequestUseCase.execute(id, request, EXECUTOR)).thenReturn(Mono.empty());

        assertEquals(HttpStatus.NO_CONTENT, controller.update(id, EXECUTOR, request).block().getStatus());
    }

    @Test
    @DisplayName("control/submit dispatches ControlChangeRequestUseCase.Action.SUBMIT")
    void controlSubmit() {
        when(controlChangeRequestUseCase.execute(id, ControlChangeRequestUseCase.Action.SUBMIT, EXECUTOR)).thenReturn(Mono.empty());

        assertEquals(HttpStatus.NO_CONTENT, controller.controlSubmit(id, EXECUTOR).block().getStatus());
    }

    @Test
    @DisplayName("assess returns 204 No Content")
    void assess() {
        AssessChangeRequestRequest request = new AssessChangeRequestRequest(
                com.thinklab.domain.model.ChangeRequest.RiskLevel.LOW, com.thinklab.domain.model.ChangeRequest.ImpactLevel.LOW);
        when(assessChangeRequestUseCase.execute(id, request, EXECUTOR)).thenReturn(Mono.empty());

        assertEquals(HttpStatus.NO_CONTENT, controller.assess(id, EXECUTOR, request).block().getStatus());
    }

    @Test
    @DisplayName("route-for-approval returns 204 No Content")
    void routeForApproval() {
        when(routeForApprovalUseCase.execute(id, EXECUTOR)).thenReturn(Mono.empty());

        assertEquals(HttpStatus.NO_CONTENT, controller.routeForApproval(id, EXECUTOR).block().getStatus());
    }

    @Test
    @DisplayName("approval/capture returns 200 OK with the post-decision state, using X-Executor as the approver id")
    void captureApprovalDecision() {
        String approver = UUID.randomUUID().toString();
        CaptureApprovalDecisionRequest request = new CaptureApprovalDecisionRequest(DecisionOutcome.APPROVE, "ok");
        when(captureApprovalDecisionUseCase.execute(eq(id), any(), eq(request), eq(approver))).thenReturn(Mono.just(sampleResponse()));

        var response = controller.captureApprovalDecision(id, approver, request).block();
        assertEquals(HttpStatus.OK, response.getStatus());
    }

    @Test
    @DisplayName("schedule returns 204 No Content")
    void schedule() {
        ScheduleChangeRequestRequest request = new ScheduleChangeRequestRequest(Instant.now(), Instant.now().plusSeconds(3600));
        when(scheduleChangeRequestUseCase.execute(id, request, EXECUTOR, "ADMIN")).thenReturn(Mono.empty());

        assertEquals(HttpStatus.NO_CONTENT, controller.schedule(id, EXECUTOR, "ADMIN", request).block().getStatus());
    }

    @Test
    @DisplayName("control/start dispatches ControlChangeRequestUseCase.Action.START")
    void controlStart() {
        when(controlChangeRequestUseCase.execute(id, ControlChangeRequestUseCase.Action.START, EXECUTOR)).thenReturn(Mono.empty());

        assertEquals(HttpStatus.NO_CONTENT, controller.controlStart(id, EXECUTOR).block().getStatus());
    }

    @Test
    @DisplayName("complete returns 204 No Content")
    void complete() {
        CompleteChangeRequestRequest request = new CompleteChangeRequestRequest("done");
        when(completeChangeRequestUseCase.execute(id, request, EXECUTOR)).thenReturn(Mono.empty());

        assertEquals(HttpStatus.NO_CONTENT, controller.complete(id, EXECUTOR, request).block().getStatus());
    }

    @Test
    @DisplayName("rollback returns 204 No Content")
    void rollback() {
        RollbackChangeRequestRequest request = new RollbackChangeRequestRequest("failed");
        when(rollbackChangeRequestUseCase.execute(id, request, EXECUTOR)).thenReturn(Mono.empty());

        assertEquals(HttpStatus.NO_CONTENT, controller.rollback(id, EXECUTOR, request).block().getStatus());
    }

    @Test
    @DisplayName("control/close returns 204 No Content")
    void controlClose() {
        CloseChangeRequestRequest request = new CloseChangeRequestRequest("all good");
        when(closeChangeRequestUseCase.execute(id, request, EXECUTOR)).thenReturn(Mono.empty());

        assertEquals(HttpStatus.NO_CONTENT, controller.controlClose(id, EXECUTOR, request).block().getStatus());
    }

    @Test
    @DisplayName("control/cancel dispatches ControlChangeRequestUseCase.Action.CANCEL")
    void controlCancel() {
        when(controlChangeRequestUseCase.execute(id, ControlChangeRequestUseCase.Action.CANCEL, EXECUTOR)).thenReturn(Mono.empty());

        assertEquals(HttpStatus.NO_CONTENT, controller.controlCancel(id, EXECUTOR).block().getStatus());
    }

    @Test
    @DisplayName("audit-log/retrieve delegates to RetrieveChangeRequestAuditLogUseCase")
    void retrieveAuditLog() {
        ChangeRequestAuditEntryResponse entry = new ChangeRequestAuditEntryResponse(Instant.now(), "INITIATED", EXECUTOR, null, "DRAFT", "d");
        when(retrieveChangeRequestAuditLogUseCase.execute(id)).thenReturn(Mono.just(List.of(entry)));

        var result = controller.retrieveAuditLog(id).block();
        assertEquals(1, result.size());
    }
}
