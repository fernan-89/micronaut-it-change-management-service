package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * DTO for reserving the implementation window (BIAN Behavior Qualifier: {@code schedule}).
 * {@code freezeOverrideJustification} (ADR-034): EMERGENCY changes only - asks operation-window-service to reserve the
 * window over an active CHANGE_FREEZE.
 */
@Serdeable
public record ScheduleChangeRequestRequest(
        @NotNull(message = "Planned start is required")
        Instant plannedStart,

        @NotNull(message = "Planned end is required")
        Instant plannedEnd,

        @Size(max = 500, message = "Freeze override justification must not exceed 500 characters")
        String freezeOverrideJustification
) {

    /** The shape without an override: reserve the window normally (a CHANGE_FREEZE then blocks it). */
    public ScheduleChangeRequestRequest(Instant plannedStart, Instant plannedEnd) {
        this(plannedStart, plannedEnd, null);
    }
}
