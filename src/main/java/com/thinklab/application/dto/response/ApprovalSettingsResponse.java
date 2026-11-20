package com.thinklab.application.dto.response;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

@Serdeable
public record ApprovalSettingsResponse(UUID organisationId, UUID cabPolicyId, UUID ecabPolicyId, Instant updatedAt, String updatedBy) {
}
