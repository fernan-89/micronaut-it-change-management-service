package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** DTO for reserving the implementation window (BIAN Behavior Qualifier: {@code schedule}). */
@Serdeable
public record ScheduleChangeRequestRequest(
        @NotNull(message = "Planned start is required")
        Instant plannedStart,

        @NotNull(message = "Planned end is required")
        Instant plannedEnd
) {}
