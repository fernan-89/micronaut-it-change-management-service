package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

/** DTO for rolling back a failed implementation (BIAN Behavior Qualifier: {@code rollback}). */
@Serdeable
public record RollbackChangeRequestRequest(
        @NotBlank(message = "Reason is required")
        String reason
) {}
