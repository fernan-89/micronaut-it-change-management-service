package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidChangeRequestStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Core Domain Model representing the ChangeRequest Aggregate Root (BIAN Service Domain:
 * {@code it-change-management}, ITIL "GMUD" - Gestão de Mudanças).
 *
 * <p><b>BIAN Alignment (ADR-013):</b> every route is a named Behavior Qualifier rather than a generic
 * {@code control/{status}} endpoint (mirroring {@code WorkOrder}, ADR-030 of {@code it-hardware-maintenance}),
 * since several transitions carry their own data (assessment records risk/impact, routing and
 * scheduling call out to other Service Domains).
 *
 * <p><b>Routing by change type (ADR-031):</b> a {@code STANDARD} change is a pre-approved template and
 * skips CAB/ECAB entirely ({@link #preApprove}); {@code NORMAL} routes to CAB, {@code EMERGENCY} to
 * ECAB ({@link #routeForApproval}) - the calling application layer creates the matching
 * {@code ApprovalRequest} on workflow-approval-service first and passes its id in.
 *
 * <p><b>Forensic Audit Ledger:</b> every mutation appends an immutable {@link ChangeRequestAuditEntry},
 * mirroring the platform's established pattern.
 *
 * <p>Strictly pure Java. Agnostic of frameworks, databases, or web layers. This aggregate never calls
 * another Service Domain itself - {@code approvalRequestId}/{@code operationWindowId} are handed in
 * already resolved by the application layer's ports (ADR-032).
 */
public class ChangeRequest {

    private final UUID id;
    private final UUID organisationId;
    private final UUID requesterId;
    private String title;
    private String description;
    private final ChangeType changeType;
    private final Set<UUID> targetAssetIds;
    private final ExternalReference externalReference;
    private RiskLevel riskLevel;
    private ImpactLevel impactLevel;
    private ChangeStatus status;
    private UUID approvalRequestId;
    private UUID operationWindowId;
    private Instant plannedStart;
    private Instant plannedEnd;
    private String implementationNotes;
    private String rollbackReason;
    private String closeNotes;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<ChangeRequestAuditEntry> auditTrail;

    private ChangeRequest(UUID id, UUID organisationId, UUID requesterId, String title, String description,
                           ChangeType changeType, Set<UUID> targetAssetIds, ExternalReference externalReference, String executor) {
        this.id = id;
        this.organisationId = organisationId;
        this.requesterId = requesterId;
        this.title = title;
        this.description = description;
        this.changeType = changeType;
        this.targetAssetIds = new LinkedHashSet<>(targetAssetIds);
        this.externalReference = externalReference;
        this.status = ChangeStatus.DRAFT;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.auditTrail = new ArrayList<>();
        this.auditTrail.add(new ChangeRequestAuditEntry(this.createdAt, "INITIATED", executor, null, ChangeStatus.DRAFT,
                String.format("Change request drafted (%s).", changeType)));
    }

    private ChangeRequest(UUID id, UUID organisationId, UUID requesterId, String title, String description,
                           ChangeType changeType, Set<UUID> targetAssetIds, ExternalReference externalReference,
                           RiskLevel riskLevel, ImpactLevel impactLevel,
                           ChangeStatus status, UUID approvalRequestId, UUID operationWindowId, Instant plannedStart,
                           Instant plannedEnd, String implementationNotes, String rollbackReason, String closeNotes,
                           Instant createdAt, Instant updatedAt, List<ChangeRequestAuditEntry> auditTrail) {
        this.id = id;
        this.organisationId = organisationId;
        this.requesterId = requesterId;
        this.title = title;
        this.description = description;
        this.changeType = changeType;
        this.targetAssetIds = targetAssetIds != null ? new LinkedHashSet<>(targetAssetIds) : new LinkedHashSet<>();
        this.externalReference = externalReference;
        this.riskLevel = riskLevel;
        this.impactLevel = impactLevel;
        this.status = status != null ? status : ChangeStatus.DRAFT;
        this.approvalRequestId = approvalRequestId;
        this.operationWindowId = operationWindowId;
        this.plannedStart = plannedStart;
        this.plannedEnd = plannedEnd;
        this.implementationNotes = implementationNotes;
        this.rollbackReason = rollbackReason;
        this.closeNotes = closeNotes;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.auditTrail = auditTrail != null ? new ArrayList<>(auditTrail) : new ArrayList<>();
    }

    public static ChangeRequest createNew(UUID id, UUID organisationId, UUID requesterId, String title, String description,
                                           ChangeType changeType, Set<UUID> targetAssetIds, ExternalReference externalReference,
                                           String executor) {
        if (id == null || organisationId == null || requesterId == null || changeType == null) {
            throw new IllegalArgumentException("ID, Organisation ID, Requester ID and Change Type are mandatory for ChangeRequest creation.");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title is mandatory for ChangeRequest creation.");
        }
        if (targetAssetIds == null || targetAssetIds.isEmpty()) {
            throw new IllegalArgumentException("At least one target Asset is mandatory for ChangeRequest creation.");
        }
        requireExecutor(executor);
        return new ChangeRequest(id, organisationId, requesterId, title, description, changeType, targetAssetIds, externalReference, executor);
    }

    public static ChangeRequest reconstitute(UUID id, UUID organisationId, UUID requesterId, String title, String description,
                                              ChangeType changeType, Set<UUID> targetAssetIds, ExternalReference externalReference,
                                              RiskLevel riskLevel, ImpactLevel impactLevel,
                                              ChangeStatus status, UUID approvalRequestId, UUID operationWindowId, Instant plannedStart,
                                              Instant plannedEnd, String implementationNotes, String rollbackReason, String closeNotes,
                                              Instant createdAt, Instant updatedAt, List<ChangeRequestAuditEntry> auditTrail) {
        if (id == null || organisationId == null || requesterId == null || title == null || changeType == null) {
            throw new IllegalArgumentException("ID, Organisation ID, Requester ID, Title and Change Type are mandatory to reconstitute a ChangeRequest.");
        }
        return new ChangeRequest(id, organisationId, requesterId, title, description, changeType, targetAssetIds, externalReference,
                riskLevel, impactLevel, status, approvalRequestId, operationWindowId, plannedStart, plannedEnd, implementationNotes,
                rollbackReason, closeNotes, createdAt, updatedAt, auditTrail);
    }

    // --- Domain Behaviors ---

    /** Behavior Qualifier: {@code update}. Not available once the change is terminal. */
    public ChangeRequestAuditEntry updateBasicInfo(String newTitle, String newDescription, String executor) {
        requireNotTerminal("update");
        if (newTitle == null || newTitle.isBlank()) {
            throw new IllegalArgumentException("Title cannot be empty.");
        }
        requireExecutor(executor);
        this.title = newTitle;
        this.description = newDescription;
        return record("UPDATED", executor, "Basic information updated.");
    }

    /** Behavior Qualifier: {@code control/submit}. DRAFT -&gt; SUBMITTED. */
    public ChangeRequestAuditEntry submit(String executor) {
        requireStatus(ChangeStatus.DRAFT);
        return transition(ChangeStatus.SUBMITTED, "SUBMITTED", executor, "Submitted for assessment.");
    }

    /** Behavior Qualifier: {@code assess}. SUBMITTED -&gt; ASSESSED. */
    public ChangeRequestAuditEntry assess(RiskLevel newRiskLevel, ImpactLevel newImpactLevel, String executor) {
        requireStatus(ChangeStatus.SUBMITTED);
        this.riskLevel = Objects.requireNonNull(newRiskLevel, "riskLevel is mandatory to assess a ChangeRequest.");
        this.impactLevel = Objects.requireNonNull(newImpactLevel, "impactLevel is mandatory to assess a ChangeRequest.");
        return transition(ChangeStatus.ASSESSED, "ASSESSED", executor,
                String.format("Assessed: risk=%s, impact=%s.", newRiskLevel, newImpactLevel));
    }

    /**
     * Behavior Qualifier: {@code route-for-approval}. ASSESSED -&gt; CAB_REVIEW (NORMAL) or ECAB_REVIEW
     * (EMERGENCY). Illegal for a STANDARD change, which is pre-approved instead ({@link #preApprove}).
     * The application layer has already created the referenced {@code ApprovalRequest}.
     */
    public ChangeRequestAuditEntry routeForApproval(UUID newApprovalRequestId, String executor) {
        requireStatus(ChangeStatus.ASSESSED);
        if (changeType == ChangeType.STANDARD) {
            throw new InvalidChangeRequestStatusException(
                    "Illegal transition: a STANDARD change is pre-approved and never routed to CAB/ECAB.");
        }
        this.approvalRequestId = Objects.requireNonNull(newApprovalRequestId, "approvalRequestId is mandatory to route a ChangeRequest for approval.");
        ChangeStatus target = changeType == ChangeType.EMERGENCY ? ChangeStatus.ECAB_REVIEW : ChangeStatus.CAB_REVIEW;
        return transition(target, "ROUTED_FOR_APPROVAL", executor,
                String.format("Routed to %s (ApprovalRequest [%s]).", target, newApprovalRequestId));
    }

    /** Behavior Qualifier: {@code route-for-approval}, STANDARD path. ASSESSED -&gt; APPROVED, no CAB/ECAB. */
    public ChangeRequestAuditEntry preApprove(String executor) {
        requireStatus(ChangeStatus.ASSESSED);
        if (changeType != ChangeType.STANDARD) {
            throw new InvalidChangeRequestStatusException(
                    "Illegal transition: only a STANDARD change can be pre-approved without CAB/ECAB routing.");
        }
        return transition(ChangeStatus.APPROVED, "PRE_APPROVED", executor, "Pre-approved: STANDARD change template.");
    }

    /** Behavior Qualifier: {@code approval/capture}, resolved APPROVE. CAB_REVIEW|ECAB_REVIEW -&gt; APPROVED. */
    public ChangeRequestAuditEntry approve(String executor) {
        requireStatus(ChangeStatus.CAB_REVIEW, ChangeStatus.ECAB_REVIEW);
        return transition(ChangeStatus.APPROVED, "APPROVED", executor, "Approval quorum reached.");
    }

    /** Behavior Qualifier: {@code approval/capture}, resolved REJECT (terminal). CAB_REVIEW|ECAB_REVIEW -&gt; REJECTED. */
    public ChangeRequestAuditEntry reject(String executor) {
        requireStatus(ChangeStatus.CAB_REVIEW, ChangeStatus.ECAB_REVIEW);
        return transition(ChangeStatus.REJECTED, "REJECTED", executor, "Rejected by the approval board.");
    }

    /**
     * Behavior Qualifier: {@code schedule}. APPROVED -&gt; SCHEDULED. The application layer has already
     * reserved the referenced implementation window on operation-window-service.
     */
    public ChangeRequestAuditEntry schedule(UUID newOperationWindowId, Instant newPlannedStart, Instant newPlannedEnd, String executor) {
        return schedule(newOperationWindowId, newPlannedStart, newPlannedEnd, executor, null);
    }

    /**
     * As above; a non-null {@code freezeOverrideJustification} records, in the audit entry, that the window was reserved over
     * an active CHANGE_FREEZE (ADR-034) - legal only for an EMERGENCY change.
     */
    public ChangeRequestAuditEntry schedule(UUID newOperationWindowId, Instant newPlannedStart, Instant newPlannedEnd, String executor,
                                            String freezeOverrideJustification) {
        requireStatus(ChangeStatus.APPROVED);
        this.operationWindowId = Objects.requireNonNull(newOperationWindowId, "operationWindowId is mandatory to schedule a ChangeRequest.");
        this.plannedStart = Objects.requireNonNull(newPlannedStart, "plannedStart is mandatory to schedule a ChangeRequest.");
        this.plannedEnd = Objects.requireNonNull(newPlannedEnd, "plannedEnd is mandatory to schedule a ChangeRequest.");
        if (!newPlannedEnd.isAfter(newPlannedStart)) {
            throw new IllegalArgumentException("plannedEnd must be after plannedStart.");
        }
        validateFreezeOverride(freezeOverrideJustification);
        if (freezeOverrideJustification != null) {
            return transition(ChangeStatus.SCHEDULED, "SCHEDULED", executor,
                    "Implementation window reserved over a CHANGE_FREEZE (override): " + freezeOverrideJustification);
        }
        return transition(ChangeStatus.SCHEDULED, "SCHEDULED", executor, "Implementation window reserved.");
    }

    /**
     * Guards a CHANGE_FREEZE override (ADR-034) before anything is reserved remotely: {@code null} means no override; otherwise
     * the change must be APPROVED, an EMERGENCY change (so it went through the ECAB), and the justification non-blank.
     */
    public void validateFreezeOverride(String freezeOverrideJustification) {
        if (freezeOverrideJustification == null) {
            return;
        }
        requireStatus(ChangeStatus.APPROVED);
        if (changeType != ChangeType.EMERGENCY || freezeOverrideJustification.isBlank()) {
            throw new IllegalArgumentException("A CHANGE_FREEZE override needs a non-blank justification and is only allowed for an EMERGENCY change.");
        }
    }

    /** Behavior Qualifier: {@code control/start}. SCHEDULED -&gt; IN_PROGRESS. */
    public ChangeRequestAuditEntry start(String executor) {
        requireStatus(ChangeStatus.SCHEDULED);
        return transition(ChangeStatus.IN_PROGRESS, "IMPLEMENTATION_STARTED", executor, "Implementation started.");
    }

    /** Behavior Qualifier: {@code complete}. IN_PROGRESS -&gt; IMPLEMENTED. */
    public ChangeRequestAuditEntry complete(String newImplementationNotes, String executor) {
        requireStatus(ChangeStatus.IN_PROGRESS);
        this.implementationNotes = Objects.requireNonNull(newImplementationNotes, "implementationNotes is mandatory to complete a ChangeRequest.");
        return transition(ChangeStatus.IMPLEMENTED, "IMPLEMENTED", executor, "Implementation completed.");
    }

    /** Behavior Qualifier: {@code rollback} (terminal). IN_PROGRESS -&gt; ROLLED_BACK. */
    public ChangeRequestAuditEntry rollback(String reason, String executor) {
        requireStatus(ChangeStatus.IN_PROGRESS);
        this.rollbackReason = Objects.requireNonNull(reason, "reason is mandatory to roll back a ChangeRequest.");
        return transition(ChangeStatus.ROLLED_BACK, "ROLLED_BACK", executor, "Implementation rolled back: " + reason);
    }

    /** Behavior Qualifier: {@code control/close} (terminal). IMPLEMENTED -&gt; CLOSED. */
    public ChangeRequestAuditEntry close(String newCloseNotes, String executor) {
        requireStatus(ChangeStatus.IMPLEMENTED);
        this.closeNotes = newCloseNotes;
        return transition(ChangeStatus.CLOSED, "CLOSED", executor, "Closed.");
    }

    /**
     * Behavior Qualifier: {@code control/cancel} (terminal, replaces DELETE). Legal any time before
     * implementation actually starts.
     */
    public ChangeRequestAuditEntry cancel(String executor) {
        requireStatus(ChangeStatus.DRAFT, ChangeStatus.SUBMITTED, ChangeStatus.ASSESSED, ChangeStatus.CAB_REVIEW,
                ChangeStatus.ECAB_REVIEW, ChangeStatus.APPROVED, ChangeStatus.SCHEDULED);
        return transition(ChangeStatus.CANCELLED, "CANCELLED", executor, "Cancelled before implementation started.");
    }

    // --- Internal helpers ---

    private ChangeRequestAuditEntry transition(ChangeStatus newStatus, String action, String executor, String detail) {
        requireExecutor(executor);
        ChangeStatus previous = this.status;
        this.status = newStatus;
        this.updatedAt = Instant.now();
        ChangeRequestAuditEntry entry = new ChangeRequestAuditEntry(this.updatedAt, action, executor, previous, newStatus, detail);
        this.auditTrail.add(entry);
        return entry;
    }

    private ChangeRequestAuditEntry record(String action, String executor, String detail) {
        requireExecutor(executor);
        this.updatedAt = Instant.now();
        ChangeRequestAuditEntry entry = new ChangeRequestAuditEntry(this.updatedAt, action, executor, this.status, this.status, detail);
        this.auditTrail.add(entry);
        return entry;
    }

    private void requireStatus(ChangeStatus... allowed) {
        if (Arrays.asList(allowed).contains(this.status)) {
            return;
        }
        throw new InvalidChangeRequestStatusException(String.format(
                "Illegal transition: ChangeRequest is [%s], expected one of %s.", this.status, Arrays.toString(allowed)));
    }

    private void requireNotTerminal(String operation) {
        if (isTerminal()) {
            throw new InvalidChangeRequestStatusException(String.format(
                    "Compliance Violation: cannot %s a %s ChangeRequest; the lifecycle is terminal.", operation, this.status));
        }
    }

    private boolean isTerminal() {
        return status == ChangeStatus.CLOSED || status == ChangeStatus.REJECTED
                || status == ChangeStatus.ROLLED_BACK || status == ChangeStatus.CANCELLED;
    }

    private static void requireExecutor(String executor) {
        if (executor == null || executor.isBlank()) {
            throw new IllegalArgumentException("Executor is mandatory for auditable ChangeRequest mutations.");
        }
    }

    // --- Getters ---

    public UUID getId() { return id; }
    public UUID getOrganisationId() { return organisationId; }
    public UUID getRequesterId() { return requesterId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public ChangeType getChangeType() { return changeType; }
    public Set<UUID> getTargetAssetIds() { return Collections.unmodifiableSet(targetAssetIds); }
    public ExternalReference getExternalReference() { return externalReference; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public ImpactLevel getImpactLevel() { return impactLevel; }
    public ChangeStatus getStatus() { return status; }
    public UUID getApprovalRequestId() { return approvalRequestId; }
    public UUID getOperationWindowId() { return operationWindowId; }
    public Instant getPlannedStart() { return plannedStart; }
    public Instant getPlannedEnd() { return plannedEnd; }
    public String getImplementationNotes() { return implementationNotes; }
    public String getRollbackReason() { return rollbackReason; }
    public String getCloseNotes() { return closeNotes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<ChangeRequestAuditEntry> getAuditTrail() { return Collections.unmodifiableList(auditTrail); }

    // --- Nested Value Objects ---

    public enum ChangeType { STANDARD, NORMAL, EMERGENCY }

    public enum RiskLevel { LOW, MEDIUM, HIGH }

    public enum ImpactLevel { LOW, MEDIUM, HIGH }

    /**
     * <pre>
     * DRAFT -&gt; SUBMITTED -&gt; ASSESSED -+-&gt; APPROVED (STANDARD: pre-approved)         -&gt; SCHEDULED
     *                                |-&gt; CAB_REVIEW   (NORMAL)    -&gt; APPROVED -&gt;-+     -&gt; IN_PROGRESS -+-&gt; IMPLEMENTED -&gt; CLOSED (terminal)
     *                                |                              |-&gt; REJECTED (terminal)              |-&gt; ROLLED_BACK (terminal)
     *                                |-&gt; ECAB_REVIEW  (EMERGENCY) -&gt; APPROVED -&gt;-+
     *                                                                |-&gt; REJECTED (terminal)
     * DRAFT, SUBMITTED, ASSESSED, CAB_REVIEW, ECAB_REVIEW, APPROVED, SCHEDULED -&gt; CANCELLED (terminal)
     * </pre>
     */
    public enum ChangeStatus {
        DRAFT, SUBMITTED, ASSESSED, CAB_REVIEW, ECAB_REVIEW, APPROVED, REJECTED,
        SCHEDULED, IN_PROGRESS, IMPLEMENTED, CLOSED, ROLLED_BACK, CANCELLED
    }

    /**
     * Immutable forensic ledger entry, mirroring the platform's established audit-trail pattern.
     *
     * @param fromStatus status before the action ({@code null} for the initiating entry)
     * @param toStatus   status after the action (equal to {@code fromStatus} for non-transition actions)
     */
    public record ChangeRequestAuditEntry(Instant occurredAt, String action, String executor,
                                           ChangeStatus fromStatus, ChangeStatus toStatus, String detail) {}

    /**
     * An optional pointer into an external change/ticketing system (e.g. ServiceNow, Jira), set once at
     * creation and never mutated - so a future integration-hub can connect without a schema change,
     * without this service importing from or depending on that system today.
     */
    public record ExternalReference(String system, String externalId) {
        public ExternalReference {
            Objects.requireNonNull(system, "system cannot be null.");
            Objects.requireNonNull(externalId, "externalId cannot be null.");
        }
    }
}
