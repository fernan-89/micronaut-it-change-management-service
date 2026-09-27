package com.thinklab.application.dto.request;

import com.thinklab.domain.model.ChangeRequest.ImpactLevel;
import com.thinklab.domain.model.ChangeRequest.RiskLevel;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

/** DTO for ChangeRequest Risk/Impact Assessment (BIAN Behavior Qualifier: {@code assess}). */
@Serdeable
public record AssessChangeRequestRequest(
        @NotNull(message = "Risk Level is required")
        RiskLevel riskLevel,

        @NotNull(message = "Impact Level is required")
        ImpactLevel impactLevel
) {}
