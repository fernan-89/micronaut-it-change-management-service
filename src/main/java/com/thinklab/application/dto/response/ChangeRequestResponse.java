package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Serdeable
public record ChangeRequestResponse(
        UUID id,
        UUID organisationId,
        UUID requesterId,
        String title,
        String description,
        String changeType,
        Set<UUID> targetAssetIds,
        ExternalReferenceResponse externalReference,
        String riskLevel,
        String impactLevel,
        String status,
        UUID approvalRequestId,
        UUID operationWindowId,
        Instant plannedStart,
        Instant plannedEnd,
        String implementationNotes,
        String rollbackReason,
        String closeNotes,
        Instant createdAt,
        Instant updatedAt
) {}
