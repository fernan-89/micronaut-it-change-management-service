package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.ChangeRequestAuditEntryResponse;
import com.thinklab.application.mapper.ChangeRequestMapper;
import com.thinklab.domain.exception.ChangeRequestNotFoundException;
import com.thinklab.domain.repository.ChangeRequestRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Projects the immutable forensic ledger of a ChangeRequest (BIAN Behavior Qualifier: {@code audit-log/retrieve}).
 *
 * <p>Returns {@code Mono<List<...>>}, not {@code Flux<...>}: a controller method returning a bare
 * {@code Flux} is streamed rather than collected by Micronaut, which both bypasses the RFC 7807
 * exception handlers and can reorder the emitted elements under JSON streaming serialization (found
 * live on {@code it-hardware-maintenance}'s own audit-log endpoint, Journey 6). Collecting into a
 * list first sidesteps both problems.
 */
@Singleton
public class RetrieveChangeRequestAuditLogUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveChangeRequestAuditLogUseCase.class);

    private final ChangeRequestRepository changeRequestRepository;

    public RetrieveChangeRequestAuditLogUseCase(ChangeRequestRepository changeRequestRepository) {
        this.changeRequestRepository = changeRequestRepository;
    }

    public Mono<List<ChangeRequestAuditEntryResponse>> execute(UUID id) {
        log.info("[USE CASE] Retrieving audit ledger for ChangeRequest ID: {}", id);

        return changeRequestRepository.findById(id)
                .switchIfEmpty(Mono.error(new ChangeRequestNotFoundException(id)))
                .map(changeRequest -> changeRequest.getAuditTrail().stream().map(ChangeRequestMapper::toResponse).collect(Collectors.toList()));
    }
}
