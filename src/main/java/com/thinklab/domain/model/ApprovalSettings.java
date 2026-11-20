package com.thinklab.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The CAB and ECAB ApprovalPolicy of one tenant (ADR-039): which two policies of workflow-approval decide its changes. The policies
 * themselves belong to workflow-approval; this only remembers which are the tenant's, and who changed that last.
 */
public record ApprovalSettings(UUID organisationId, UUID cabPolicyId, UUID ecabPolicyId, Instant updatedAt, String updatedBy) {

    public ApprovalSettings {
        Objects.requireNonNull(organisationId, "Domain Invariant Violation: organisationId cannot be null.");
        Objects.requireNonNull(cabPolicyId, "Domain Invariant Violation: cabPolicyId cannot be null.");
        Objects.requireNonNull(ecabPolicyId, "Domain Invariant Violation: ecabPolicyId cannot be null.");
        Objects.requireNonNull(updatedAt, "Domain Invariant Violation: updatedAt cannot be null.");
        Objects.requireNonNull(updatedBy, "Domain Invariant Violation: updatedBy cannot be null.");
    }
}
