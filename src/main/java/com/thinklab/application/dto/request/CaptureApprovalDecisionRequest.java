package com.thinklab.application.dto.request;

import com.thinklab.domain.port.ApprovalServicePort.DecisionOutcome;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for forwarding one approver's decision (BIAN Behavior Qualifier: {@code approval/capture}).
 * Mirrors workflow-approval-service's own {@code CaptureDecisionRequest} shape byte-for-byte, since
 * this is passed straight through (ADR-032).
 */
@Serdeable
public record CaptureApprovalDecisionRequest(
        @NotNull(message = "Outcome is required")
        DecisionOutcome outcome,

        String comment
) {}
