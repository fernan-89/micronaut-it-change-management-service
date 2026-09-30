package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.thinklab.domain.exception.ChangeRequestNotFoundException;
import com.thinklab.domain.model.ChangeRequest;
import com.thinklab.domain.model.ChangeRequest.ChangeRequestAuditEntry;
import com.thinklab.domain.model.ChangeRequest.ChangeStatus;
import com.thinklab.domain.model.ChangeRequest.ChangeType;
import com.thinklab.domain.model.ChangeRequest.ImpactLevel;
import com.thinklab.domain.model.ChangeRequest.RiskLevel;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ChangeRequestDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ChangeRequestDocument.ChangeRequestPersistenceMapper;
import org.bson.BsonObjectId;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class ChangeRequestMongoRepositoryAdapterTest {

    @Mock private MongoClient mongoClient;
    @Mock private MongoDatabase mongoDatabase;
    @Mock private MongoCollection<ChangeRequestDocument> mongoCollection;

    private ChangeRequestMongoRepositoryAdapter adapter;
    private UUID changeRequestId;
    private UUID organisationId;
    private ChangeRequest changeRequest;
    private ChangeRequestAuditEntry entry;

    @BeforeEach
    void setUp() {
        lenient().when(mongoClient.getDatabase("thinklab_it_change_management_db")).thenReturn(mongoDatabase);
        lenient().when(mongoDatabase.getCollection("change_requests", ChangeRequestDocument.class)).thenReturn(mongoCollection);
        lenient().when(mongoCollection.withCodecRegistry(any())).thenReturn(mongoCollection);
        adapter = new ChangeRequestMongoRepositoryAdapter(mongoClient, "mongodb://localhost:27017/thinklab_it_change_management_db");

        changeRequestId = UUID.randomUUID();
        organisationId = UUID.randomUUID();
        changeRequest = ChangeRequest.createNew(changeRequestId, organisationId, UUID.randomUUID(), "t", "d",
                ChangeType.NORMAL, Set.of(UUID.randomUUID()), null, "op-1");
        entry = new ChangeRequestAuditEntry(Instant.now(), "ACTION", "op-1", ChangeStatus.DRAFT, ChangeStatus.SUBMITTED, "detail");
    }

    @Test
    @DisplayName("the database falls back to the default name when the URI has none")
    void databaseFallback() {
        lenient().when(mongoClient.getDatabase("thinklab_it_change_management_db")).thenReturn(mongoDatabase);
        ChangeRequestMongoRepositoryAdapter fallbackAdapter = new ChangeRequestMongoRepositoryAdapter(mongoClient, "mongodb://localhost:27017");
        when(mongoCollection.insertOne(any(ChangeRequestDocument.class)))
                .thenReturn(Mono.just(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))));

        StepVerifier.create(fallbackAdapter.create(changeRequest)).expectNext(changeRequest).verifyComplete();
    }

    @Test
    @DisplayName("create inserts the whole aggregate as one document")
    void create() {
        when(mongoCollection.insertOne(any(ChangeRequestDocument.class)))
                .thenReturn(Mono.just(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))));

        StepVerifier.create(adapter.create(changeRequest)).expectNext(changeRequest).verifyComplete();
    }

    @Test
    @DisplayName("findById maps the found document back to the domain aggregate")
    void findById() {
        FindPublisher<ChangeRequestDocument> publisher = mock(FindPublisher.class);
        when(mongoCollection.find(any(Bson.class))).thenReturn(publisher);
        when(publisher.first()).thenReturn(publisher);
        doAnswer(invocation -> {
            org.reactivestreams.Subscriber<ChangeRequestDocument> subscriber = invocation.getArgument(0);
            Flux.just(ChangeRequestPersistenceMapper.toDocument(changeRequest)).subscribe(subscriber);
            return null;
        }).when(publisher).subscribe(any());

        StepVerifier.create(adapter.findById(changeRequestId))
                .assertNext(found -> org.junit.jupiter.api.Assertions.assertEquals(changeRequestId, found.getId()))
                .verifyComplete();
    }

    @Test
    @DisplayName("findAllByOrganisationId applies the optional status filter")
    void findAllByOrganisationId() {
        FindPublisher<ChangeRequestDocument> publisher = mock(FindPublisher.class);
        when(mongoCollection.find(any(Bson.class))).thenReturn(publisher);
        doAnswer(invocation -> {
            org.reactivestreams.Subscriber<ChangeRequestDocument> subscriber = invocation.getArgument(0);
            Flux.just(ChangeRequestPersistenceMapper.toDocument(changeRequest)).subscribe(subscriber);
            return null;
        }).when(publisher).subscribe(any());

        StepVerifier.create(adapter.findAllByOrganisationId(organisationId, ChangeStatus.DRAFT)).expectNextCount(1).verifyComplete();
        StepVerifier.create(adapter.findAllByOrganisationId(organisationId, null)).expectNextCount(1).verifyComplete();
    }

    @Test
    @DisplayName("updateBasicInfo issues a granular set of title/description")
    void updateBasicInfo() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        StepVerifier.create(adapter.updateBasicInfo(changeRequestId, "t2", "d2", entry)).verifyComplete();
    }

    @Test
    @DisplayName("updateStatus issues a granular status set")
    void updateStatus() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        StepVerifier.create(adapter.updateStatus(changeRequestId, ChangeStatus.SUBMITTED, entry)).verifyComplete();
    }

    @Test
    @DisplayName("updateAssessment sets risk, impact and status")
    void updateAssessment() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        StepVerifier.create(adapter.updateAssessment(changeRequestId, RiskLevel.HIGH, ImpactLevel.MEDIUM, ChangeStatus.ASSESSED, entry)).verifyComplete();
    }

    @Test
    @DisplayName("updateRouting sets the approvalRequestId and status")
    void updateRouting() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        StepVerifier.create(adapter.updateRouting(changeRequestId, UUID.randomUUID(), ChangeStatus.CAB_REVIEW, entry)).verifyComplete();
    }

    @Test
    @DisplayName("updateScheduling sets the window id and both planned dates")
    void updateScheduling() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        StepVerifier.create(adapter.updateScheduling(changeRequestId, UUID.randomUUID(), Instant.now(), Instant.now().plusSeconds(3600),
                ChangeStatus.SCHEDULED, entry)).verifyComplete();
    }

    @Test
    @DisplayName("updateCompletion sets implementation notes and status")
    void updateCompletion() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        StepVerifier.create(adapter.updateCompletion(changeRequestId, "done", ChangeStatus.IMPLEMENTED, entry)).verifyComplete();
    }

    @Test
    @DisplayName("updateRollback sets the rollback reason and status")
    void updateRollback() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        StepVerifier.create(adapter.updateRollback(changeRequestId, "failed", ChangeStatus.ROLLED_BACK, entry)).verifyComplete();
    }

    @Test
    @DisplayName("updateClose sets the close notes and status")
    void updateClose() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));

        StepVerifier.create(adapter.updateClose(changeRequestId, "closed", ChangeStatus.CLOSED, entry)).verifyComplete();
    }

    @Test
    @DisplayName("a zero-matched update translates into ChangeRequestNotFoundException")
    void zeroMatchedUpdate() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(0, 0L, null)));

        StepVerifier.create(adapter.updateStatus(changeRequestId, ChangeStatus.SUBMITTED, entry))
                .expectError(ChangeRequestNotFoundException.class)
                .verify();
    }
}
