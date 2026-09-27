package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

/** DTO for recording implementation completion (BIAN Behavior Qualifier: {@code complete}). */
@Serdeable
public record CompleteChangeRequestRequest(
        @NotBlank(message = "Implementation notes are required")
        String implementationNotes
) {}
