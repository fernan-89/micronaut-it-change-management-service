package com.thinklab.application.mapper;

import com.thinklab.application.dto.request.InitiateChangeRequestRequest;
import com.thinklab.application.dto.response.ChangeRequestResponse;
import com.thinklab.domain.model.ChangeRequest;
import com.thinklab.domain.model.ChangeRequest.ChangeType;
import com.thinklab.domain.model.ChangeRequest.ImpactLevel;
import com.thinklab.domain.model.ChangeRequest.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeRequestMapperTest {

    private static final String EXECUTOR = "op-1";

    @Test
    @DisplayName("toDomain builds a new ChangeRequest from the request, id and organisation, with a real externalReference")
    void toDomain() {
        UUID requesterId = UUID.randomUUID();
        Set<UUID> assets = Set.of(UUID.randomUUID());
        InitiateChangeRequestRequest request = new InitiateChangeRequestRequest(requesterId, "t", "d", ChangeType.NORMAL, assets, "Jira", "GMUD-1");
        UUID id = UUID.randomUUID();
        UUID organisationId = UUID.randomUUID();

        ChangeRequest cr = ChangeRequestMapper.toDomain(request, id, organisationId, EXECUTOR);

        assertEquals(id, cr.getId());
        assertEquals(organisationId, cr.getOrganisationId());
        assertEquals(requesterId, cr.getRequesterId());
        assertEquals("t", cr.getTitle());
        assertEquals(ChangeType.NORMAL, cr.getChangeType());
        assertEquals(assets, cr.getTargetAssetIds());
        assertEquals("Jira", cr.getExternalReference().system());
        assertEquals("GMUD-1", cr.getExternalReference().externalId());
    }

    @Test
    @DisplayName("toDomain leaves externalReference null when either half of the pair is missing")
    void toDomainNoExternalReference() {
        InitiateChangeRequestRequest bothNull = new InitiateChangeRequestRequest(UUID.randomUUID(), "t", "d", ChangeType.NORMAL, Set.of(UUID.randomUUID()), null, null);
        assertNull(ChangeRequestMapper.toDomain(bothNull, UUID.randomUUID(), UUID.randomUUID(), EXECUTOR).getExternalReference());

        InitiateChangeRequestRequest systemOnly = new InitiateChangeRequestRequest(UUID.randomUUID(), "t", "d", ChangeType.NORMAL, Set.of(UUID.randomUUID()), "Jira", null);
        assertNull(ChangeRequestMapper.toDomain(systemOnly, UUID.randomUUID(), UUID.randomUUID(), EXECUTOR).getExternalReference());
    }

    @Test
    @DisplayName("toResponse projects every field, including a null riskLevel/impactLevel/externalReference before assessment")
    void toResponseFreshChangeRequest() {
        ChangeRequest cr = ChangeRequest.createNew(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "t", "d",
                ChangeType.NORMAL, Set.of(UUID.randomUUID()), null, EXECUTOR);

        ChangeRequestResponse response = ChangeRequestMapper.toResponse(cr);

        assertEquals(cr.getId(), response.id());
        assertEquals("DRAFT", response.status());
        assertNull(response.riskLevel());
        assertNull(response.impactLevel());
        assertNull(response.approvalRequestId());
        assertNull(response.externalReference());
    }

    @Test
    @DisplayName("toResponse projects a fully-assessed ChangeRequest's risk/impact as strings and a real externalReference")
    void toResponseAssessedChangeRequest() {
        ChangeRequest cr = ChangeRequest.createNew(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "t", "d",
                ChangeType.NORMAL, Set.of(UUID.randomUUID()), new ChangeRequest.ExternalReference("Jira", "GMUD-2"), EXECUTOR);
        cr.submit(EXECUTOR);
        cr.assess(RiskLevel.HIGH, ImpactLevel.LOW, EXECUTOR);

        ChangeRequestResponse response = ChangeRequestMapper.toResponse(cr);

        assertEquals("HIGH", response.riskLevel());
        assertEquals("LOW", response.impactLevel());
        assertEquals("ASSESSED", response.status());
        assertEquals("Jira", response.externalReference().system());
        assertEquals("GMUD-2", response.externalReference().externalId());
    }

    @Test
    @DisplayName("toResponse (audit entry) preserves a null fromStatus for the initiating entry, and a real one thereafter")
    void toResponseAuditEntry() {
        ChangeRequest cr = ChangeRequest.createNew(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "t", "d",
                ChangeType.NORMAL, Set.of(UUID.randomUUID()), null, EXECUTOR);
        cr.submit(EXECUTOR);

        var initiated = ChangeRequestMapper.toResponse(cr.getAuditTrail().get(0));
        assertNull(initiated.fromStatus());
        assertEquals("DRAFT", initiated.toStatus());

        var submitted = ChangeRequestMapper.toResponse(cr.getAuditTrail().get(1));
        assertEquals("DRAFT", submitted.fromStatus());
        assertEquals("SUBMITTED", submitted.toStatus());
    }

    @Test
    @DisplayName("the mapper is a non-instantiable utility class")
    void utilityClass() throws Exception {
        var constructor = ChangeRequestMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        var ex = assertThrows(java.lang.reflect.InvocationTargetException.class, constructor::newInstance);
        assertTrue(ex.getCause() instanceof UnsupportedOperationException);
    }
}
