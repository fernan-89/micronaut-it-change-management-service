package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.CloseChangeRequestRequest;
import com.thinklab.domain.exception.ChangeRequestNotFoundException;
import com.thinklab.domain.repository.ChangeRequestRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for closing a ChangeRequest (BIAN Behavior Qualifier: {@code control/close}). Terminal. */
@Singleton
public class CloseChangeRequestUseCase {

    private static final Logger log = LoggerFactory.getLogger(CloseChangeRequestUseCase.class);

    private final ChangeRequestRepository changeRequestRepository;

    public CloseChangeRequestUseCase(ChangeRequestRepository changeRequestRepository) {
        this.changeRequestRepository = changeRequestRepository;
    }

    public Mono<Void> execute(UUID id, CloseChangeRequestRequest request, String executor) {
        log.info("[USE CASE] Closing ChangeRequest ID: {}", id);

        return changeRequestRepository.findById(id)
                .switchIfEmpty(Mono.error(new ChangeRequestNotFoundException(id)))
                .flatMap(changeRequest -> {
                    var entry = changeRequest.close(request.closeNotes(), executor);
                    return changeRequestRepository.updateClose(id, request.closeNotes(), changeRequest.getStatus(), entry);
                });
    }
}
