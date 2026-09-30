package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.ChangeRequest;
import com.thinklab.domain.model.ChangeRequest.ChangeRequestAuditEntry;
import com.thinklab.domain.model.ChangeRequest.ChangeStatus;
import com.thinklab.domain.model.ChangeRequest.ChangeType;
import com.thinklab.domain.model.ChangeRequest.ExternalReference;
import com.thinklab.domain.model.ChangeRequest.ImpactLevel;
import com.thinklab.domain.model.ChangeRequest.RiskLevel;
import io.micronaut.core.annotation.Introspected;
import org.bson.codecs.pojo.annotations.BsonId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Infrastructure-specific representation of the ChangeRequest Aggregate for MongoDB. Keeps the pure
 * Domain Model free of persistence annotations, same pattern as {@code WorkOrderDocument}.
 */
@Introspected
public class ChangeRequestDocument {

    @BsonId
    private UUID id;

    private UUID organisationId;
    private UUID requesterId;
    private String title;
    private String description;
    private String changeType;
    private Set<UUID> targetAssetIds = new LinkedHashSet<>();
    private ExternalReferenceDocument externalReference;
    private String riskLevel;
    private String impactLevel;
    private String status;
    private UUID approvalRequestId;
    private UUID operationWindowId;
    private Instant plannedStart;
    private Instant plannedEnd;
    private String implementationNotes;
    private String rollbackReason;
    private String closeNotes;
    private Instant createdAt;
    private Instant updatedAt;
    private List<AuditEntryDocument> auditTrail = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganisationId() { return organisationId; }
    public void setOrganisationId(UUID organisationId) { this.organisationId = organisationId; }
    public UUID getRequesterId() { return requesterId; }
    public void setRequesterId(UUID requesterId) { this.requesterId = requesterId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }
    public Set<UUID> getTargetAssetIds() { return targetAssetIds; }
    public void setTargetAssetIds(Set<UUID> targetAssetIds) { this.targetAssetIds = targetAssetIds; }
    public ExternalReferenceDocument getExternalReference() { return externalReference; }
    public void setExternalReference(ExternalReferenceDocument externalReference) { this.externalReference = externalReference; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getImpactLevel() { return impactLevel; }
    public void setImpactLevel(String impactLevel) { this.impactLevel = impactLevel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UUID getApprovalRequestId() { return approvalRequestId; }
    public void setApprovalRequestId(UUID approvalRequestId) { this.approvalRequestId = approvalRequestId; }
    public UUID getOperationWindowId() { return operationWindowId; }
    public void setOperationWindowId(UUID operationWindowId) { this.operationWindowId = operationWindowId; }
    public Instant getPlannedStart() { return plannedStart; }
    public void setPlannedStart(Instant plannedStart) { this.plannedStart = plannedStart; }
    public Instant getPlannedEnd() { return plannedEnd; }
    public void setPlannedEnd(Instant plannedEnd) { this.plannedEnd = plannedEnd; }
    public String getImplementationNotes() { return implementationNotes; }
    public void setImplementationNotes(String implementationNotes) { this.implementationNotes = implementationNotes; }
    public String getRollbackReason() { return rollbackReason; }
    public void setRollbackReason(String rollbackReason) { this.rollbackReason = rollbackReason; }
    public String getCloseNotes() { return closeNotes; }
    public void setCloseNotes(String closeNotes) { this.closeNotes = closeNotes; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<AuditEntryDocument> getAuditTrail() { return auditTrail; }
    public void setAuditTrail(List<AuditEntryDocument> auditTrail) { this.auditTrail = auditTrail; }

    @Introspected
    public record ExternalReferenceDocument(String system, String externalId) {
        public static ExternalReferenceDocument fromDomain(ExternalReference ref) {
            return ref == null ? null : new ExternalReferenceDocument(ref.system(), ref.externalId());
        }

        ExternalReference toDomain() { return new ExternalReference(system, externalId); }
    }

    @Introspected
    public record AuditEntryDocument(Instant occurredAt, String action, String executor,
                                      String fromStatus, String toStatus, String detail) {

        public static AuditEntryDocument fromDomain(ChangeRequestAuditEntry entry) {
            return new AuditEntryDocument(entry.occurredAt(), entry.action(), entry.executor(),
                    entry.fromStatus() != null ? entry.fromStatus().name() : null, entry.toStatus().name(), entry.detail());
        }

        // toStatus has no null branch here: fromDomain above always writes entry.toStatus().name()
        // unconditionally, so a document this application wrote never has a null toStatus.
        ChangeRequestAuditEntry toDomain() {
            return new ChangeRequestAuditEntry(occurredAt, action, executor,
                    fromStatus != null ? ChangeStatus.valueOf(fromStatus) : null,
                    ChangeStatus.valueOf(toStatus), detail);
        }
    }

    public static final class ChangeRequestPersistenceMapper {

        private ChangeRequestPersistenceMapper() { throw new UnsupportedOperationException(); }

        public static ChangeRequestDocument toDocument(ChangeRequest changeRequest) {
            ChangeRequestDocument doc = new ChangeRequestDocument();
            doc.setId(changeRequest.getId());
            doc.setOrganisationId(changeRequest.getOrganisationId());
            doc.setRequesterId(changeRequest.getRequesterId());
            doc.setTitle(changeRequest.getTitle());
            doc.setDescription(changeRequest.getDescription());
            doc.setChangeType(changeRequest.getChangeType().name());
            doc.setTargetAssetIds(new LinkedHashSet<>(changeRequest.getTargetAssetIds()));
            doc.setExternalReference(ExternalReferenceDocument.fromDomain(changeRequest.getExternalReference()));
            doc.setRiskLevel(changeRequest.getRiskLevel() != null ? changeRequest.getRiskLevel().name() : null);
            doc.setImpactLevel(changeRequest.getImpactLevel() != null ? changeRequest.getImpactLevel().name() : null);
            doc.setStatus(changeRequest.getStatus().name());
            doc.setApprovalRequestId(changeRequest.getApprovalRequestId());
            doc.setOperationWindowId(changeRequest.getOperationWindowId());
            doc.setPlannedStart(changeRequest.getPlannedStart());
            doc.setPlannedEnd(changeRequest.getPlannedEnd());
            doc.setImplementationNotes(changeRequest.getImplementationNotes());
            doc.setRollbackReason(changeRequest.getRollbackReason());
            doc.setCloseNotes(changeRequest.getCloseNotes());
            doc.setCreatedAt(changeRequest.getCreatedAt());
            doc.setUpdatedAt(changeRequest.getUpdatedAt());
            doc.setAuditTrail(changeRequest.getAuditTrail().stream().map(AuditEntryDocument::fromDomain).collect(Collectors.toCollection(ArrayList::new)));
            return doc;
        }

        public static ChangeRequest toDomain(ChangeRequestDocument doc) {
            ChangeStatus status = doc.getStatus() != null ? ChangeStatus.valueOf(doc.getStatus()) : ChangeStatus.DRAFT;
            RiskLevel riskLevel = doc.getRiskLevel() != null ? RiskLevel.valueOf(doc.getRiskLevel()) : null;
            ImpactLevel impactLevel = doc.getImpactLevel() != null ? ImpactLevel.valueOf(doc.getImpactLevel()) : null;
            Set<UUID> targetAssetIds = doc.getTargetAssetIds() != null ? doc.getTargetAssetIds() : new LinkedHashSet<>();
            List<ChangeRequestAuditEntry> trail = doc.getAuditTrail() != null
                    ? doc.getAuditTrail().stream().map(AuditEntryDocument::toDomain).collect(Collectors.toList()) : new ArrayList<>();

            return ChangeRequest.reconstitute(
                    doc.getId(), doc.getOrganisationId(), doc.getRequesterId(), doc.getTitle(), doc.getDescription(),
                    ChangeType.valueOf(doc.getChangeType()), targetAssetIds,
                    doc.getExternalReference() != null ? doc.getExternalReference().toDomain() : null,
                    riskLevel, impactLevel, status,
                    doc.getApprovalRequestId(), doc.getOperationWindowId(), doc.getPlannedStart(), doc.getPlannedEnd(),
                    doc.getImplementationNotes(), doc.getRollbackReason(), doc.getCloseNotes(),
                    doc.getCreatedAt(), doc.getUpdatedAt(), trail
            );
        }
    }
}
