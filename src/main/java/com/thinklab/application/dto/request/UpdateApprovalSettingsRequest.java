package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** The two ApprovalPolicy ids of workflow-approval that decide this tenant's changes (ADR-039). */
@Serdeable
public record UpdateApprovalSettingsRequest(@NotNull UUID cabPolicyId, @NotNull UUID ecabPolicyId) {
}
