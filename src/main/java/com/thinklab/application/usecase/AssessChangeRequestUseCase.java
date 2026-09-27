package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.AssessChangeRequestRequest;
import com.thinklab.domain.exception.ChangeRequestNotFoundException;
import com.thinklab.domain.repository.ChangeRequestRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for ChangeRequest risk/impact assessment (BIAN Behavior Qualifier: {@code assess}). */
@Singleton
public class AssessChangeRequestUseCase {

    private static final Logger log = LoggerFactory.getLogger(AssessChangeRequestUseCase.class);

    private final ChangeRequestRepository changeRequestRepository;

    public AssessChangeRequestUseCase(ChangeRequestRepository changeRequestRepository) {
        this.changeRequestRepository = changeRequestRepository;
    }

    public Mono<Void> execute(UUID id, AssessChangeRequestRequest request, String executor) {
        log.info("[USE CASE] Assessing ChangeRequest ID: {} - risk: {}, impact: {}", id, request.riskLevel(), request.impactLevel());

        return changeRequestRepository.findById(id)
                .switchIfEmpty(Mono.error(new ChangeRequestNotFoundException(id)))
                .flatMap(changeRequest -> {
                    var entry = changeRequest.assess(request.riskLevel(), request.impactLevel(), executor);
                    return changeRequestRepository.updateAssessment(id, request.riskLevel(), request.impactLevel(), changeRequest.getStatus(), entry);
                });
    }
}
