package com.thinklab.domain.exception;

/**
 * Domain Exception: the caller's role may not set the CAB and ECAB policy of the tenant (ADR-039): that needs ADMIN or SERVICE.
 *
 * <p>RFC 7807 mapping: HTTP 403 Forbidden.
 */
public class ApprovalSettingsNotPermittedException extends BusinessException {
    public ApprovalSettingsNotPermittedException(String role) {
        super("ERR-CHG-00403", "The role " + role + " may not change the approval settings of the organisation: that needs ADMIN or SERVICE.");
    }
}
