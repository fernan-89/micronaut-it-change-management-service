package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.ChangeRequestResponse;
import com.thinklab.application.mapper.ChangeRequestMapper;
import com.thinklab.domain.model.ChangeRequest.ChangeStatus;
import com.thinklab.domain.repository.ChangeRequestRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.UUID;

/** Use Case for the tenant-scoped ChangeRequest collection (BIAN Behavior Qualifier: {@code retrieve}). */
@Singleton
public class RetrieveChangeRequestsUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveChangeRequestsUseCase.class);

    private final ChangeRequestRepository changeRequestRepository;

    public RetrieveChangeRequestsUseCase(ChangeRequestRepository changeRequestRepository) {
        this.changeRequestRepository = changeRequestRepository;
    }

    public Flux<ChangeRequestResponse> execute(UUID organisationId, ChangeStatus status) {
        log.info("[USE CASE] Retrieving ChangeRequests for organisation: {} status: {}", organisationId, status);

        return changeRequestRepository.findAllByOrganisationId(organisationId, status)
                .map(ChangeRequestMapper::toResponse);
    }
}
