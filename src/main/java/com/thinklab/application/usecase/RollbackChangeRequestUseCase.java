package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.RollbackChangeRequestRequest;
import com.thinklab.domain.exception.ChangeRequestNotFoundException;
import com.thinklab.domain.repository.ChangeRequestRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for rolling back a failed implementation (BIAN Behavior Qualifier: {@code rollback}). */
@Singleton
public class RollbackChangeRequestUseCase {

    private static final Logger log = LoggerFactory.getLogger(RollbackChangeRequestUseCase.class);

    private final ChangeRequestRepository changeRequestRepository;

    public RollbackChangeRequestUseCase(ChangeRequestRepository changeRequestRepository) {
        this.changeRequestRepository = changeRequestRepository;
    }

    public Mono<Void> execute(UUID id, RollbackChangeRequestRequest request, String executor) {
        log.info("[USE CASE] Rolling back ChangeRequest ID: {}", id);

        return changeRequestRepository.findById(id)
                .switchIfEmpty(Mono.error(new ChangeRequestNotFoundException(id)))
                .flatMap(changeRequest -> {
                    var entry = changeRequest.rollback(request.reason(), executor);
                    return changeRequestRepository.updateRollback(id, request.reason(), changeRequest.getStatus(), entry);
                });
    }
}
