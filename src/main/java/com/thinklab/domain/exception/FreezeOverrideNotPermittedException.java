package com.thinklab.domain.exception;

/**
 * Domain Exception: the caller's role may not waive a CHANGE_FREEZE (ADR-035). The change itself is fine and can still be
 * scheduled without the override.
 *
 * <p>RFC 7807 mapping: HTTP 403 Forbidden.
 */
public class FreezeOverrideNotPermittedException extends BusinessException {

    public FreezeOverrideNotPermittedException(String role) {
        super("ERR-CHG-00403", "The role " + role + " may not override a CHANGE_FREEZE: that needs ADMIN. The change can still be scheduled without the override.");
    }
}
