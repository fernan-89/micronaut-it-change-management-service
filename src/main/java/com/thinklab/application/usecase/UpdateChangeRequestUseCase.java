package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.UpdateChangeRequestRequest;
import com.thinklab.domain.exception.ChangeRequestNotFoundException;
import com.thinklab.domain.repository.ChangeRequestRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for updating a ChangeRequest's basic info (BIAN Behavior Qualifier: {@code update}). */
@Singleton
public class UpdateChangeRequestUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateChangeRequestUseCase.class);

    private final ChangeRequestRepository changeRequestRepository;

    public UpdateChangeRequestUseCase(ChangeRequestRepository changeRequestRepository) {
        this.changeRequestRepository = changeRequestRepository;
    }

    public Mono<Void> execute(UUID id, UpdateChangeRequestRequest request, String executor) {
        log.info("[USE CASE] Updating ChangeRequest ID: {}", id);

        return changeRequestRepository.findById(id)
                .switchIfEmpty(Mono.error(new ChangeRequestNotFoundException(id)))
                .flatMap(changeRequest -> {
                    var entry = changeRequest.updateBasicInfo(request.title(), request.description(), executor);
                    return changeRequestRepository.updateBasicInfo(id, request.title(), request.description(), entry);
                });
    }
}
