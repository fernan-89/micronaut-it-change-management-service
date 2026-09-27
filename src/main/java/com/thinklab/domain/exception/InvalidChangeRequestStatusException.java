package com.thinklab.domain.exception;

/**
 * Domain Exception: Indicates an illegal lifecycle transition on a
 * {@link com.thinklab.domain.model.ChangeRequest} (e.g. scheduling a change that isn't {@code APPROVED},
 * capturing a decision when no approval is in progress, or mutating a terminal change), or a scheduling
 * collision surfaced by the Operation Window Service Domain when reserving the change's implementation
 * window (ADR-032).
 *
 * <p>RFC 7807 mapping: HTTP 409 Conflict (AST-03, ADR-019). The request is well formed but collides with
 * the aggregate's current state or a downstream reservation, the same contract used for every other
 * state conflict on the platform.
 */
public class InvalidChangeRequestStatusException extends BusinessException {

    private static final String ERROR_CODE = "ERR-CHG-00409";

    public InvalidChangeRequestStatusException(String message) {
        super(ERROR_CODE, message);
    }
}
