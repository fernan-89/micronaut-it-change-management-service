package com.thinklab.domain.port;

import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Outbound Port for the operation-window Service Domain (ADR-032: synchronous HTTP integration, not
 * events). Reuses operation-window's existing collision detection wholesale - this service never
 * duplicates the {@code overlapsInTime}/{@code sharedAssetsWith} logic; a colliding reservation
 * (including with an existing {@code CHANGE_FREEZE} window) surfaces as a
 * {@link com.thinklab.domain.exception.InvalidChangeRequestStatusException} (ADR-032/ADR-033).
 */
public interface OperationWindowServicePort {

    /**
     * BIAN Behavior Qualifier {@code initiate} on operation-window-service. Reserves the change's
     * implementation window (the adapter's own choice of window type - a caller-visible concern this
     * port does not expose) and returns its sovereign id.
     */
    Mono<UUID> reserveImplementationWindow(UUID organisationId, String title, Set<UUID> targetAssetIds,
                                            Instant startAt, Instant endAt, String executor);
}
