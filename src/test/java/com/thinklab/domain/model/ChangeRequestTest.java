package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidChangeRequestStatusException;
import com.thinklab.domain.model.ChangeRequest.ChangeStatus;
import com.thinklab.domain.model.ChangeRequest.ChangeType;
import com.thinklab.domain.model.ChangeRequest.ImpactLevel;
import com.thinklab.domain.model.ChangeRequest.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeRequestTest {

    private static final String EXECUTOR = "op-1";

    private UUID id;
    private UUID organisationId;
    private UUID requesterId;
    private UUID assetId;
    private Set<UUID> targetAssetIds;

    @BeforeEach
    void setUp() {
        id = UUID.randomUUID();
        organisationId = UUID.randomUUID();
        requesterId = UUID.randomUUID();
        assetId = UUID.randomUUID();
        targetAssetIds = Set.of(assetId);
    }

    private ChangeRequest newChangeRequest(ChangeType type) {
        return ChangeRequest.createNew(id, organisationId, requesterId, "Upgrade firmware", "desc", type, targetAssetIds, null, EXECUTOR);
    }

    @Test
    @DisplayName("createNew starts DRAFT with an INITIATED audit entry")
    void createNewStartsDraft() {
        ChangeRequest cr = newChangeRequest(ChangeType.NORMAL);

        assertEquals(ChangeStatus.DRAFT, cr.getStatus());
        assertEquals(1, cr.getAuditTrail().size());
        assertEquals("INITIATED", cr.getAuditTrail().get(0).action());
        assertNull(cr.getAuditTrail().get(0).fromStatus());
        assertEquals(targetAssetIds, cr.getTargetAssetIds());
        assertEquals(cr.getCreatedAt(), cr.getUpdatedAt());
    }

    @Test
    @DisplayName("createNew rejects missing identity, blank title, an empty asset set or a blank executor")
    void createNewGuards() {
        assertThrows(IllegalArgumentException.class, () -> ChangeRequest.createNew(null, organisationId, requesterId, "t", "d", ChangeType.NORMAL, targetAssetIds, null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> ChangeRequest.createNew(id, null, requesterId, "t", "d", ChangeType.NORMAL, targetAssetIds, null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> ChangeRequest.createNew(id, organisationId, null, "t", "d", ChangeType.NORMAL, targetAssetIds, null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> ChangeRequest.createNew(id, organisationId, requesterId, "t", "d", null, targetAssetIds, null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> ChangeRequest.createNew(id, organisationId, requesterId, null, "d", ChangeType.NORMAL, targetAssetIds, null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> ChangeRequest.createNew(id, organisationId, requesterId, "", "d", ChangeType.NORMAL, targetAssetIds, null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> ChangeRequest.createNew(id, organisationId, requesterId, "t", "d", ChangeType.NORMAL, null, null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> ChangeRequest.createNew(id, organisationId, requesterId, "t", "d", ChangeType.NORMAL, Set.of(), null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> ChangeRequest.createNew(id, organisationId, requesterId, "t", "d", ChangeType.NORMAL, targetAssetIds, null, null));
        assertThrows(IllegalArgumentException.class, () -> ChangeRequest.createNew(id, organisationId, requesterId, "t", "d", ChangeType.NORMAL, targetAssetIds, null, " "));
    }

    @Test
    @DisplayName("reconstitute rejects missing mandatory identity")
    void reconstituteGuards() {
        assertThrows(IllegalArgumentException.class, () -> ChangeRequest.reconstitute(null, organisationId, requesterId, "t", "d", ChangeType.NORMAL, targetAssetIds, null, null, null, null, null, null, null, null, null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> ChangeRequest.reconstitute(id, null, requesterId, "t", "d", ChangeType.NORMAL, targetAssetIds, null, null, null, null, null, null, null, null, null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> ChangeRequest.reconstitute(id, organisationId, null, "t", "d", ChangeType.NORMAL, targetAssetIds, null, null, null, null, null, null, null, null, null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> ChangeRequest.reconstitute(id, organisationId, requesterId, null, "d", ChangeType.NORMAL, targetAssetIds, null, null, null, null, null, null, null, null, null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> ChangeRequest.reconstitute(id, organisationId, requesterId, "t", "d", null, targetAssetIds, null, null, null, null, null, null, null, null, null, null, null, null, null, null));
    }

    @Test
    @DisplayName("reconstitute defaults a missing status to DRAFT and a null asset set to empty")
    void reconstituteDefaults() {
        ChangeRequest restored = ChangeRequest.reconstitute(id, organisationId, requesterId, "t", "d", ChangeType.NORMAL, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertEquals(ChangeStatus.DRAFT, restored.getStatus());
        assertTrue(restored.getTargetAssetIds().isEmpty());
        assertTrue(restored.getAuditTrail().isEmpty());
        assertNotNull(restored.getCreatedAt());
        assertEquals(restored.getCreatedAt(), restored.getUpdatedAt());
    }

    @Test
    @DisplayName("updateBasicInfo replaces title/description and rejects a blank title or terminal state")
    void updateBasicInfo() {
        ChangeRequest cr = newChangeRequest(ChangeType.NORMAL);

        cr.updateBasicInfo("New title", "New desc", EXECUTOR);
        assertEquals("New title", cr.getTitle());
        assertEquals("New desc", cr.getDescription());

        assertThrows(IllegalArgumentException.class, () -> cr.updateBasicInfo(null, "d", EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> cr.updateBasicInfo("", "d", EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> cr.updateBasicInfo("t", "d", null));

        cr.cancel(EXECUTOR);
        assertThrows(InvalidChangeRequestStatusException.class, () -> cr.updateBasicInfo("t", "d", EXECUTOR));
    }

    @Test
    @DisplayName("updateBasicInfo is illegal for every terminal status: CLOSED, REJECTED, ROLLED_BACK, CANCELLED")
    void updateBasicInfoIllegalForEveryTerminalStatus() {
        ChangeRequest closed = inProgressChangeRequest();
        closed.complete("done", EXECUTOR);
        closed.close(null, EXECUTOR);
        assertThrows(InvalidChangeRequestStatusException.class, () -> closed.updateBasicInfo("t", "d", EXECUTOR));

        ChangeRequest rejected = assessedChangeRequest(ChangeType.NORMAL);
        rejected.routeForApproval(UUID.randomUUID(), EXECUTOR);
        rejected.reject(EXECUTOR);
        assertThrows(InvalidChangeRequestStatusException.class, () -> rejected.updateBasicInfo("t", "d", EXECUTOR));

        ChangeRequest rolledBack = inProgressChangeRequest();
        rolledBack.rollback("failed", EXECUTOR);
        assertThrows(InvalidChangeRequestStatusException.class, () -> rolledBack.updateBasicInfo("t", "d", EXECUTOR));

        ChangeRequest cancelled = newChangeRequest(ChangeType.NORMAL);
        cancelled.cancel(EXECUTOR);
        assertThrows(InvalidChangeRequestStatusException.class, () -> cancelled.updateBasicInfo("t", "d", EXECUTOR));
    }

    @Test
    @DisplayName("submit: DRAFT -> SUBMITTED, illegal outside DRAFT")
    void submit() {
        ChangeRequest cr = newChangeRequest(ChangeType.NORMAL);
        cr.submit(EXECUTOR);
        assertEquals(ChangeStatus.SUBMITTED, cr.getStatus());

        assertThrows(InvalidChangeRequestStatusException.class, () -> cr.submit(EXECUTOR));
    }

    @Test
    @DisplayName("assess: SUBMITTED -> ASSESSED, records risk/impact, illegal outside SUBMITTED")
    void assess() {
        ChangeRequest cr = newChangeRequest(ChangeType.NORMAL);
        assertThrows(InvalidChangeRequestStatusException.class, () -> cr.assess(RiskLevel.LOW, ImpactLevel.LOW, EXECUTOR));

        cr.submit(EXECUTOR);
        cr.assess(RiskLevel.HIGH, ImpactLevel.MEDIUM, EXECUTOR);

        assertEquals(ChangeStatus.ASSESSED, cr.getStatus());
        assertEquals(RiskLevel.HIGH, cr.getRiskLevel());
        assertEquals(ImpactLevel.MEDIUM, cr.getImpactLevel());
    }

    @Test
    @DisplayName("assess requires a non-null riskLevel and impactLevel")
    void assessGuards() {
        ChangeRequest cr = newChangeRequest(ChangeType.NORMAL);
        cr.submit(EXECUTOR);

        assertThrows(NullPointerException.class, () -> cr.assess(null, ImpactLevel.LOW, EXECUTOR));
        assertThrows(NullPointerException.class, () -> cr.assess(RiskLevel.LOW, null, EXECUTOR));
    }

    @Test
    @DisplayName("routeForApproval: NORMAL routes to CAB_REVIEW, EMERGENCY to ECAB_REVIEW, illegal for STANDARD")
    void routeForApproval() {
        ChangeRequest normal = assessedChangeRequest(ChangeType.NORMAL);
        UUID approvalRequestId = UUID.randomUUID();
        normal.routeForApproval(approvalRequestId, EXECUTOR);
        assertEquals(ChangeStatus.CAB_REVIEW, normal.getStatus());
        assertEquals(approvalRequestId, normal.getApprovalRequestId());

        ChangeRequest emergency = assessedChangeRequest(ChangeType.EMERGENCY);
        emergency.routeForApproval(UUID.randomUUID(), EXECUTOR);
        assertEquals(ChangeStatus.ECAB_REVIEW, emergency.getStatus());

        ChangeRequest standard = assessedChangeRequest(ChangeType.STANDARD);
        assertThrows(InvalidChangeRequestStatusException.class, () -> standard.routeForApproval(UUID.randomUUID(), EXECUTOR));
    }

    @Test
    @DisplayName("routeForApproval requires a non-null approvalRequestId and is illegal outside ASSESSED")
    void routeForApprovalGuards() {
        ChangeRequest normal = assessedChangeRequest(ChangeType.NORMAL);
        assertThrows(NullPointerException.class, () -> normal.routeForApproval(null, EXECUTOR));

        ChangeRequest draft = newChangeRequest(ChangeType.NORMAL);
        assertThrows(InvalidChangeRequestStatusException.class, () -> draft.routeForApproval(UUID.randomUUID(), EXECUTOR));
    }

    @Test
    @DisplayName("preApprove: STANDARD skips CAB/ECAB entirely, illegal for NORMAL/EMERGENCY")
    void preApprove() {
        ChangeRequest standard = assessedChangeRequest(ChangeType.STANDARD);
        standard.preApprove(EXECUTOR);
        assertEquals(ChangeStatus.APPROVED, standard.getStatus());

        ChangeRequest normal = assessedChangeRequest(ChangeType.NORMAL);
        assertThrows(InvalidChangeRequestStatusException.class, () -> normal.preApprove(EXECUTOR));
    }

    @Test
    @DisplayName("preApprove is illegal outside ASSESSED")
    void preApproveIllegalOutsideAssessed() {
        ChangeRequest draft = newChangeRequest(ChangeType.STANDARD);
        assertThrows(InvalidChangeRequestStatusException.class, () -> draft.preApprove(EXECUTOR));
    }

    @Test
    @DisplayName("approve/reject: CAB_REVIEW|ECAB_REVIEW -> APPROVED|REJECTED, illegal otherwise")
    void approveAndReject() {
        ChangeRequest approved = assessedChangeRequest(ChangeType.NORMAL);
        approved.routeForApproval(UUID.randomUUID(), EXECUTOR);
        approved.approve(EXECUTOR);
        assertEquals(ChangeStatus.APPROVED, approved.getStatus());

        ChangeRequest rejected = assessedChangeRequest(ChangeType.EMERGENCY);
        rejected.routeForApproval(UUID.randomUUID(), EXECUTOR);
        rejected.reject(EXECUTOR);
        assertEquals(ChangeStatus.REJECTED, rejected.getStatus());

        ChangeRequest draft = newChangeRequest(ChangeType.NORMAL);
        assertThrows(InvalidChangeRequestStatusException.class, () -> draft.approve(EXECUTOR));
        assertThrows(InvalidChangeRequestStatusException.class, () -> draft.reject(EXECUTOR));
    }

    @Test
    @DisplayName("schedule: APPROVED -> SCHEDULED, records the window/plan, illegal otherwise")
    void schedule() {
        ChangeRequest cr = approvedChangeRequest();
        UUID windowId = UUID.randomUUID();
        Instant start = Instant.now();
        Instant end = start.plusSeconds(3600);

        cr.schedule(windowId, start, end, EXECUTOR);

        assertEquals(ChangeStatus.SCHEDULED, cr.getStatus());
        assertEquals(windowId, cr.getOperationWindowId());
        assertEquals(start, cr.getPlannedStart());
        assertEquals(end, cr.getPlannedEnd());
    }

    private ChangeRequest approvedEmergency() {
        ChangeRequest cr = assessedChangeRequest(ChangeType.EMERGENCY);
        cr.routeForApproval(UUID.randomUUID(), EXECUTOR);
        cr.approve(EXECUTOR);
        return cr;
    }

    @Test
    @DisplayName("schedule over a CHANGE_FREEZE: an approved EMERGENCY change records the override in its audit entry")
    void scheduleWithFreezeOverride() {
        ChangeRequest cr = approvedEmergency();
        Instant start = Instant.now();

        ChangeRequest.ChangeRequestAuditEntry entry = cr.schedule(UUID.randomUUID(), start, start.plusSeconds(3600), EXECUTOR, "P1 outage");

        assertEquals(ChangeStatus.SCHEDULED, cr.getStatus());
        assertTrue(entry.detail().contains("CHANGE_FREEZE (override): P1 outage"));
    }

    @Test
    @DisplayName("validateFreezeOverride: null is no override; otherwise APPROVED + EMERGENCY + non-blank justification")
    void validateFreezeOverride() {
        ChangeRequest emergency = approvedEmergency();
        emergency.validateFreezeOverride(null);
        emergency.validateFreezeOverride("P1 outage");

        assertThrows(IllegalArgumentException.class, () -> emergency.validateFreezeOverride("  "));
        assertThrows(IllegalArgumentException.class, () -> approvedChangeRequest().validateFreezeOverride("P1 outage"));
        ChangeRequest notApproved = assessedChangeRequest(ChangeType.EMERGENCY);
        notApproved.validateFreezeOverride(null);
        assertThrows(InvalidChangeRequestStatusException.class, () -> notApproved.validateFreezeOverride("P1 outage"));
        assertThrows(IllegalArgumentException.class, () -> approvedChangeRequest().schedule(UUID.randomUUID(), Instant.now(),
                Instant.now().plusSeconds(3600), EXECUTOR, "not an emergency"));
    }

    @Test
    @DisplayName("schedule requires non-null window/dates, endAfterStart, and is illegal outside APPROVED")
    void scheduleGuards() {
        ChangeRequest cr = approvedChangeRequest();
        Instant start = Instant.now();
        Instant end = start.plusSeconds(3600);

        assertThrows(NullPointerException.class, () -> cr.schedule(null, start, end, EXECUTOR));
        assertThrows(NullPointerException.class, () -> cr.schedule(UUID.randomUUID(), null, end, EXECUTOR));
        assertThrows(NullPointerException.class, () -> cr.schedule(UUID.randomUUID(), start, null, EXECUTOR));
        assertThrows(IllegalArgumentException.class, () -> cr.schedule(UUID.randomUUID(), end, start, EXECUTOR));

        ChangeRequest draft = newChangeRequest(ChangeType.NORMAL);
        assertThrows(InvalidChangeRequestStatusException.class, () -> draft.schedule(UUID.randomUUID(), start, end, EXECUTOR));
    }

    @Test
    @DisplayName("start: SCHEDULED -> IN_PROGRESS, illegal otherwise")
    void start() {
        ChangeRequest cr = scheduledChangeRequest();
        cr.start(EXECUTOR);
        assertEquals(ChangeStatus.IN_PROGRESS, cr.getStatus());

        assertThrows(InvalidChangeRequestStatusException.class, () -> cr.start(EXECUTOR));
    }

    @Test
    @DisplayName("complete: IN_PROGRESS -> IMPLEMENTED, records notes, requires non-null notes, illegal otherwise")
    void complete() {
        ChangeRequest cr = inProgressChangeRequest();
        cr.complete("done", EXECUTOR);
        assertEquals(ChangeStatus.IMPLEMENTED, cr.getStatus());
        assertEquals("done", cr.getImplementationNotes());

        assertThrows(InvalidChangeRequestStatusException.class, () -> cr.complete("again", EXECUTOR));

        ChangeRequest another = inProgressChangeRequest();
        assertThrows(NullPointerException.class, () -> another.complete(null, EXECUTOR));
    }

    @Test
    @DisplayName("rollback: IN_PROGRESS -> ROLLED_BACK (terminal), records reason, illegal otherwise")
    void rollback() {
        ChangeRequest cr = inProgressChangeRequest();
        cr.rollback("failed midway", EXECUTOR);
        assertEquals(ChangeStatus.ROLLED_BACK, cr.getStatus());
        assertEquals("failed midway", cr.getRollbackReason());

        assertThrows(InvalidChangeRequestStatusException.class, () -> cr.rollback("again", EXECUTOR));

        ChangeRequest another = inProgressChangeRequest();
        assertThrows(NullPointerException.class, () -> another.rollback(null, EXECUTOR));
    }

    @Test
    @DisplayName("close: IMPLEMENTED -> CLOSED (terminal), notes optional, illegal otherwise")
    void close() {
        ChangeRequest cr = inProgressChangeRequest();
        cr.complete("done", EXECUTOR);
        cr.close("all good", EXECUTOR);
        assertEquals(ChangeStatus.CLOSED, cr.getStatus());
        assertEquals("all good", cr.getCloseNotes());

        assertThrows(InvalidChangeRequestStatusException.class, () -> cr.close(null, EXECUTOR));
    }

    @Test
    @DisplayName("cancel: legal from every pre-implementation state, terminal, illegal once IN_PROGRESS or later")
    void cancel() {
        for (ChangeStatus legal : List.of(ChangeStatus.DRAFT, ChangeStatus.SUBMITTED, ChangeStatus.ASSESSED,
                ChangeStatus.CAB_REVIEW, ChangeStatus.ECAB_REVIEW, ChangeStatus.APPROVED, ChangeStatus.SCHEDULED)) {
            ChangeRequest cr = changeRequestAt(legal);
            cr.cancel(EXECUTOR);
            assertEquals(ChangeStatus.CANCELLED, cr.getStatus());
        }

        ChangeRequest inProgress = inProgressChangeRequest();
        assertThrows(InvalidChangeRequestStatusException.class, () -> inProgress.cancel(EXECUTOR));
    }

    private ChangeRequest assessedChangeRequest(ChangeType type) {
        ChangeRequest cr = newChangeRequest(type);
        cr.submit(EXECUTOR);
        cr.assess(RiskLevel.LOW, ImpactLevel.LOW, EXECUTOR);
        return cr;
    }

    private ChangeRequest approvedChangeRequest() {
        ChangeRequest cr = assessedChangeRequest(ChangeType.STANDARD);
        cr.preApprove(EXECUTOR);
        return cr;
    }

    private ChangeRequest scheduledChangeRequest() {
        ChangeRequest cr = approvedChangeRequest();
        cr.schedule(UUID.randomUUID(), Instant.now(), Instant.now().plusSeconds(3600), EXECUTOR);
        return cr;
    }

    private ChangeRequest inProgressChangeRequest() {
        ChangeRequest cr = scheduledChangeRequest();
        cr.start(EXECUTOR);
        return cr;
    }

    private ChangeRequest changeRequestAt(ChangeStatus status) {
        return switch (status) {
            case DRAFT -> newChangeRequest(ChangeType.NORMAL);
            case SUBMITTED -> { var cr = newChangeRequest(ChangeType.NORMAL); cr.submit(EXECUTOR); yield cr; }
            case ASSESSED -> assessedChangeRequest(ChangeType.NORMAL);
            case CAB_REVIEW -> { var cr = assessedChangeRequest(ChangeType.NORMAL); cr.routeForApproval(UUID.randomUUID(), EXECUTOR); yield cr; }
            case ECAB_REVIEW -> { var cr = assessedChangeRequest(ChangeType.EMERGENCY); cr.routeForApproval(UUID.randomUUID(), EXECUTOR); yield cr; }
            case APPROVED -> approvedChangeRequest();
            case SCHEDULED -> scheduledChangeRequest();
            default -> throw new IllegalArgumentException("Unsupported status for this test helper: " + status);
        };
    }
}
