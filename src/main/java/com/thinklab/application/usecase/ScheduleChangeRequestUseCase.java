package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.ScheduleChangeRequestRequest;
import com.thinklab.domain.exception.ChangeRequestNotFoundException;
import com.thinklab.domain.port.OperationWindowServicePort;
import com.thinklab.domain.repository.ChangeRequestRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Use Case for reserving a ChangeRequest's implementation window (BIAN Behavior Qualifier:
 * {@code schedule}). Reuses operation-window-service's existing collision detection wholesale
 * (ADR-032/ADR-033 of this service) - a colliding reservation (including an overlapping
 * {@code CHANGE_FREEZE}) surfaces as this service's own {@code ERR-CHG-00409}.
 */
@Singleton
public class ScheduleChangeRequestUseCase {

    private static final Logger log = LoggerFactory.getLogger(ScheduleChangeRequestUseCase.class);

    private final ChangeRequestRepository changeRequestRepository;
    private final OperationWindowServicePort operationWindowServicePort;

    public ScheduleChangeRequestUseCase(ChangeRequestRepository changeRequestRepository, OperationWindowServicePort operationWindowServicePort) {
        this.changeRequestRepository = changeRequestRepository;
        this.operationWindowServicePort = operationWindowServicePort;
    }

    public Mono<Void> execute(UUID id, ScheduleChangeRequestRequest request, String executor) {
        log.info("[USE CASE] Scheduling ChangeRequest ID: {} from {} to {}", id, request.plannedStart(), request.plannedEnd());

        return changeRequestRepository.findById(id)
                .switchIfEmpty(Mono.error(new ChangeRequestNotFoundException(id)))
                .flatMap(changeRequest -> {
                    // Before anything is reserved remotely: an override on a non-EMERGENCY or non-APPROVED change must not leak a window.
                    changeRequest.validateFreezeOverride(request.freezeOverrideJustification());
                    return operationWindowServicePort.reserveImplementationWindow(
                                    changeRequest.getOrganisationId(), changeRequest.getTitle(), changeRequest.getTargetAssetIds(),
                                    request.plannedStart(), request.plannedEnd(), executor, request.freezeOverrideJustification())
                            .flatMap(operationWindowId -> {
                                var entry = changeRequest.schedule(operationWindowId, request.plannedStart(), request.plannedEnd(),
                                        executor, request.freezeOverrideJustification());
                                return changeRequestRepository.updateScheduling(
                                        id, operationWindowId, request.plannedStart(), request.plannedEnd(), changeRequest.getStatus(), entry);
                            });
                });
    }
}
