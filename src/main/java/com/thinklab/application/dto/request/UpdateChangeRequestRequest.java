package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

/** DTO for ChangeRequest Update (BIAN Behavior Qualifier: {@code update}). */
@Serdeable
public record UpdateChangeRequestRequest(
        @NotBlank(message = "Title is required")
        String title,

        String description
) {}
