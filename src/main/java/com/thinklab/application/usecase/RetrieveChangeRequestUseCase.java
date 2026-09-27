package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.ChangeRequestResponse;
import com.thinklab.application.mapper.ChangeRequestMapper;
import com.thinklab.domain.exception.ChangeRequestNotFoundException;
import com.thinklab.domain.repository.ChangeRequestRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for retrieving a single ChangeRequest (BIAN Behavior Qualifier: {@code retrieve}). */
@Singleton
public class RetrieveChangeRequestUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveChangeRequestUseCase.class);

    private final ChangeRequestRepository changeRequestRepository;

    public RetrieveChangeRequestUseCase(ChangeRequestRepository changeRequestRepository) {
        this.changeRequestRepository = changeRequestRepository;
    }

    public Mono<ChangeRequestResponse> execute(UUID id) {
        log.info("[USE CASE] Retrieving ChangeRequest by ID: {}", id);

        return changeRequestRepository.findById(id)
                .switchIfEmpty(Mono.error(new ChangeRequestNotFoundException(id)))
                .map(ChangeRequestMapper::toResponse);
    }
}
