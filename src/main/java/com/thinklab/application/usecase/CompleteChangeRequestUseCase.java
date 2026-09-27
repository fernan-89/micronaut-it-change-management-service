package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.CompleteChangeRequestRequest;
import com.thinklab.domain.exception.ChangeRequestNotFoundException;
import com.thinklab.domain.repository.ChangeRequestRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for recording implementation completion (BIAN Behavior Qualifier: {@code complete}). */
@Singleton
public class CompleteChangeRequestUseCase {

    private static final Logger log = LoggerFactory.getLogger(CompleteChangeRequestUseCase.class);

    private final ChangeRequestRepository changeRequestRepository;

    public CompleteChangeRequestUseCase(ChangeRequestRepository changeRequestRepository) {
        this.changeRequestRepository = changeRequestRepository;
    }

    public Mono<Void> execute(UUID id, CompleteChangeRequestRequest request, String executor) {
        log.info("[USE CASE] Completing implementation for ChangeRequest ID: {}", id);

        return changeRequestRepository.findById(id)
                .switchIfEmpty(Mono.error(new ChangeRequestNotFoundException(id)))
                .flatMap(changeRequest -> {
                    var entry = changeRequest.complete(request.implementationNotes(), executor);
                    return changeRequestRepository.updateCompletion(id, request.implementationNotes(), changeRequest.getStatus(), entry);
                });
    }
}
