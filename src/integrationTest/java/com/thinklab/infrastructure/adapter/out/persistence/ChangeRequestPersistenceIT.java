package com.thinklab.infrastructure.adapter.out.persistence;

import com.mongodb.client.model.Filters;
import com.mongodb.reactivestreams.client.MongoClient;
import com.thinklab.domain.exception.ChangeRequestNotFoundException;
import com.thinklab.domain.model.ChangeRequest;
import com.thinklab.domain.model.ChangeRequest.ChangeRequestAuditEntry;
import com.thinklab.domain.model.ChangeRequest.ChangeStatus;
import com.thinklab.domain.model.ChangeRequest.ChangeType;
import com.thinklab.domain.model.ChangeRequest.ImpactLevel;
import com.thinklab.domain.model.ChangeRequest.RiskLevel;
import com.thinklab.domain.repository.ChangeRequestRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ChangeRequest aggregate through {@link ChangeRequestRepository} against a real MongoDB: every
 * granular update, tenant-scoped filtering, not-found handling, the database taken from
 * {@code mongodb.uri}, and the compound index
 * {@link com.thinklab.infrastructure.adapter.out.persistence.repository.ChangeManagementIndexInitializer}
 * creates at startup.
 */
@MicronautTest(packages = "com.thinklab", transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChangeRequestPersistenceIT implements TestPropertyProvider {

    private static final String DATABASE = "it_change_management_it";
    private static final String EXECUTOR = "op-1";

    @Override
    public Map<String, String> getProperties() {
        return Map.of("mongodb.uri", MongoContainer.uri(DATABASE));
    }

    @Inject
    ChangeRequestRepository changeRequests;

    @Inject
    MongoClient mongoClient;

    private static ChangeRequest newChangeRequest(UUID organisationId, UUID requesterId) {
        return ChangeRequest.createNew(UUID.randomUUID(), organisationId, requesterId, "Upgrade firmware", "desc",
                ChangeType.NORMAL, Set.of(UUID.randomUUID()), null, EXECUTOR);
    }

    private static ChangeRequestAuditEntry audit(String action, ChangeStatus from, ChangeStatus to) {
        return new ChangeRequestAuditEntry(Instant.now(), action, EXECUTOR, from, to, action + " detail");
    }

    @Test
    @DisplayName("a created ChangeRequest is read back with its INITIATED audit entry")
    void createAndFind() {
        ChangeRequest created = changeRequests.create(newChangeRequest(UUID.randomUUID(), UUID.randomUUID())).block();

        ChangeRequest found = changeRequests.findById(created.getId()).block();

        assertEquals(created.getTitle(), found.getTitle());
        assertEquals(ChangeStatus.DRAFT, found.getStatus());
        assertEquals(1, found.getAuditTrail().size());
        assertNotNull(found.getCreatedAt());
    }

    @Test
    @DisplayName("writes land in the database named by mongodb.uri")
    void usesTheConfiguredDatabase() {
        ChangeRequest created = changeRequests.create(newChangeRequest(UUID.randomUUID(), UUID.randomUUID())).block();

        Document stored = Mono.from(mongoClient.getDatabase(DATABASE).getCollection("change_requests")
                .find(Filters.eq("_id", created.getId())).first()).block();

        assertNotNull(stored, "change request not found in " + DATABASE);
    }

    @Test
    @DisplayName("every granular update is persisted and appends its own audit entry")
    void everyGranularUpdateAppendsToTheLedger() {
        ChangeRequest created = changeRequests.create(newChangeRequest(UUID.randomUUID(), UUID.randomUUID())).block();
        UUID id = created.getId();
        int initial = created.getAuditTrail().size();

        changeRequests.updateBasicInfo(id, "New title", "New desc",
                audit("UPDATED", ChangeStatus.DRAFT, ChangeStatus.DRAFT)).block();
        changeRequests.updateStatus(id, ChangeStatus.SUBMITTED, audit("SUBMITTED", ChangeStatus.DRAFT, ChangeStatus.SUBMITTED)).block();
        changeRequests.updateAssessment(id, RiskLevel.HIGH, ImpactLevel.MEDIUM, ChangeStatus.ASSESSED,
                audit("ASSESSED", ChangeStatus.SUBMITTED, ChangeStatus.ASSESSED)).block();
        UUID approvalRequestId = UUID.randomUUID();
        changeRequests.updateRouting(id, approvalRequestId, ChangeStatus.CAB_REVIEW,
                audit("ROUTED_FOR_APPROVAL", ChangeStatus.ASSESSED, ChangeStatus.CAB_REVIEW)).block();
        changeRequests.updateStatus(id, ChangeStatus.APPROVED, audit("APPROVED", ChangeStatus.CAB_REVIEW, ChangeStatus.APPROVED)).block();
        UUID windowId = UUID.randomUUID();
        Instant start = Instant.now();
        Instant end = start.plusSeconds(3600);
        changeRequests.updateScheduling(id, windowId, start, end, ChangeStatus.SCHEDULED,
                audit("SCHEDULED", ChangeStatus.APPROVED, ChangeStatus.SCHEDULED)).block();
        changeRequests.updateStatus(id, ChangeStatus.IN_PROGRESS, audit("IMPLEMENTATION_STARTED", ChangeStatus.SCHEDULED, ChangeStatus.IN_PROGRESS)).block();
        changeRequests.updateCompletion(id, "done", ChangeStatus.IMPLEMENTED,
                audit("IMPLEMENTED", ChangeStatus.IN_PROGRESS, ChangeStatus.IMPLEMENTED)).block();
        changeRequests.updateClose(id, "closed", ChangeStatus.CLOSED, audit("CLOSED", ChangeStatus.IMPLEMENTED, ChangeStatus.CLOSED)).block();

        ChangeRequest found = changeRequests.findById(id).block();
        assertEquals("New title", found.getTitle());
        assertEquals(RiskLevel.HIGH, found.getRiskLevel());
        assertEquals(ImpactLevel.MEDIUM, found.getImpactLevel());
        assertEquals(approvalRequestId, found.getApprovalRequestId());
        assertEquals(windowId, found.getOperationWindowId());
        assertEquals(ChangeStatus.CLOSED, found.getStatus());
        assertEquals("done", found.getImplementationNotes());
        assertEquals("closed", found.getCloseNotes());
        assertEquals(initial + 8, found.getAuditTrail().size());
    }

    @Test
    @DisplayName("a rollback records the reason via a dedicated granular update")
    void rollbackUpdate() {
        ChangeRequest created = changeRequests.create(newChangeRequest(UUID.randomUUID(), UUID.randomUUID())).block();

        changeRequests.updateRollback(created.getId(), "failed midway",
                ChangeStatus.ROLLED_BACK, audit("ROLLED_BACK", ChangeStatus.IN_PROGRESS, ChangeStatus.ROLLED_BACK)).block();

        ChangeRequest found = changeRequests.findById(created.getId()).block();
        assertEquals(ChangeStatus.ROLLED_BACK, found.getStatus());
        assertEquals("failed midway", found.getRollbackReason());
    }

    @Test
    @DisplayName("listing is tenant-scoped and honours the optional status filter")
    void listingFilters() {
        UUID organisation = UUID.randomUUID();
        ChangeRequest fromOrg = changeRequests.create(newChangeRequest(organisation, UUID.randomUUID())).block();
        ChangeRequest cancelled = changeRequests.create(newChangeRequest(organisation, UUID.randomUUID())).block();
        changeRequests.create(newChangeRequest(UUID.randomUUID(), UUID.randomUUID())).block();
        changeRequests.updateStatus(cancelled.getId(), ChangeStatus.CANCELLED,
                audit("CANCELLED", ChangeStatus.DRAFT, ChangeStatus.CANCELLED)).block();

        assertEquals(Set.of(fromOrg.getId(), cancelled.getId()), ids(changeRequests.findAllByOrganisationId(organisation, null).collectList().block()));
        assertEquals(Set.of(cancelled.getId()), ids(changeRequests.findAllByOrganisationId(organisation, ChangeStatus.CANCELLED).collectList().block()));
    }

    @Test
    @DisplayName("an unknown ChangeRequest is empty on read and ChangeRequestNotFoundException on update")
    void notFound() {
        UUID unknown = UUID.randomUUID();

        assertNull(changeRequests.findById(unknown).block());
        assertThrows(ChangeRequestNotFoundException.class, () -> changeRequests.updateStatus(unknown, ChangeStatus.CANCELLED,
                audit("CANCELLED", ChangeStatus.DRAFT, ChangeStatus.CANCELLED)).block());
    }

    @Test
    @DisplayName("the compound (organisationId, status) index exists")
    void indexExists() {
        changeRequests.create(newChangeRequest(UUID.randomUUID(), UUID.randomUUID())).block();

        List<Document> indexes = Flux.from(mongoClient.getDatabase(DATABASE).getCollection("change_requests").listIndexes()).collectList().block();

        assertTrue(indexes.stream().anyMatch(index -> new Document("organisationId", 1).append("status", 1)
                .equals(index.get("key", Document.class))), () -> "change_requests: " + indexes);
    }

    private static Set<UUID> ids(List<ChangeRequest> list) {
        return list.stream().map(ChangeRequest::getId).collect(Collectors.toSet());
    }
}
