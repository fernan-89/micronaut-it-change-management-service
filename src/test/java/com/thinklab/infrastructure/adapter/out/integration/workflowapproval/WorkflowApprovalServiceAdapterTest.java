package com.thinklab.infrastructure.adapter.out.integration.workflowapproval;

import com.thinklab.domain.port.ApprovalServicePort.ApprovalOutcome;
import com.thinklab.domain.port.ApprovalServicePort.DecisionOutcome;
import com.thinklab.infrastructure.adapter.out.integration.workflowapproval.WorkflowApprovalServiceAdapter.ApprovalRequestApiResponse;
import com.thinklab.infrastructure.adapter.out.integration.workflowapproval.WorkflowApprovalServiceAdapter.CaptureDecisionApiRequest;
import com.thinklab.infrastructure.adapter.out.integration.workflowapproval.WorkflowApprovalServiceAdapter.InitiateApprovalRequestApiRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowApprovalServiceAdapterTest {

    @Mock private WorkflowApprovalApiClient apiClient;

    private WorkflowApprovalServiceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new WorkflowApprovalServiceAdapter(apiClient);
    }

    @Test
    @DisplayName("initiateApprovalRequest files against the given policy with subjectType='ChangeRequest' and returns its id")
    void initiateApprovalRequest() {
        UUID organisationId = UUID.randomUUID();
        UUID changeRequestId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        UUID approvalRequestId = UUID.randomUUID();
        when(apiClient.initiate(any(), any(), any())).thenReturn(Mono.just(new ApprovalRequestApiResponse(approvalRequestId, "PENDING")));

        StepVerifier.create(adapter.initiateApprovalRequest(organisationId, changeRequestId, requesterId, policyId, "op-1"))
                .expectNext(approvalRequestId)
                .verifyComplete();

        ArgumentCaptor<InitiateApprovalRequestApiRequest> captor = ArgumentCaptor.forClass(InitiateApprovalRequestApiRequest.class);
        verify(apiClient).initiate(eq(organisationId.toString()), eq("op-1"), captor.capture());
        assertEquals("ChangeRequest", captor.getValue().subjectType());
        assertEquals(changeRequestId, captor.getValue().subjectId());
        assertEquals(requesterId, captor.getValue().requesterId());
        assertEquals(policyId, captor.getValue().policyId());
    }

    @Test
    @DisplayName("initiateApprovalRequest hides infrastructure failures behind a dependency-failure error")
    void initiateApprovalRequestFailure() {
        when(apiClient.initiate(any(), any(), any())).thenReturn(Mono.error(new RuntimeException("connection refused")));

        StepVerifier.create(adapter.initiateApprovalRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "op-1"))
                .expectErrorSatisfies(error -> {
                    assertInstanceOf(IllegalStateException.class, error);
                    assertTrue(error.getMessage().contains("Workflow Approval Service is currently unavailable"));
                })
                .verify();
    }

    @Test
    @DisplayName("captureDecision forwards the outcome/comment and maps the resolved status back")
    void captureDecision() {
        UUID approvalRequestId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        when(apiClient.captureDecision(any(), any(), any())).thenReturn(Mono.just(new ApprovalRequestApiResponse(approvalRequestId, "APPROVED")));

        StepVerifier.create(adapter.captureDecision(approvalRequestId, approverId, DecisionOutcome.APPROVE, "ok", "op-1"))
                .expectNext(ApprovalOutcome.APPROVED)
                .verifyComplete();

        ArgumentCaptor<CaptureDecisionApiRequest> captor = ArgumentCaptor.forClass(CaptureDecisionApiRequest.class);
        verify(apiClient).captureDecision(eq(approvalRequestId), eq("op-1"), captor.capture());
        assertEquals("APPROVE", captor.getValue().outcome());
        assertEquals("ok", captor.getValue().comment());
    }

    @Test
    @DisplayName("captureDecision hides infrastructure failures behind a dependency-failure error")
    void captureDecisionFailure() {
        when(apiClient.captureDecision(any(), any(), any())).thenReturn(Mono.error(new RuntimeException("timeout")));

        StepVerifier.create(adapter.captureDecision(UUID.randomUUID(), UUID.randomUUID(), DecisionOutcome.REJECT, null, "op-1"))
                .expectErrorSatisfies(error -> {
                    assertInstanceOf(IllegalStateException.class, error);
                    assertTrue(error.getMessage().contains("Workflow Approval Service is currently unavailable"));
                })
                .verify();
    }
}
