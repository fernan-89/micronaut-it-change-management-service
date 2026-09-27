package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.AssessChangeRequestRequest;
import com.thinklab.application.dto.request.CloseChangeRequestRequest;
import com.thinklab.application.dto.request.CompleteChangeRequestRequest;
import com.thinklab.application.dto.request.InitiateChangeRequestRequest;
import com.thinklab.application.dto.request.RollbackChangeRequestRequest;
import com.thinklab.application.dto.request.UpdateChangeRequestRequest;
import com.thinklab.domain.exception.ChangeRequestNotFoundException;
import com.thinklab.domain.model.ChangeRequest;
import com.thinklab.domain.model.ChangeRequest.ChangeStatus;
import com.thinklab.domain.model.ChangeRequest.ChangeType;
import com.thinklab.domain.model.ChangeRequest.ImpactLevel;
import com.thinklab.domain.model.ChangeRequest.RiskLevel;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.ChangeRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeRequestCoreUseCaseTest {

    private static final String EXECUTOR = "op-1";

    @Mock private HashServicePort hashServicePort;
    @Mock private ChangeRequestRepository changeRequestRepository;

    private UUID organisationId;
    private UUID requesterId;

    @BeforeEach
    void setUp() {
        organisationId = UUID.randomUUID();
        requesterId = UUID.randomUUID();
    }

    private ChangeRequest newChangeRequest() {
        return ChangeRequest.createNew(UUID.randomUUID(), organisationId, requesterId, "t", "d",
                ChangeType.NORMAL, Set.of(UUID.randomUUID()), EXECUTOR);
    }

    // --- InitiateChangeRequestUseCase ---

    @Test
    @DisplayName("initiate: fetches a sovereign id and persists the new ChangeRequest")
    void initiate() {
        InitiateChangeRequestRequest request = new InitiateChangeRequestRequest(requesterId, "t", "d", ChangeType.NORMAL, Set.of(UUID.randomUUID()));
        UUID sovereignId = UUID.randomUUID();
        when(hashServicePort.generateSovereignId("change-request-creation")).thenReturn(Mono.just(sovereignId));
        when(changeRequestRepository.create(any(ChangeRequest.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        InitiateChangeRequestUseCase useCase = new InitiateChangeRequestUseCase(hashServicePort, changeRequestRepository);

        StepVerifier.create(useCase.execute(organisationId, request, EXECUTOR))
                .assertNext(response -> assertEquals("t", response.title()))
                .verifyComplete();
    }

    // --- RetrieveChangeRequestUseCase ---

    @Test
    @DisplayName("retrieveById: not found surfaces ChangeRequestNotFoundException")
    void retrieveByIdNotFound() {
        UUID id = UUID.randomUUID();
        when(changeRequestRepository.findById(id)).thenReturn(Mono.empty());
        RetrieveChangeRequestUseCase useCase = new RetrieveChangeRequestUseCase(changeRequestRepository);

        StepVerifier.create(useCase.execute(id)).expectError(ChangeRequestNotFoundException.class).verify();
    }

    @Test
    @DisplayName("retrieveById: maps the found ChangeRequest to a response")
    void retrieveByIdFound() {
        ChangeRequest cr = newChangeRequest();
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Mono.just(cr));
        RetrieveChangeRequestUseCase useCase = new RetrieveChangeRequestUseCase(changeRequestRepository);

        StepVerifier.create(useCase.execute(cr.getId())).expectNextCount(1).verifyComplete();
    }

    // --- RetrieveChangeRequestsUseCase ---

    @Test
    @DisplayName("retrieveAll: delegates to the repository's tenant-scoped, optionally status-filtered query")
    void retrieveAll() {
        when(changeRequestRepository.findAllByOrganisationId(organisationId, ChangeStatus.DRAFT)).thenReturn(Flux.just(newChangeRequest()));
        RetrieveChangeRequestsUseCase useCase = new RetrieveChangeRequestsUseCase(changeRequestRepository);

        StepVerifier.create(useCase.execute(organisationId, ChangeStatus.DRAFT)).expectNextCount(1).verifyComplete();
    }

    // --- UpdateChangeRequestUseCase ---

    @Test
    @DisplayName("update: not found surfaces ChangeRequestNotFoundException")
    void updateNotFound() {
        UUID id = UUID.randomUUID();
        when(changeRequestRepository.findById(id)).thenReturn(Mono.empty());
        UpdateChangeRequestUseCase useCase = new UpdateChangeRequestUseCase(changeRequestRepository);

        StepVerifier.create(useCase.execute(id, new UpdateChangeRequestRequest("t", "d"), EXECUTOR))
                .expectError(ChangeRequestNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("update: applies the domain mutation and persists the granular update")
    void updateSuccess() {
        ChangeRequest cr = newChangeRequest();
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Mono.just(cr));
        when(changeRequestRepository.updateBasicInfo(eq(cr.getId()), eq("New title"), eq("New desc"), any())).thenReturn(Mono.empty());
        UpdateChangeRequestUseCase useCase = new UpdateChangeRequestUseCase(changeRequestRepository);

        StepVerifier.create(useCase.execute(cr.getId(), new UpdateChangeRequestRequest("New title", "New desc"), EXECUTOR)).verifyComplete();
    }

    // --- ControlChangeRequestUseCase ---

    @Test
    @DisplayName("control: not found surfaces ChangeRequestNotFoundException")
    void controlNotFound() {
        UUID id = UUID.randomUUID();
        when(changeRequestRepository.findById(id)).thenReturn(Mono.empty());
        ControlChangeRequestUseCase useCase = new ControlChangeRequestUseCase(changeRequestRepository);

        StepVerifier.create(useCase.execute(id, ControlChangeRequestUseCase.Action.SUBMIT, EXECUTOR))
                .expectError(ChangeRequestNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("control: SUBMIT, START and CANCEL each dispatch their domain transition")
    void controlActions() {
        ChangeRequest submittable = newChangeRequest();
        when(changeRequestRepository.findById(submittable.getId())).thenReturn(Mono.just(submittable));
        when(changeRequestRepository.updateStatus(eq(submittable.getId()), eq(ChangeStatus.SUBMITTED), any())).thenReturn(Mono.empty());
        ControlChangeRequestUseCase useCase = new ControlChangeRequestUseCase(changeRequestRepository);
        StepVerifier.create(useCase.execute(submittable.getId(), ControlChangeRequestUseCase.Action.SUBMIT, EXECUTOR)).verifyComplete();

        ChangeRequest cancellable = newChangeRequest();
        when(changeRequestRepository.findById(cancellable.getId())).thenReturn(Mono.just(cancellable));
        when(changeRequestRepository.updateStatus(eq(cancellable.getId()), eq(ChangeStatus.CANCELLED), any())).thenReturn(Mono.empty());
        StepVerifier.create(useCase.execute(cancellable.getId(), ControlChangeRequestUseCase.Action.CANCEL, EXECUTOR)).verifyComplete();

        ChangeRequest scheduled = ChangeRequest.createNew(UUID.randomUUID(), organisationId, requesterId, "t", "d",
                ChangeType.STANDARD, Set.of(UUID.randomUUID()), EXECUTOR);
        scheduled.submit(EXECUTOR);
        scheduled.assess(RiskLevel.LOW, ImpactLevel.LOW, EXECUTOR);
        scheduled.preApprove(EXECUTOR);
        scheduled.schedule(UUID.randomUUID(), java.time.Instant.now(), java.time.Instant.now().plusSeconds(3600), EXECUTOR);
        when(changeRequestRepository.findById(scheduled.getId())).thenReturn(Mono.just(scheduled));
        when(changeRequestRepository.updateStatus(eq(scheduled.getId()), eq(ChangeStatus.IN_PROGRESS), any())).thenReturn(Mono.empty());
        StepVerifier.create(useCase.execute(scheduled.getId(), ControlChangeRequestUseCase.Action.START, EXECUTOR)).verifyComplete();
    }

    // --- AssessChangeRequestUseCase ---

    @Test
    @DisplayName("assess: not found surfaces ChangeRequestNotFoundException")
    void assessNotFound() {
        UUID id = UUID.randomUUID();
        when(changeRequestRepository.findById(id)).thenReturn(Mono.empty());
        AssessChangeRequestUseCase useCase = new AssessChangeRequestUseCase(changeRequestRepository);

        StepVerifier.create(useCase.execute(id, new AssessChangeRequestRequest(RiskLevel.LOW, ImpactLevel.LOW), EXECUTOR))
                .expectError(ChangeRequestNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("assess: applies the domain mutation and persists risk/impact/status")
    void assessSuccess() {
        ChangeRequest cr = newChangeRequest();
        cr.submit(EXECUTOR);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Mono.just(cr));
        when(changeRequestRepository.updateAssessment(eq(cr.getId()), eq(RiskLevel.HIGH), eq(ImpactLevel.MEDIUM), eq(ChangeStatus.ASSESSED), any()))
                .thenReturn(Mono.empty());
        AssessChangeRequestUseCase useCase = new AssessChangeRequestUseCase(changeRequestRepository);

        StepVerifier.create(useCase.execute(cr.getId(), new AssessChangeRequestRequest(RiskLevel.HIGH, ImpactLevel.MEDIUM), EXECUTOR)).verifyComplete();
    }

    // --- CompleteChangeRequestUseCase / RollbackChangeRequestUseCase / CloseChangeRequestUseCase ---

    private ChangeRequest inProgressChangeRequest() {
        ChangeRequest cr = ChangeRequest.createNew(UUID.randomUUID(), organisationId, requesterId, "t", "d",
                ChangeType.STANDARD, Set.of(UUID.randomUUID()), EXECUTOR);
        cr.submit(EXECUTOR);
        cr.assess(RiskLevel.LOW, ImpactLevel.LOW, EXECUTOR);
        cr.preApprove(EXECUTOR);
        cr.schedule(UUID.randomUUID(), java.time.Instant.now(), java.time.Instant.now().plusSeconds(3600), EXECUTOR);
        cr.start(EXECUTOR);
        return cr;
    }

    @Test
    @DisplayName("complete: not found surfaces ChangeRequestNotFoundException; success persists the notes")
    void complete() {
        UUID missing = UUID.randomUUID();
        when(changeRequestRepository.findById(missing)).thenReturn(Mono.empty());
        CompleteChangeRequestUseCase useCase = new CompleteChangeRequestUseCase(changeRequestRepository);
        StepVerifier.create(useCase.execute(missing, new CompleteChangeRequestRequest("done"), EXECUTOR))
                .expectError(ChangeRequestNotFoundException.class)
                .verify();

        ChangeRequest cr = inProgressChangeRequest();
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Mono.just(cr));
        when(changeRequestRepository.updateCompletion(eq(cr.getId()), eq("done"), eq(ChangeStatus.IMPLEMENTED), any())).thenReturn(Mono.empty());
        StepVerifier.create(useCase.execute(cr.getId(), new CompleteChangeRequestRequest("done"), EXECUTOR)).verifyComplete();
    }

    @Test
    @DisplayName("rollback: not found surfaces ChangeRequestNotFoundException; success persists the reason")
    void rollback() {
        UUID missing = UUID.randomUUID();
        when(changeRequestRepository.findById(missing)).thenReturn(Mono.empty());
        RollbackChangeRequestUseCase useCase = new RollbackChangeRequestUseCase(changeRequestRepository);
        StepVerifier.create(useCase.execute(missing, new RollbackChangeRequestRequest("failed"), EXECUTOR))
                .expectError(ChangeRequestNotFoundException.class)
                .verify();

        ChangeRequest cr = inProgressChangeRequest();
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Mono.just(cr));
        when(changeRequestRepository.updateRollback(eq(cr.getId()), eq("failed"), eq(ChangeStatus.ROLLED_BACK), any())).thenReturn(Mono.empty());
        StepVerifier.create(useCase.execute(cr.getId(), new RollbackChangeRequestRequest("failed"), EXECUTOR)).verifyComplete();
    }

    @Test
    @DisplayName("close: not found surfaces ChangeRequestNotFoundException; success persists the notes")
    void close() {
        UUID missing = UUID.randomUUID();
        when(changeRequestRepository.findById(missing)).thenReturn(Mono.empty());
        CloseChangeRequestUseCase useCase = new CloseChangeRequestUseCase(changeRequestRepository);
        StepVerifier.create(useCase.execute(missing, new CloseChangeRequestRequest("all good"), EXECUTOR))
                .expectError(ChangeRequestNotFoundException.class)
                .verify();

        ChangeRequest cr = inProgressChangeRequest();
        cr.complete("done", EXECUTOR);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Mono.just(cr));
        when(changeRequestRepository.updateClose(eq(cr.getId()), eq("all good"), eq(ChangeStatus.CLOSED), any())).thenReturn(Mono.empty());
        StepVerifier.create(useCase.execute(cr.getId(), new CloseChangeRequestRequest("all good"), EXECUTOR)).verifyComplete();
    }

    // --- RetrieveChangeRequestAuditLogUseCase ---

    @Test
    @DisplayName("auditLog: not found surfaces ChangeRequestNotFoundException")
    void auditLogNotFound() {
        UUID id = UUID.randomUUID();
        when(changeRequestRepository.findById(id)).thenReturn(Mono.empty());
        RetrieveChangeRequestAuditLogUseCase useCase = new RetrieveChangeRequestAuditLogUseCase(changeRequestRepository);

        StepVerifier.create(useCase.execute(id)).expectError(ChangeRequestNotFoundException.class).verify();
    }

    @Test
    @DisplayName("auditLog: collects the ledger into a Mono<List<...>>")
    void auditLogSuccess() {
        ChangeRequest cr = newChangeRequest();
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Mono.just(cr));
        RetrieveChangeRequestAuditLogUseCase useCase = new RetrieveChangeRequestAuditLogUseCase(changeRequestRepository);

        StepVerifier.create(useCase.execute(cr.getId()))
                .assertNext(entries -> assertEquals(1, entries.size()))
                .verifyComplete();
    }
}
