package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.thinklab.domain.model.ApprovalSettings;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ApprovalSettingsDocument;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class ApprovalSettingsMongoRepositoryAdapterTest {

    @Mock private MongoClient mongoClient;
    @Mock private MongoDatabase mongoDatabase;
    @Mock private MongoCollection<ApprovalSettingsDocument> collection;
    @Mock private FindPublisher<ApprovalSettingsDocument> find;

    private ApprovalSettingsMongoRepositoryAdapter adapter;
    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(mongoClient.getDatabase("thinklab_it_change_management_db")).thenReturn(mongoDatabase);
        lenient().when(mongoDatabase.getCollection("approval_settings", ApprovalSettingsDocument.class)).thenReturn(collection);
        lenient().when(collection.withCodecRegistry(any())).thenReturn(collection);
        adapter = new ApprovalSettingsMongoRepositoryAdapter(mongoClient, "mongodb://localhost:27017/thinklab_it_change_management_db");
    }

    @Test
    @DisplayName("the database falls back to the default name when the URI has none")
    void databaseFallback() {
        when(mongoClient.getDatabase(ChangeRequestMongoRepositoryAdapter.DEFAULT_DATABASE)).thenReturn(mongoDatabase);
        ApprovalSettingsMongoRepositoryAdapter fallback = new ApprovalSettingsMongoRepositoryAdapter(mongoClient, "mongodb://localhost:27017");
        when(collection.find(any(Bson.class))).thenReturn(find);
        when(find.first()).thenReturn(Mono.empty());

        StepVerifier.create(fallback.findByOrganisationId(tenant)).verifyComplete();
    }

    @Test
    @DisplayName("findByOrganisationId looks the tenant's own document up by id and maps it, or completes empty")
    void find() {
        ApprovalSettings settings = new ApprovalSettings(tenant, UUID.randomUUID(), UUID.randomUUID(), Instant.now(), "admin-1");
        when(collection.find(any(Bson.class))).thenReturn(find);
        when(find.first()).thenReturn(Mono.just(ApprovalSettingsDocument.from(settings)), Mono.empty());

        StepVerifier.create(adapter.findByOrganisationId(tenant)).expectNext(settings).verifyComplete();
        StepVerifier.create(adapter.findByOrganisationId(tenant)).verifyComplete();
        assertThrows(NullPointerException.class, () -> adapter.findByOrganisationId(null));
    }

    @Test
    @DisplayName("save replaces the tenant's document, creating it when there is none, and answers what was stored")
    void save() {
        ApprovalSettings settings = new ApprovalSettings(tenant, UUID.randomUUID(), UUID.randomUUID(), Instant.now(), "admin-1");
        when(collection.replaceOne(any(Bson.class), any(ApprovalSettingsDocument.class), any(ReplaceOptions.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(0, 0L, null)));

        StepVerifier.create(adapter.save(settings)).expectNext(settings).verifyComplete();

        ArgumentCaptor<ReplaceOptions> options = ArgumentCaptor.forClass(ReplaceOptions.class);
        ArgumentCaptor<ApprovalSettingsDocument> document = ArgumentCaptor.forClass(ApprovalSettingsDocument.class);
        verify(collection).replaceOne(any(Bson.class), document.capture(), options.capture());
        assertTrue(options.getValue().isUpsert());
        assertEquals(tenant, document.getValue().getId());
        assertEquals(settings, document.getValue().toDomain());
        assertThrows(NullPointerException.class, () -> adapter.save(null));
    }

    @Test
    @DisplayName("the document keeps every field through its setters")
    void documentRoundTrip() {
        ApprovalSettingsDocument document = new ApprovalSettingsDocument();
        UUID cab = UUID.randomUUID();
        UUID ecab = UUID.randomUUID();
        Instant now = Instant.now();
        document.setId(tenant);
        document.setCabPolicyId(cab);
        document.setEcabPolicyId(ecab);
        document.setUpdatedAt(now);
        document.setUpdatedBy("admin-1");

        assertEquals(new ApprovalSettings(tenant, cab, ecab, now, "admin-1"), document.toDomain());
        assertEquals(cab, document.getCabPolicyId());
        assertEquals(ecab, document.getEcabPolicyId());
        assertEquals(now, document.getUpdatedAt());
        assertEquals("admin-1", document.getUpdatedBy());
    }
}
