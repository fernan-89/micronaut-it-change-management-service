package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.ConnectionString;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoClient;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

/**
 * Creates the {@code (organisationId, status)} index on {@code change_requests} at startup, matching
 * {@link ChangeRequestMongoRepositoryAdapter#findAllByOrganisationId}'s own filter order. This adapter
 * uses the driver directly, so the kit's generic {@code MongoIndexInitializer} does not see it (same
 * rationale as {@code WorkOrderIndexInitializer}).
 *
 * <p>Fail-open: {@code createIndex} is idempotent; a failure is logged and the application still
 * starts. Turn it off with {@code thinklab.mongo.create-indexes=false}.
 */
@Singleton
@Requires(property = "thinklab.mongo.create-indexes", notEquals = "false")
public class ChangeManagementIndexInitializer implements ApplicationEventListener<StartupEvent> {

    static final String TENANT_STATUS_INDEX = "organisationId_1_status_1";

    private static final Logger log = LoggerFactory.getLogger(ChangeManagementIndexInitializer.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final MongoClient mongoClient;
    private final String database;
    private final Duration timeout;

    @Inject
    public ChangeManagementIndexInitializer(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this(mongoClient, mongoUri, TIMEOUT);
    }

    ChangeManagementIndexInitializer(MongoClient mongoClient, String mongoUri, Duration timeout) {
        this.mongoClient = Objects.requireNonNull(mongoClient, "Infrastructure constraint violated: MongoClient cannot be null.");
        String configured = new ConnectionString(Objects.requireNonNull(mongoUri, "mongodb.uri cannot be null.")).getDatabase();
        this.database = configured != null ? configured : ChangeRequestMongoRepositoryAdapter.DEFAULT_DATABASE;
        this.timeout = timeout;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        Objects.requireNonNull(event, "Application constraint violated: StartupEvent cannot be null.");
        Document keys = new Document("organisationId", 1).append("status", 1);
        try {
            Mono.from(mongoClient.getDatabase(database).getCollection(ChangeRequestMongoRepositoryAdapter.COLLECTION_NAME)
                    .createIndex(keys, new IndexOptions().name(TENANT_STATUS_INDEX))).block(timeout);
            log.info("[MONGO_INDEXES] Ensured index [{}] on [{}.{}]", TENANT_STATUS_INDEX, database, ChangeRequestMongoRepositoryAdapter.COLLECTION_NAME);
        } catch (MongoTimeoutException e) {
            log.error("[MONGO_INDEXES] MongoDB unreachable; index [{}] was not created. Reason: {}", TENANT_STATUS_INDEX, e.getMessage());
        } catch (RuntimeException e) {
            log.error("[MONGO_INDEXES] Could not create index [{}] on [{}.{}]: {}",
                    TENANT_STATUS_INDEX, database, ChangeRequestMongoRepositoryAdapter.COLLECTION_NAME, e.getMessage());
        }
    }
}
