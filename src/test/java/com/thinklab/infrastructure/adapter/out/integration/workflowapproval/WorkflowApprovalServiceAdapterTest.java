package com.thinklab.infrastructure.adapter.out.integration.workflowapproval;

import com.thinklab.domain.exception.InvalidChangeRequestStatusException;
import com.thinklab.domain.port.ApprovalServicePort.ApprovalOutcome;
import com.thinklab.domain.port.ApprovalServicePort.DecisionOutcome;
import com.thinklab.infrastructure.adapter.out.integration.workflowapproval.WorkflowApprovalServiceAdapter.ApprovalRequestApiResponse;
import com.thinklab.infrastructure.adapter.out.integration.workflowapproval.WorkflowApprovalServiceAdapter.CaptureDecisionApiRequest;
import com.thinklab.infrastructure.adapter.out.integration.workflowapproval.WorkflowApprovalServiceAdapter.InitiateApprovalRequestApiRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.Set;
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
    @DisplayName("a 409 on a decision (an approver who is not eligible) is relayed as a 409 with the reason; without a body it falls back to the message; other statuses stay dependency failures")
    void conflictIsRelayed() {
        var withBody = new HttpClientResponseException("Conflict", HttpResponse.status(HttpStatus.CONFLICT).body(Map.of("detail", "approver is not eligible")));
        var withoutBody = new HttpClientResponseException("Conflict without body", HttpResponse.status(HttpStatus.CONFLICT));
        var notFound = new HttpClientResponseException("Not Found", HttpResponse.status(HttpStatus.NOT_FOUND));
        when(apiClient.captureDecision(any(), any(), any())).thenReturn(Mono.error(withBody)).thenReturn(Mono.error(withoutBody)).thenReturn(Mono.error(notFound));

        StepVerifier.create(adapter.captureDecision(UUID.randomUUID(), UUID.randomUUID(), DecisionOutcome.APPROVE, null, "op-1"))
                .expectErrorSatisfies(error -> { assertInstanceOf(InvalidChangeRequestStatusException.class, error); assertEquals("approver is not eligible", error.getMessage()); }).verify();
        StepVerifier.create(adapter.captureDecision(UUID.randomUUID(), UUID.randomUUID(), DecisionOutcome.APPROVE, null, "op-1"))
                .expectErrorSatisfies(error -> { assertInstanceOf(InvalidChangeRequestStatusException.class, error); assertEquals("Conflict without body", error.getMessage()); }).verify();
        StepVerifier.create(adapter.captureDecision(UUID.randomUUID(), UUID.randomUUID(), DecisionOutcome.APPROVE, null, "op-1"))
                .expectErrorSatisfies(error -> assertInstanceOf(IllegalStateException.class, error)).verify();
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
    @Test
    @DisplayName("approversOfPolicy returns everyone who is an approver of any stage, and tolerates a policy without stages or without the flat list")
    void approversOfPolicy() {
        UUID policyId = UUID.randomUUID();
        UUID lead = UUID.randomUUID();
        UUID security = UUID.randomUUID();
        UUID director = UUID.randomUUID();
        when(apiClient.retrievePolicy(policyId)).thenReturn(Mono.just(new WorkflowApprovalServiceAdapter.PolicyApiResponse(
                List.of(lead), List.of(new WorkflowApprovalServiceAdapter.PolicyStageApiResponse(List.of(lead)),
                        new WorkflowApprovalServiceAdapter.PolicyStageApiResponse(List.of(security, director))))));

        StepVerifier.create(adapter.approversOfPolicy(policyId)).expectNext(Set.of(lead, security, director)).verifyComplete();

        // an older workflow-approval answers only the flat list; a newer one could omit it
        when(apiClient.retrievePolicy(policyId)).thenReturn(Mono.just(new WorkflowApprovalServiceAdapter.PolicyApiResponse(List.of(lead), null)));
        StepVerifier.create(adapter.approversOfPolicy(policyId)).expectNext(Set.of(lead)).verifyComplete();
        when(apiClient.retrievePolicy(policyId)).thenReturn(Mono.just(new WorkflowApprovalServiceAdapter.PolicyApiResponse(null,
                List.of(new WorkflowApprovalServiceAdapter.PolicyStageApiResponse(List.of(security))))));
        StepVerifier.create(adapter.approversOfPolicy(policyId)).expectNext(Set.of(security)).verifyComplete();
    }

    @Test
    @DisplayName("approversOfPolicy hides infrastructure failures behind a dependency-failure error")
    void approversOfPolicyFailure() {
        UUID policyId = UUID.randomUUID();
        when(apiClient.retrievePolicy(policyId)).thenReturn(Mono.error(new RuntimeException("boom")));

        StepVerifier.create(adapter.approversOfPolicy(policyId))
                .expectErrorSatisfies(error -> assertTrue(error.getMessage().contains("Workflow Approval Service is currently unavailable")))
                .verify();
    }
}
