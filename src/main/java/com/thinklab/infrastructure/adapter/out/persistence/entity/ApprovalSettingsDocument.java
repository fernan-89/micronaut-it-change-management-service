package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.ApprovalSettings;
import io.micronaut.core.annotation.Introspected;
import org.bson.codecs.pojo.annotations.BsonId;

import java.time.Instant;
import java.util.UUID;

/** MongoDB representation of the approval settings of a tenant (ADR-039); the document id is the organisation id, so there is one per tenant. */
@Introspected
public class ApprovalSettingsDocument {

    @BsonId
    private UUID id;
    private UUID cabPolicyId;
    private UUID ecabPolicyId;
    private Instant updatedAt;
    private String updatedBy;

    public ApprovalSettingsDocument() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCabPolicyId() { return cabPolicyId; }
    public void setCabPolicyId(UUID cabPolicyId) { this.cabPolicyId = cabPolicyId; }
    public UUID getEcabPolicyId() { return ecabPolicyId; }
    public void setEcabPolicyId(UUID ecabPolicyId) { this.ecabPolicyId = ecabPolicyId; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public static ApprovalSettingsDocument from(ApprovalSettings settings) {
        ApprovalSettingsDocument document = new ApprovalSettingsDocument();
        document.id = settings.organisationId();
        document.cabPolicyId = settings.cabPolicyId();
        document.ecabPolicyId = settings.ecabPolicyId();
        document.updatedAt = settings.updatedAt();
        document.updatedBy = settings.updatedBy();
        return document;
    }

    public ApprovalSettings toDomain() {
        return new ApprovalSettings(id, cabPolicyId, ecabPolicyId, updatedAt, updatedBy);
    }
}
