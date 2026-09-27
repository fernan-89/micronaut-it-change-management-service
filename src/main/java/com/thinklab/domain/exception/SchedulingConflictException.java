package com.thinklab.domain.exception;

/**
 * Domain Exception: the implementation window requested for a {@link com.thinklab.domain.model.ChangeRequest}
 * collided with an existing reservation on operation-window-service (its own {@code overlapsInTime}/
 * {@code sharedAssetsWith} collision check, reused wholesale rather than duplicated here - ADR-032).
 * A window overlapping an existing {@code CHANGE_FREEZE} for the same assets is exactly this case.
 *
 * <p>RFC 7807 mapping: HTTP 409 Conflict (AST-03, ADR-019), the same {@code ERR-CHG-00409} code as
 * every other ChangeRequest state conflict.
 */
public class SchedulingConflictException extends BusinessException {

    private static final String ERROR_CODE = "ERR-CHG-00409";

    public SchedulingConflictException(String message) {
        super(ERROR_CODE, message);
    }

    public SchedulingConflictException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
