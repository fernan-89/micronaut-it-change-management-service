package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.ChangeRequest;
import com.thinklab.domain.model.ChangeRequest.ChangeStatus;
import com.thinklab.domain.model.ChangeRequest.ChangeType;
import com.thinklab.domain.model.ChangeRequest.ImpactLevel;
import com.thinklab.domain.model.ChangeRequest.RiskLevel;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ChangeRequestDocument.ChangeRequestPersistenceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeRequestDocumentTest {

    private static final String EXECUTOR = "op-1";

    @Test
    @DisplayName("toDocument/toDomain round-trip preserves the initiating audit entry and a real externalReference")
    void roundTripFreshChangeRequest() {
        UUID assetId = UUID.randomUUID();
        ChangeRequest.ExternalReference externalReference = new ChangeRequest.ExternalReference("Jira", "GMUD-1");
        ChangeRequest cr = ChangeRequest.createNew(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Upgrade firmware", "desc", ChangeType.NORMAL, Set.of(assetId), externalReference, EXECUTOR);

        ChangeRequestDocument document = ChangeRequestPersistenceMapper.toDocument(cr);
        ChangeRequest restored = ChangeRequestPersistenceMapper.toDomain(document);

        assertEquals(cr.getId(), restored.getId());
        assertEquals(cr.getStatus(), restored.getStatus());
        assertEquals(Set.of(assetId), restored.getTargetAssetIds());
        assertEquals(externalReference, restored.getExternalReference());
        assertEquals(1, restored.getAuditTrail().size());
        assertNull(restored.getAuditTrail().get(0).fromStatus());
        assertEquals(ChangeStatus.DRAFT, restored.getAuditTrail().get(0).toStatus());
        assertNull(restored.getRiskLevel());
        assertNull(restored.getApprovalRequestId());
    }

    @Test
    @DisplayName("toDocument/toDomain round-trip preserves risk/impact, routing, scheduling and completion data, and a null externalReference")
    void roundTripFullyDrivenChangeRequest() {
        ChangeRequest cr = ChangeRequest.createNew(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Upgrade firmware", "desc", ChangeType.NORMAL, Set.of(UUID.randomUUID()), null, EXECUTOR);
        cr.submit(EXECUTOR);
        cr.assess(RiskLevel.HIGH, ImpactLevel.MEDIUM, EXECUTOR);
        UUID approvalRequestId = UUID.randomUUID();
        cr.routeForApproval(approvalRequestId, EXECUTOR);
        cr.approve(EXECUTOR);
        UUID windowId = UUID.randomUUID();
        Instant start = Instant.now();
        Instant end = start.plusSeconds(3600);
        cr.schedule(windowId, start, end, EXECUTOR);
        cr.start(EXECUTOR);
        cr.complete("done", EXECUTOR);
        cr.close("closed out", EXECUTOR);

        ChangeRequestDocument document = ChangeRequestPersistenceMapper.toDocument(cr);
        ChangeRequest restored = ChangeRequestPersistenceMapper.toDomain(document);

        assertEquals(ChangeStatus.CLOSED, restored.getStatus());
        assertEquals(RiskLevel.HIGH, restored.getRiskLevel());
        assertEquals(ImpactLevel.MEDIUM, restored.getImpactLevel());
        assertEquals(approvalRequestId, restored.getApprovalRequestId());
        assertEquals(windowId, restored.getOperationWindowId());
        assertEquals(start, restored.getPlannedStart());
        assertEquals(end, restored.getPlannedEnd());
        assertEquals("done", restored.getImplementationNotes());
        assertEquals("closed out", restored.getCloseNotes());
        assertEquals(cr.getAuditTrail().size(), restored.getAuditTrail().size());
        assertNull(restored.getExternalReference());
    }

    @Test
    @DisplayName("toDomain defaults a null status/asset set/auditTrail to their safe empty forms")
    void toDomainNullDefaults() {
        ChangeRequestDocument document = new ChangeRequestDocument();
        document.setId(UUID.randomUUID());
        document.setOrganisationId(UUID.randomUUID());
        document.setRequesterId(UUID.randomUUID());
        document.setTitle("t");
        document.setChangeType(ChangeType.STANDARD.name());
        document.setTargetAssetIds(null);
        document.setStatus(null);
        document.setAuditTrail(null);

        ChangeRequest restored = ChangeRequestPersistenceMapper.toDomain(document);

        assertEquals(ChangeStatus.DRAFT, restored.getStatus());
        assertTrue(restored.getTargetAssetIds().isEmpty());
        assertTrue(restored.getAuditTrail().isEmpty());
    }

    @Test
    @DisplayName("the persistence mapper is a non-instantiable utility class")
    void cannotInstantiate() throws Exception {
        var constructor = ChangeRequestPersistenceMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        var ex = assertThrows(java.lang.reflect.InvocationTargetException.class, constructor::newInstance);
        assertTrue(ex.getCause() instanceof UnsupportedOperationException);
    }
}
