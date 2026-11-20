package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.thinklab.domain.model.ApprovalSettings;
import com.thinklab.domain.repository.ApprovalSettingsRepository;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ApprovalSettingsDocument;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

/** MongoDB adapter for the approval settings of a tenant (ADR-039): one document per organisation, replaced as a whole. */
@Singleton
public class ApprovalSettingsMongoRepositoryAdapter implements ApprovalSettingsRepository {

    static final String COLLECTION_NAME = "approval_settings";
    private static final String FIELD_ID = "_id";

    private static final CodecRegistry POJO_CODEC_REGISTRY = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build())
    );

    private final MongoClient mongoClient;
    private final String database;

    public ApprovalSettingsMongoRepositoryAdapter(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this.mongoClient = mongoClient;
        String configured = new ConnectionString(Objects.requireNonNull(mongoUri, "mongodb.uri cannot be null.")).getDatabase();
        this.database = configured != null ? configured : ChangeRequestMongoRepositoryAdapter.DEFAULT_DATABASE;
    }

    private MongoCollection<ApprovalSettingsDocument> collection() {
        return mongoClient.getDatabase(database)
                .getCollection(COLLECTION_NAME, ApprovalSettingsDocument.class)
                .withCodecRegistry(POJO_CODEC_REGISTRY);
    }

    @Override
    public Mono<ApprovalSettings> findByOrganisationId(UUID organisationId) {
        Objects.requireNonNull(organisationId, "Infrastructure constraint violated: Organisation UUID is mandatory.");
        return Mono.from(collection().find(Filters.eq(FIELD_ID, organisationId)).first()).map(ApprovalSettingsDocument::toDomain);
    }

    @Override
    public Mono<ApprovalSettings> save(ApprovalSettings settings) {
        Objects.requireNonNull(settings, "Infrastructure constraint violated: settings cannot be null.");
        return Mono.from(collection().replaceOne(Filters.eq(FIELD_ID, settings.organisationId()), ApprovalSettingsDocument.from(settings), new ReplaceOptions().upsert(true)))
                .thenReturn(settings);
    }
}
