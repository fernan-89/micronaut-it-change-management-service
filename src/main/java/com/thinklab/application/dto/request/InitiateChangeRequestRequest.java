package com.thinklab.application.dto.request;

import com.thinklab.domain.model.ChangeRequest.ChangeType;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

/** DTO for ChangeRequest Creation Request (BIAN Behavior Qualifier: {@code initiate}). */
@Serdeable
public record InitiateChangeRequestRequest(
        @NotNull(message = "Requester ID is required")
        UUID requesterId,

        @NotBlank(message = "Title is required")
        String title,

        String description,

        @NotNull(message = "Change Type is required")
        ChangeType changeType,

        @NotEmpty(message = "At least one target Asset is required")
        Set<UUID> targetAssetIds
) {}
