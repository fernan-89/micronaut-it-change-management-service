package com.thinklab.domain.exception;

/**
 * Domain Exception: the tenant has no CAB/ECAB ApprovalPolicy configured (ADR-039), so a change that needs one cannot be routed. It never
 * falls back to another tenant's policy.
 *
 * <p>RFC 7807 mapping: HTTP 422 Unprocessable Entity.
 */
public class ApprovalPolicyNotConfiguredException extends BusinessException {
    public ApprovalPolicyNotConfiguredException(String committee) {
        super("ERR-CHG-00422", "This organisation has no " + committee + " approval policy configured: set it with PUT /it-change-management/v1/approval-settings/update before routing a change for approval.");
    }
}
