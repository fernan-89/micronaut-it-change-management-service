package com.thinklab.domain.repository;

import com.thinklab.domain.model.ApprovalSettings;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Output port for the approval settings of a tenant (ADR-039): at most one record per organisation. */
public interface ApprovalSettingsRepository {

    Mono<ApprovalSettings> findByOrganisationId(UUID organisationId);

    /** Creates or replaces the tenant's record. */
    Mono<ApprovalSettings> save(ApprovalSettings settings);
}
