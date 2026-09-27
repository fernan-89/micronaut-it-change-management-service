package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateChangeRequestRequest;
import com.thinklab.application.dto.response.ChangeRequestResponse;
import com.thinklab.application.mapper.ChangeRequestMapper;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.ChangeRequestRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for creating a new ChangeRequest (BIAN Behavior Qualifier: {@code initiate}). */
@Singleton
public class InitiateChangeRequestUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateChangeRequestUseCase.class);

    private final HashServicePort hashServicePort;
    private final ChangeRequestRepository changeRequestRepository;

    public InitiateChangeRequestUseCase(HashServicePort hashServicePort, ChangeRequestRepository changeRequestRepository) {
        this.hashServicePort = hashServicePort;
        this.changeRequestRepository = changeRequestRepository;
    }

    public Mono<ChangeRequestResponse> execute(UUID organisationId, InitiateChangeRequestRequest request, String executor) {
        log.info("[USE CASE] Initiating ChangeRequest '{}' for organisation: {}", request.title(), organisationId);

        return hashServicePort.generateSovereignId("change-request-creation")
                .map(sovereignId -> ChangeRequestMapper.toDomain(request, sovereignId, organisationId, executor))
                .flatMap(changeRequestRepository::create)
                .map(ChangeRequestMapper::toResponse);
    }
}
