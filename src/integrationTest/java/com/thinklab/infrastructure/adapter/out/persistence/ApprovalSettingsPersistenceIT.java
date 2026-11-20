package com.thinklab.infrastructure.adapter.out.persistence;

import com.thinklab.domain.model.ApprovalSettings;
import com.thinklab.domain.repository.ApprovalSettingsRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The approval settings of a tenant (ADR-039) against a real MongoDB: one record per organisation, replaced whole, never visible to another tenant. */
@MicronautTest(packages = "com.thinklab", transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApprovalSettingsPersistenceIT implements TestPropertyProvider {

    private static final String DATABASE = "it_change_management_settings_it";

    @Override
    public Map<String, String> getProperties() {
        return Map.of("mongodb.uri", MongoContainer.uri(DATABASE));
    }

    @Inject
    ApprovalSettingsRepository settings;

    @Test
    @DisplayName("save creates the tenant's record, a second save replaces it, and another tenant finds nothing")
    void saveFindReplace() {
        UUID tenant = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        ApprovalSettings first = new ApprovalSettings(tenant, UUID.randomUUID(), UUID.randomUUID(), now, "admin-1");
        ApprovalSettings second = new ApprovalSettings(tenant, UUID.randomUUID(), UUID.randomUUID(), now.plusSeconds(60), "admin-2");

        assertNull(settings.findByOrganisationId(tenant).block());
        assertEquals(first, settings.save(first).block());
        assertEquals(first, settings.findByOrganisationId(tenant).block());

        settings.save(second).block();
        assertEquals(second, settings.findByOrganisationId(tenant).block());
        assertNull(settings.findByOrganisationId(UUID.randomUUID()).block(), "another tenant's settings are the same as none");
    }
}
