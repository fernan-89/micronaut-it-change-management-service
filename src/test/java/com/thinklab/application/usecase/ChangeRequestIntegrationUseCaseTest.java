package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.CaptureApprovalDecisionRequest;
import com.thinklab.application.dto.request.ScheduleChangeRequestRequest;
import com.thinklab.domain.exception.ChangeRequestNotFoundException;
import com.thinklab.domain.exception.InvalidChangeRequestStatusException;
import com.thinklab.domain.model.ChangeRequest;
import com.thinklab.domain.model.ChangeRequest.ChangeStatus;
import com.thinklab.domain.model.ChangeRequest.ChangeType;
import com.thinklab.domain.model.ChangeRequest.ImpactLevel;
import com.thinklab.domain.model.ChangeRequest.RiskLevel;
import com.thinklab.domain.port.ApprovalServicePort;
import com.thinklab.domain.port.ApprovalServicePort.ApprovalOutcome;
import com.thinklab.domain.port.ApprovalServicePort.DecisionOutcome;
import com.thinklab.domain.port.OperationWindowServicePort;
import com.thinklab.domain.repository.ChangeRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeRequestIntegrationUseCaseTest {

    private static final String EXECUTOR = "op-1";
    private static final String CAB_POLICY = "11111111-1111-1111-1111-111111111111";
    private static final String ECAB_POLICY = "22222222-2222-2222-2222-222222222222";

    @Mock private ChangeRequestRepository changeRequestRepository;
    @Mock private ApprovalServicePort approvalServicePort;
    @Mock private OperationWindowServicePort operationWindowServicePort;

    private UUID organisationId;
    private UUID requesterId;

    @BeforeEach
    void setUp() {
        organisationId = UUID.randomUUID();
        requesterId = UUID.randomUUID();
    }

    private ChangeRequest assessedChangeRequest(ChangeType type) {
        ChangeRequest cr = ChangeRequest.createNew(UUID.randomUUID(), organisationId, requesterId, "t", "d",
                type, Set.of(UUID.randomUUID()), null, EXECUTOR);
        cr.submit(EXECUTOR);
        cr.assess(RiskLevel.LOW, ImpactLevel.LOW, EXECUTOR);
        return cr;
    }

    // --- RouteForApprovalUseCase ---

    @Test
    @DisplayName("routeForApproval: not found surfaces ChangeRequestNotFoundException")
    void routeForApprovalNotFound() {
        UUID id = UUID.randomUUID();
        when(changeRequestRepository.findById(id)).thenReturn(Mono.empty());
        RouteForApprovalUseCase useCase = new RouteForApprovalUseCase(changeRequestRepository, approvalServicePort, CAB_POLICY, ECAB_POLICY);

        StepVerifier.create(useCase.execute(id, EXECUTOR)).expectError(ChangeRequestNotFoundException.class).verify();
    }

    @Test
    @DisplayName("routeForApproval: a STANDARD change is pre-approved with no outbound call")
    void routeForApprovalStandard() {
        ChangeRequest cr = assessedChangeRequest(ChangeType.STANDARD);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Mono.just(cr));
        when(changeRequestRepository.updateStatus(eq(cr.getId()), eq(ChangeStatus.APPROVED), any())).thenReturn(Mono.empty());
        RouteForApprovalUseCase useCase = new RouteForApprovalUseCase(changeRequestRepository, approvalServicePort, CAB_POLICY, ECAB_POLICY);

        StepVerifier.create(useCase.execute(cr.getId(), EXECUTOR)).verifyComplete();

        verify(approvalServicePort, org.mockito.Mockito.never()).initiateApprovalRequest(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("routeForApproval: a NORMAL change files against the CAB policy and routes to CAB_REVIEW")
    void routeForApprovalNormal() {
        ChangeRequest cr = assessedChangeRequest(ChangeType.NORMAL);
        UUID approvalRequestId = UUID.randomUUID();
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Mono.just(cr));
        when(approvalServicePort.initiateApprovalRequest(organisationId, cr.getId(), requesterId, UUID.fromString(CAB_POLICY), EXECUTOR))
                .thenReturn(Mono.just(approvalRequestId));
        when(changeRequestRepository.updateRouting(eq(cr.getId()), eq(approvalRequestId), eq(ChangeStatus.CAB_REVIEW), any())).thenReturn(Mono.empty());
        RouteForApprovalUseCase useCase = new RouteForApprovalUseCase(changeRequestRepository, approvalServicePort, CAB_POLICY, ECAB_POLICY);

        StepVerifier.create(useCase.execute(cr.getId(), EXECUTOR)).verifyComplete();
    }

    @Test
    @DisplayName("routeForApproval: an EMERGENCY change files against the ECAB policy and routes to ECAB_REVIEW")
    void routeForApprovalEmergency() {
        ChangeRequest cr = assessedChangeRequest(ChangeType.EMERGENCY);
        UUID approvalRequestId = UUID.randomUUID();
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Mono.just(cr));
        when(approvalServicePort.initiateApprovalRequest(organisationId, cr.getId(), requesterId, UUID.fromString(ECAB_POLICY), EXECUTOR))
                .thenReturn(Mono.just(approvalRequestId));
        when(changeRequestRepository.updateRouting(eq(cr.getId()), eq(approvalRequestId), eq(ChangeStatus.ECAB_REVIEW), any())).thenReturn(Mono.empty());
        RouteForApprovalUseCase useCase = new RouteForApprovalUseCase(changeRequestRepository, approvalServicePort, CAB_POLICY, ECAB_POLICY);

        StepVerifier.create(useCase.execute(cr.getId(), EXECUTOR)).verifyComplete();
    }

    @Test
    @DisplayName("routeForApproval: a missing policy configuration (blank or null) surfaces a dependency-failure error")
    void routeForApprovalMissingPolicyConfig() {
        ChangeRequest blank = assessedChangeRequest(ChangeType.NORMAL);
        when(changeRequestRepository.findById(blank.getId())).thenReturn(Mono.just(blank));
        RouteForApprovalUseCase blankConfigured = new RouteForApprovalUseCase(changeRequestRepository, approvalServicePort, "", "");

        StepVerifier.create(blankConfigured.execute(blank.getId(), EXECUTOR))
                .expectError(IllegalStateException.class)
                .verify();

        ChangeRequest nullConfig = assessedChangeRequest(ChangeType.EMERGENCY);
        when(changeRequestRepository.findById(nullConfig.getId())).thenReturn(Mono.just(nullConfig));
        RouteForApprovalUseCase nullConfigured = new RouteForApprovalUseCase(changeRequestRepository, approvalServicePort, null, null);

        StepVerifier.create(nullConfigured.execute(nullConfig.getId(), EXECUTOR))
                .expectError(IllegalStateException.class)
                .verify();
    }

    // --- CaptureApprovalDecisionUseCase ---

    @Test
    @DisplayName("captureApprovalDecision: not found surfaces ChangeRequestNotFoundException")
    void captureDecisionNotFound() {
        UUID id = UUID.randomUUID();
        when(changeRequestRepository.findById(id)).thenReturn(Mono.empty());
        CaptureApprovalDecisionUseCase useCase = new CaptureApprovalDecisionUseCase(changeRequestRepository, approvalServicePort);

        StepVerifier.create(useCase.execute(id, UUID.randomUUID(), new CaptureApprovalDecisionRequest(DecisionOutcome.APPROVE, "ok"), EXECUTOR))
                .expectError(ChangeRequestNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("captureApprovalDecision: no approval in progress is an illegal transition")
    void captureDecisionNoApprovalInProgress() {
        ChangeRequest cr = assessedChangeRequest(ChangeType.NORMAL);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Mono.just(cr));
        CaptureApprovalDecisionUseCase useCase = new CaptureApprovalDecisionUseCase(changeRequestRepository, approvalServicePort);

        StepVerifier.create(useCase.execute(cr.getId(), UUID.randomUUID(), new CaptureApprovalDecisionRequest(DecisionOutcome.APPROVE, "ok"), EXECUTOR))
                .expectError(InvalidChangeRequestStatusException.class)
                .verify();
    }

    @Test
    @DisplayName("captureApprovalDecision: a resolved APPROVED outcome transitions the ChangeRequest and persists it")
    void captureDecisionApproved() {
        ChangeRequest cr = assessedChangeRequest(ChangeType.NORMAL);
        UUID approvalRequestId = UUID.randomUUID();
        cr.routeForApproval(approvalRequestId, EXECUTOR);
        UUID approverId = UUID.randomUUID();
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Mono.just(cr));
        when(approvalServicePort.captureDecision(approvalRequestId, approverId, DecisionOutcome.APPROVE, "ok", EXECUTOR))
                .thenReturn(Mono.just(ApprovalOutcome.APPROVED));
        lenient().when(changeRequestRepository.updateStatus(eq(cr.getId()), eq(ChangeStatus.APPROVED), any())).thenReturn(Mono.empty());
        CaptureApprovalDecisionUseCase useCase = new CaptureApprovalDecisionUseCase(changeRequestRepository, approvalServicePort);

        StepVerifier.create(useCase.execute(cr.getId(), approverId, new CaptureApprovalDecisionRequest(DecisionOutcome.APPROVE, "ok"), EXECUTOR))
                .assertNext(response -> assertEquals("APPROVED", response.status()))
                .verifyComplete();
    }

    @Test
    @DisplayName("captureApprovalDecision: a resolved REJECTED outcome transitions the ChangeRequest and persists it")
    void captureDecisionRejected() {
        ChangeRequest cr = assessedChangeRequest(ChangeType.EMERGENCY);
        UUID approvalRequestId = UUID.randomUUID();
        cr.routeForApproval(approvalRequestId, EXECUTOR);
        UUID approverId = UUID.randomUUID();
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Mono.just(cr));
        when(approvalServicePort.captureDecision(approvalRequestId, approverId, DecisionOutcome.REJECT, null, EXECUTOR))
                .thenReturn(Mono.just(ApprovalOutcome.REJECTED));
        lenient().when(changeRequestRepository.updateStatus(eq(cr.getId()), eq(ChangeStatus.REJECTED), any())).thenReturn(Mono.empty());
        CaptureApprovalDecisionUseCase useCase = new CaptureApprovalDecisionUseCase(changeRequestRepository, approvalServicePort);

        StepVerifier.create(useCase.execute(cr.getId(), approverId, new CaptureApprovalDecisionRequest(DecisionOutcome.REJECT, null), EXECUTOR))
                .assertNext(response -> assertEquals("REJECTED", response.status()))
                .verifyComplete();
    }

    @Test
    @DisplayName("captureApprovalDecision: a still-PENDING quorum leaves the ChangeRequest untouched")
    void captureDecisionStillPending() {
        ChangeRequest cr = assessedChangeRequest(ChangeType.NORMAL);
        UUID approvalRequestId = UUID.randomUUID();
        cr.routeForApproval(approvalRequestId, EXECUTOR);
        UUID approverId = UUID.randomUUID();
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Mono.just(cr));
        when(approvalServicePort.captureDecision(approvalRequestId, approverId, DecisionOutcome.APPROVE, "ok", EXECUTOR))
                .thenReturn(Mono.just(ApprovalOutcome.PENDING));
        CaptureApprovalDecisionUseCase useCase = new CaptureApprovalDecisionUseCase(changeRequestRepository, approvalServicePort);

        StepVerifier.create(useCase.execute(cr.getId(), approverId, new CaptureApprovalDecisionRequest(DecisionOutcome.APPROVE, "ok"), EXECUTOR))
                .assertNext(response -> assertEquals("CAB_REVIEW", response.status()))
                .verifyComplete();

        verify(changeRequestRepository, org.mockito.Mockito.never()).updateStatus(any(), any(), any());
    }

    // --- ScheduleChangeRequestUseCase ---

    @Test
    @DisplayName("schedule: not found surfaces ChangeRequestNotFoundException")
    void scheduleNotFound() {
        UUID id = UUID.randomUUID();
        when(changeRequestRepository.findById(id)).thenReturn(Mono.empty());
        ScheduleChangeRequestUseCase useCase = new ScheduleChangeRequestUseCase(changeRequestRepository, operationWindowServicePort);

        StepVerifier.create(useCase.execute(id, new ScheduleChangeRequestRequest(Instant.now(), Instant.now().plusSeconds(3600)), EXECUTOR))
                .expectError(ChangeRequestNotFoundException.class)
                .verify();
    }

    private ChangeRequest approvedEmergency() {
        ChangeRequest cr = assessedChangeRequest(ChangeType.EMERGENCY);
        cr.routeForApproval(UUID.randomUUID(), EXECUTOR);
        cr.approve(EXECUTOR);
        return cr;
    }

    @Test
    @DisplayName("schedule: an approved EMERGENCY change passes its freeze-override justification to the window reservation")
    void scheduleWithFreezeOverride() {
        ChangeRequest cr = approvedEmergency();
        Instant start = Instant.now();
        Instant end = start.plusSeconds(3600);
        UUID windowId = UUID.randomUUID();
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Mono.just(cr));
        when(operationWindowServicePort.reserveImplementationWindow(organisationId, cr.getTitle(), cr.getTargetAssetIds(), start, end, EXECUTOR, "P1 outage"))
                .thenReturn(Mono.just(windowId));
        when(changeRequestRepository.updateScheduling(eq(cr.getId()), eq(windowId), eq(start), eq(end), eq(ChangeStatus.SCHEDULED), any()))
                .thenReturn(Mono.empty());
        ScheduleChangeRequestUseCase useCase = new ScheduleChangeRequestUseCase(changeRequestRepository, operationWindowServicePort);

        StepVerifier.create(useCase.execute(cr.getId(), new ScheduleChangeRequestRequest(start, end, "P1 outage"), EXECUTOR)).verifyComplete();
    }

    @Test
    @DisplayName("schedule: a freeze override on a non-EMERGENCY change is refused before any window is reserved")
    void scheduleOverrideRefusedForNonEmergency() {
        ChangeRequest cr = assessedChangeRequest(ChangeType.STANDARD);
        cr.preApprove(EXECUTOR);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Mono.just(cr));
        ScheduleChangeRequestUseCase useCase = new ScheduleChangeRequestUseCase(changeRequestRepository, operationWindowServicePort);

        assertThrows(IllegalArgumentException.class, () -> useCase.execute(cr.getId(),
                new ScheduleChangeRequestRequest(Instant.now(), Instant.now().plusSeconds(3600), "P1 outage"), EXECUTOR).block());

        verifyNoInteractions(operationWindowServicePort);
    }

    @Test
    @DisplayName("schedule: reserves the implementation window and persists the granular update")
    void scheduleSuccess() {
        ChangeRequest cr = assessedChangeRequest(ChangeType.STANDARD);
        cr.preApprove(EXECUTOR);
        Instant start = Instant.now();
        Instant end = start.plusSeconds(3600);
        UUID windowId = UUID.randomUUID();
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Mono.just(cr));
        when(operationWindowServicePort.reserveImplementationWindow(organisationId, cr.getTitle(), cr.getTargetAssetIds(), start, end, EXECUTOR, null))
                .thenReturn(Mono.just(windowId));
        when(changeRequestRepository.updateScheduling(eq(cr.getId()), eq(windowId), eq(start), eq(end), eq(ChangeStatus.SCHEDULED), any()))
                .thenReturn(Mono.empty());
        ScheduleChangeRequestUseCase useCase = new ScheduleChangeRequestUseCase(changeRequestRepository, operationWindowServicePort);

        StepVerifier.create(useCase.execute(cr.getId(), new ScheduleChangeRequestRequest(start, end), EXECUTOR)).verifyComplete();
    }
}
