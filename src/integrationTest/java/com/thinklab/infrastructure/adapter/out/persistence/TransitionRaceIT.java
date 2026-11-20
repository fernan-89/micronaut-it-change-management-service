package com.thinklab.infrastructure.adapter.out.persistence;

import com.thinklab.application.dto.request.ScheduleChangeRequestRequest;
import com.thinklab.application.usecase.ControlChangeRequestUseCase;
import com.thinklab.application.usecase.RouteForApprovalUseCase;
import com.thinklab.application.usecase.ScheduleChangeRequestUseCase;
import com.thinklab.domain.exception.InvalidChangeRequestStatusException;
import com.thinklab.domain.model.ChangeRequest;
import com.thinklab.domain.model.ChangeRequest.ChangeStatus;
import com.thinklab.domain.model.ChangeRequest.ChangeType;
import com.thinklab.domain.model.ChangeRequest.ImpactLevel;
import com.thinklab.domain.model.ChangeRequest.RiskLevel;
import com.thinklab.domain.port.ApprovalServicePort;
import com.thinklab.domain.port.OperationWindowServicePort;
import com.thinklab.domain.model.ApprovalSettings;
import com.thinklab.domain.repository.ApprovalSettingsRepository;
import com.thinklab.domain.repository.ChangeRequestRepository;
import com.thinklab.infrastructure.adapter.out.integration.operationwindow.OperationWindowServiceAdapter;
import com.thinklab.infrastructure.adapter.out.integration.workflowapproval.WorkflowApprovalServiceAdapter;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The transition races against a real MongoDB (platform race audit, ACTION-PLAN 0.5): a change read by two requests at once must have exactly one of
 * them win. Routing creates an approval request and scheduling reserves a window in other services, so those two must never be done twice for one
 * change either. The other services are replaced by ones that count what they are asked to create and take their time.
 */
@MicronautTest(packages = "com.thinklab", transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransitionRaceIT implements TestPropertyProvider {

    private static final String DATABASE = "it_change_management_race_it";
    private static final String EXECUTOR = "op-1";
    private static final UUID CAB = UUID.fromString("11111111-2222-4333-8444-555555555555");

    @Override
    public Map<String, String> getProperties() {
        return Map.of("mongodb.uri", MongoContainer.uri(DATABASE));
    }

    @Singleton
    @Replaces(WorkflowApprovalServiceAdapter.class)
    static class CountingApprovals implements ApprovalServicePort {
        final AtomicInteger created = new AtomicInteger();

        @Override
        public Mono<UUID> initiateApprovalRequest(UUID organisationId, UUID changeRequestId, UUID requesterId, UUID policyId, String executor) {
            return Mono.defer(() -> {
                created.incrementAndGet();
                return Mono.just(UUID.randomUUID()).delayElement(Duration.ofMillis(300));
            });
        }

        @Override
        public Mono<ApprovalOutcome> captureDecision(UUID organisationId, UUID approvalRequestId, UUID approverId, DecisionOutcome outcome, String comment, String executor) {
            return Mono.just(ApprovalOutcome.PENDING);
        }

        @Override
        public Mono<Set<UUID>> approversOfPolicy(UUID organisationId, UUID policyId) {
            return Mono.just(Set.of());
        }

        @Override
        public Mono<Boolean> policyExists(UUID organisationId, UUID policyId) {
            return Mono.just(true);
        }
    }

    @Singleton
    @Replaces(OperationWindowServiceAdapter.class)
    static class CountingWindows implements OperationWindowServicePort {
        final AtomicInteger reserved = new AtomicInteger();

        @Override
        public Mono<UUID> reserveImplementationWindow(UUID organisationId, String title, Set<UUID> targetAssetIds, Instant startAt, Instant endAt, String executor,
                                                      String freezeOverrideJustification) {
            return Mono.defer(() -> {
                reserved.incrementAndGet();
                return Mono.just(UUID.randomUUID()).delayElement(Duration.ofMillis(300));
            });
        }
    }

    @Inject ChangeRequestRepository repository;
    @Inject ApprovalSettingsRepository approvalSettings;
    @Inject ControlChangeRequestUseCase control;
    @Inject RouteForApprovalUseCase route;
    @Inject ScheduleChangeRequestUseCase schedule;
    @Inject CountingApprovals approvals;
    @Inject CountingWindows windows;

    private ChangeRequest draft() {
        ChangeRequest change = ChangeRequest.createNew(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Race change", "d", ChangeType.NORMAL, Set.of(UUID.randomUUID()), null, EXECUTOR);
        repository.create(change).block();
        approvalSettings.save(new ApprovalSettings(change.getOrganisationId(), CAB, CAB, Instant.now(), EXECUTOR)).block();
        return change;
    }

    private ChangeRequest assessed() {
        ChangeRequest change = draft();
        repository.updateStatus(change.getId(), change.submit(EXECUTOR).toStatus(), change.getAuditTrail().get(change.getAuditTrail().size() - 1)).block();
        var entry = change.assess(RiskLevel.MEDIUM, ImpactLevel.MEDIUM, EXECUTOR);
        repository.updateAssessment(change.getId(), RiskLevel.MEDIUM, ImpactLevel.MEDIUM, change.getStatus(), entry).block();
        return change;
    }

    private static List<String> outcomes(Flux<String> results) {
        return results.collectList().block(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("ten submits of one draft at once: exactly one wins, the others are refused with a conflict")
    void tenSubmitsAtOnce() {
        ChangeRequest change = draft();

        List<String> results = outcomes(Flux.range(0, 10)
                .flatMap(i -> control.execute(change.getId(), change.getOrganisationId(), ControlChangeRequestUseCase.Action.SUBMIT, EXECUTOR).thenReturn("OK")
                        .onErrorResume(e -> Mono.just(e.getClass().getSimpleName())), 10));

        assertEquals(1, results.stream().filter("OK"::equals).count(), results.toString());
        assertEquals(9, results.stream().filter(InvalidChangeRequestStatusException.class.getSimpleName()::equals).count(), results.toString());
        ChangeRequest stored = repository.findById(change.getId(), change.getOrganisationId()).block();
        assertEquals(ChangeStatus.SUBMITTED, stored.getStatus());
        assertEquals(2, stored.getAuditTrail().size(), "one entry for creating and one for the single submit that won");
    }

    @Test
    @DisplayName("a submit and a cancel at once: one wins, and the final status and the trail agree")
    void submitAgainstCancel() {
        ChangeRequest change = draft();

        List<String> results = outcomes(Flux.just(ControlChangeRequestUseCase.Action.SUBMIT, ControlChangeRequestUseCase.Action.CANCEL)
                .flatMap(action -> control.execute(change.getId(), change.getOrganisationId(), action, EXECUTOR).thenReturn("OK").onErrorResume(e -> Mono.just("CONFLICT")), 2));

        assertEquals(1, results.stream().filter("OK"::equals).count(), results.toString());
        ChangeRequest stored = repository.findById(change.getId(), change.getOrganisationId()).block();
        assertEquals(2, stored.getAuditTrail().size());
        assertEquals(stored.getStatus(), stored.getAuditTrail().get(1).toStatus());
    }

    @Test
    @DisplayName("ten routings of one change at once file exactly one approval request")
    void tenRoutingsFileOneApproval() {
        ChangeRequest change = assessed();
        int before = approvals.created.get();

        List<String> results = outcomes(Flux.range(0, 10)
                .flatMap(i -> route.execute(change.getId(), change.getOrganisationId(), EXECUTOR).thenReturn("OK").onErrorResume(e -> Mono.just(e.getClass().getSimpleName())), 10));

        assertEquals(1, results.stream().filter("OK"::equals).count(), results.toString());
        assertEquals(1, approvals.created.get() - before, "only one approval request may be filed for one change");
        ChangeRequest stored = repository.findById(change.getId(), change.getOrganisationId()).block();
        assertEquals(ChangeStatus.CAB_REVIEW, stored.getStatus());
    }

    @Test
    @DisplayName("ten schedulings of one approved change at once reserve exactly one window")
    void tenSchedulingsReserveOneWindow() {
        ChangeRequest change = assessed();
        var routed = change.routeForApproval(UUID.randomUUID(), EXECUTOR);
        repository.updateRouting(change.getId(), change.getApprovalRequestId(), change.getStatus(), routed).block();
        var approved = change.approve(EXECUTOR);
        repository.updateStatus(change.getId(), change.getStatus(), approved).block();
        int before = windows.reserved.get();
        Instant start = Instant.now().plusSeconds(86400);
        ScheduleChangeRequestRequest request = new ScheduleChangeRequestRequest(start, start.plusSeconds(3600), null);

        List<String> results = outcomes(Flux.range(0, 10)
                .flatMap(i -> schedule.execute(change.getId(), change.getOrganisationId(), request, EXECUTOR, "ADMIN").thenReturn("OK").onErrorResume(e -> Mono.just(e.getClass().getSimpleName())), 10));

        assertEquals(1, results.stream().filter("OK"::equals).count(), results.toString());
        assertEquals(1, windows.reserved.get() - before, "only one window may be reserved for one change");
        assertEquals(ChangeStatus.SCHEDULED, repository.findById(change.getId(), change.getOrganisationId()).block().getStatus());
        assertTrue(true);
    }
}
