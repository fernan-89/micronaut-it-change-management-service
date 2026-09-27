package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.thinklab.domain.exception.ChangeRequestNotFoundException;
import com.thinklab.domain.model.ChangeRequest;
import com.thinklab.domain.model.ChangeRequest.ChangeRequestAuditEntry;
import com.thinklab.domain.model.ChangeRequest.ChangeStatus;
import com.thinklab.domain.model.ChangeRequest.ImpactLevel;
import com.thinklab.domain.model.ChangeRequest.RiskLevel;
import com.thinklab.domain.repository.ChangeRequestRepository;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ChangeRequestDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ChangeRequestDocument.AuditEntryDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ChangeRequestDocument.ChangeRequestPersistenceMapper;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * MongoDB Reactive Repository Adapter for the ChangeRequest aggregate, same pattern as
 * {@code WorkOrderMongoRepositoryAdapter}: every transition is a single atomic {@code $set}/{@code $push}
 * that also appends the forensic audit entry, so state and ledger can never diverge.
 */
@Singleton
public class ChangeRequestMongoRepositoryAdapter implements ChangeRequestRepository {

    private static final Logger log = LoggerFactory.getLogger(ChangeRequestMongoRepositoryAdapter.class);
    static final String DEFAULT_DATABASE = "thinklab_it_change_management_db";
    static final String COLLECTION_NAME = "change_requests";
    private static final String FIELD_ID = "_id";
    private static final String FIELD_UPDATED_AT = "updatedAt";
    private static final String FIELD_AUDIT_TRAIL = "auditTrail";

    private static final CodecRegistry POJO_CODEC_REGISTRY = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build())
    );

    private final MongoClient mongoClient;
    private final String database;

    public ChangeRequestMongoRepositoryAdapter(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this.mongoClient = mongoClient;
        String configured = new ConnectionString(Objects.requireNonNull(mongoUri, "mongodb.uri cannot be null.")).getDatabase();
        this.database = configured != null ? configured : DEFAULT_DATABASE;
    }

    private MongoCollection<ChangeRequestDocument> getCollection() {
        return mongoClient.getDatabase(database)
                .getCollection(COLLECTION_NAME, ChangeRequestDocument.class)
                .withCodecRegistry(POJO_CODEC_REGISTRY);
    }

    @Override
    public Mono<ChangeRequest> create(ChangeRequest changeRequest) {
        log.debug("[PERSISTENCE] Monolithic create for ChangeRequest Aggregate: {}", changeRequest.getId());

        ChangeRequestDocument document = ChangeRequestPersistenceMapper.toDocument(changeRequest);
        return Mono.from(getCollection().insertOne(document)).map(result -> changeRequest);
    }

    @Override
    public Mono<ChangeRequest> findById(UUID id) {
        return Mono.from(getCollection().find(Filters.eq(FIELD_ID, id)).first())
                .map(ChangeRequestPersistenceMapper::toDomain);
    }

    @Override
    public Flux<ChangeRequest> findAllByOrganisationId(UUID organisationId, ChangeStatus status) {
        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.eq("organisationId", organisationId));
        if (status != null) {
            filters.add(Filters.eq("status", status.name()));
        }

        return Flux.from(getCollection().find(Filters.and(filters)))
                .map(ChangeRequestPersistenceMapper::toDomain);
    }

    @Override
    public Mono<Void> updateBasicInfo(UUID id, String title, String description, ChangeRequestAuditEntry auditEntry) {
        Bson update = Updates.combine(
                Updates.set("title", title),
                Updates.set("description", description),
                Updates.set(FIELD_UPDATED_AT, Instant.now()),
                Updates.push(FIELD_AUDIT_TRAIL, AuditEntryDocument.fromDomain(auditEntry))
        );
        return executeUpdate(id, update);
    }

    @Override
    public Mono<Void> updateStatus(UUID id, ChangeStatus status, ChangeRequestAuditEntry auditEntry) {
        Bson update = Updates.combine(
                Updates.set("status", status.name()),
                Updates.set(FIELD_UPDATED_AT, Instant.now()),
                Updates.push(FIELD_AUDIT_TRAIL, AuditEntryDocument.fromDomain(auditEntry))
        );
        return executeUpdate(id, update);
    }

    @Override
    public Mono<Void> updateAssessment(UUID id, RiskLevel riskLevel, ImpactLevel impactLevel, ChangeStatus status, ChangeRequestAuditEntry auditEntry) {
        Bson update = Updates.combine(
                Updates.set("riskLevel", riskLevel.name()),
                Updates.set("impactLevel", impactLevel.name()),
                Updates.set("status", status.name()),
                Updates.set(FIELD_UPDATED_AT, Instant.now()),
                Updates.push(FIELD_AUDIT_TRAIL, AuditEntryDocument.fromDomain(auditEntry))
        );
        return executeUpdate(id, update);
    }

    @Override
    public Mono<Void> updateRouting(UUID id, UUID approvalRequestId, ChangeStatus status, ChangeRequestAuditEntry auditEntry) {
        Bson update = Updates.combine(
                Updates.set("approvalRequestId", approvalRequestId),
                Updates.set("status", status.name()),
                Updates.set(FIELD_UPDATED_AT, Instant.now()),
                Updates.push(FIELD_AUDIT_TRAIL, AuditEntryDocument.fromDomain(auditEntry))
        );
        return executeUpdate(id, update);
    }

    @Override
    public Mono<Void> updateScheduling(UUID id, UUID operationWindowId, Instant plannedStart, Instant plannedEnd, ChangeStatus status, ChangeRequestAuditEntry auditEntry) {
        Bson update = Updates.combine(
                Updates.set("operationWindowId", operationWindowId),
                Updates.set("plannedStart", plannedStart),
                Updates.set("plannedEnd", plannedEnd),
                Updates.set("status", status.name()),
                Updates.set(FIELD_UPDATED_AT, Instant.now()),
                Updates.push(FIELD_AUDIT_TRAIL, AuditEntryDocument.fromDomain(auditEntry))
        );
        return executeUpdate(id, update);
    }

    @Override
    public Mono<Void> updateCompletion(UUID id, String implementationNotes, ChangeStatus status, ChangeRequestAuditEntry auditEntry) {
        Bson update = Updates.combine(
                Updates.set("implementationNotes", implementationNotes),
                Updates.set("status", status.name()),
                Updates.set(FIELD_UPDATED_AT, Instant.now()),
                Updates.push(FIELD_AUDIT_TRAIL, AuditEntryDocument.fromDomain(auditEntry))
        );
        return executeUpdate(id, update);
    }

    @Override
    public Mono<Void> updateRollback(UUID id, String rollbackReason, ChangeStatus status, ChangeRequestAuditEntry auditEntry) {
        Bson update = Updates.combine(
                Updates.set("rollbackReason", rollbackReason),
                Updates.set("status", status.name()),
                Updates.set(FIELD_UPDATED_AT, Instant.now()),
                Updates.push(FIELD_AUDIT_TRAIL, AuditEntryDocument.fromDomain(auditEntry))
        );
        return executeUpdate(id, update);
    }

    @Override
    public Mono<Void> updateClose(UUID id, String closeNotes, ChangeStatus status, ChangeRequestAuditEntry auditEntry) {
        Bson update = Updates.combine(
                Updates.set("closeNotes", closeNotes),
                Updates.set("status", status.name()),
                Updates.set(FIELD_UPDATED_AT, Instant.now()),
                Updates.push(FIELD_AUDIT_TRAIL, AuditEntryDocument.fromDomain(auditEntry))
        );
        return executeUpdate(id, update);
    }

    private Mono<Void> executeUpdate(UUID id, Bson update) {
        return Mono.from(getCollection().updateOne(Filters.eq(FIELD_ID, id), update))
                .flatMap(result -> {
                    if (result.getMatchedCount() == 0) {
                        return Mono.error(new ChangeRequestNotFoundException(id));
                    }
                    return Mono.empty();
                });
    }
}
