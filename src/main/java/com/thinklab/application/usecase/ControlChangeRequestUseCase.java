package com.thinklab.application.usecase;

import com.thinklab.domain.exception.ChangeRequestNotFoundException;
import com.thinklab.domain.model.ChangeRequest;
import com.thinklab.domain.model.ChangeRequest.ChangeRequestAuditEntry;
import com.thinklab.domain.repository.ChangeRequestRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Use Case governing every ChangeRequest transition that changes status alone - no extra data, no
 * outbound call (route-for-approval/schedule/approval-capture are their own use cases: each also
 * talks to another Service Domain). Mirrors {@code ControlWorkOrderUseCase}'s dispatch-by-enum shape.
 */
@Singleton
public class ControlChangeRequestUseCase {

    private static final Logger log = LoggerFactory.getLogger(ControlChangeRequestUseCase.class);

    private final ChangeRequestRepository changeRequestRepository;

    public ControlChangeRequestUseCase(ChangeRequestRepository changeRequestRepository) {
        this.changeRequestRepository = changeRequestRepository;
    }

    public Mono<Void> execute(UUID id, Action action, String executor) {
        log.info("[USE CASE] Controlling ChangeRequest lifecycle: {} for ID: {}", action, id);

        return changeRequestRepository.findById(id)
                .switchIfEmpty(Mono.error(new ChangeRequestNotFoundException(id)))
                .flatMap(changeRequest -> {
                    ChangeRequestAuditEntry entry = action.apply(changeRequest, executor);
                    return changeRequestRepository.updateStatus(id, changeRequest.getStatus(), entry);
                });
    }

    public enum Action {
        SUBMIT {
            @Override ChangeRequestAuditEntry apply(ChangeRequest changeRequest, String executor) { return changeRequest.submit(executor); }
        },
        START {
            @Override ChangeRequestAuditEntry apply(ChangeRequest changeRequest, String executor) { return changeRequest.start(executor); }
        },
        CANCEL {
            @Override ChangeRequestAuditEntry apply(ChangeRequest changeRequest, String executor) { return changeRequest.cancel(executor); }
        };

        abstract ChangeRequestAuditEntry apply(ChangeRequest changeRequest, String executor);
    }
}
