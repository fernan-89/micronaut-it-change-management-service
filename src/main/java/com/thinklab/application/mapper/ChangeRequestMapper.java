package com.thinklab.application.mapper;

import com.thinklab.application.dto.request.InitiateChangeRequestRequest;
import com.thinklab.application.dto.response.ChangeRequestAuditEntryResponse;
import com.thinklab.application.dto.response.ChangeRequestResponse;
import com.thinklab.domain.model.ChangeRequest;
import com.thinklab.domain.model.ChangeRequest.ChangeRequestAuditEntry;

import java.util.UUID;

/** Static factory mapper for ChangeRequest DTOs and the Domain aggregate. */
public final class ChangeRequestMapper {

    private ChangeRequestMapper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static ChangeRequest toDomain(InitiateChangeRequestRequest request, UUID sovereignId, UUID organisationId, String executor) {
        return ChangeRequest.createNew(sovereignId, organisationId, request.requesterId(), request.title(),
                request.description(), request.changeType(), request.targetAssetIds(), executor);
    }

    public static ChangeRequestResponse toResponse(ChangeRequest changeRequest) {
        return new ChangeRequestResponse(
                changeRequest.getId(),
                changeRequest.getOrganisationId(),
                changeRequest.getRequesterId(),
                changeRequest.getTitle(),
                changeRequest.getDescription(),
                changeRequest.getChangeType().name(),
                changeRequest.getTargetAssetIds(),
                changeRequest.getRiskLevel() != null ? changeRequest.getRiskLevel().name() : null,
                changeRequest.getImpactLevel() != null ? changeRequest.getImpactLevel().name() : null,
                changeRequest.getStatus().name(),
                changeRequest.getApprovalRequestId(),
                changeRequest.getOperationWindowId(),
                changeRequest.getPlannedStart(),
                changeRequest.getPlannedEnd(),
                changeRequest.getImplementationNotes(),
                changeRequest.getRollbackReason(),
                changeRequest.getCloseNotes(),
                changeRequest.getCreatedAt(),
                changeRequest.getUpdatedAt()
        );
    }

    public static ChangeRequestAuditEntryResponse toResponse(ChangeRequestAuditEntry entry) {
        return new ChangeRequestAuditEntryResponse(entry.occurredAt(), entry.action(), entry.executor(),
                entry.fromStatus() != null ? entry.fromStatus().name() : null, entry.toStatus().name(), entry.detail());
    }
}
