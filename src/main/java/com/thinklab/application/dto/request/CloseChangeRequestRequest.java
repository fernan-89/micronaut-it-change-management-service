package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;

/** DTO for closing a change (BIAN Behavior Qualifier: {@code control/close}). Notes are optional. */
@Serdeable
public record CloseChangeRequestRequest(
        String closeNotes
) {}
