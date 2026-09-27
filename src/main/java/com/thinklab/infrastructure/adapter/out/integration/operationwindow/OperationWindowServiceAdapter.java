package com.thinklab.infrastructure.adapter.out.integration.operationwindow;

import com.thinklab.domain.exception.SchedulingConflictException;
import com.thinklab.domain.port.OperationWindowServicePort;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Outbound Adapter for the operation-window Service Domain. Implements the Domain Port, ensuring
 * that Micronaut-specific HTTP client details do not leak into the Application or Domain layers.
 *
 * <p><b>Reuses collision detection wholesale (ADR-032):</b> this adapter never re-implements
 * operation-window's {@code overlapsInTime}/{@code sharedAssetsWith} checks; a 409 response is
 * translated into this service's own {@link SchedulingConflictException} and nothing more.
 *
 * <p>Every ChangeRequest's implementation window is reserved as a {@code DEPLOYMENT}-type window -
 * an implementation detail of this adapter, not something the domain port needs to know.
 */
@Singleton
public class OperationWindowServiceAdapter implements OperationWindowServicePort {

    private static final Logger log = LoggerFactory.getLogger(OperationWindowServiceAdapter.class);
    private static final String WINDOW_TYPE = "DEPLOYMENT";

    private final OperationWindowApiClient apiClient;

    public OperationWindowServiceAdapter(OperationWindowApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public Mono<UUID> reserveImplementationWindow(UUID organisationId, String title, Set<UUID> targetAssetIds,
                                                    Instant startAt, Instant endAt, String executor) {
        log.debug("[INTEGRATION] Reserving implementation window on operation-window-service: '{}'", title);

        return apiClient.initiate(organisationId.toString(), executor,
                        new InitiateOperationWindowApiRequest(title, WINDOW_TYPE, targetAssetIds, startAt, endAt, null))
                .map(OperationWindowApiResponse::id)
                .doOnError(error -> log.error("[INTEGRATION FAILURE] Failed to reserve implementation window: '{}'", title, error))
                .onErrorMap(this::translate);
    }

    private Throwable translate(Throwable error) {
        if (error instanceof HttpClientResponseException httpError) {
            if (httpError.getStatus() == HttpStatus.CONFLICT) {
                return new SchedulingConflictException(
                        "The requested implementation window collides with an existing reservation on operation-window-service.", httpError);
            }
            return new IllegalStateException(
                    "Dependency Failure: Operation Window Service rejected the request (" + httpError.getStatus() + ").", httpError);
        }
        return new IllegalStateException("Dependency Failure: Operation Window Service is currently unavailable", error);
    }

    @Serdeable
    @Introspected
    record InitiateOperationWindowApiRequest(String title, String windowType, Set<UUID> targetAssetIds,
                                              Instant startAt, Instant endAt, @Nullable UUID maintenanceTicketId) {}

    @Serdeable
    @Introspected
    record OperationWindowApiResponse(UUID id) {}
}

/**
 * Declarative Micronaut HTTP Client for the operation-window Service Domain. Package-private
 * visibility strictly encapsulates this integration detail within the adapter. The 'id' maps to the
 * configuration in application.yml for dynamic resolution.
 */
@Client(id = "operation-window-service", path = "/it-operation-window/v1")
interface OperationWindowApiClient {

    @Post("/initiate")
    Mono<OperationWindowServiceAdapter.OperationWindowApiResponse> initiate(
            @Header("X-Tenant-Id") String tenantId,
            @Header("X-Executor") String executor,
            @Body OperationWindowServiceAdapter.InitiateOperationWindowApiRequest request
    );
}
