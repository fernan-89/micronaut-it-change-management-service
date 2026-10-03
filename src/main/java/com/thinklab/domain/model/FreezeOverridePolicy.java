package com.thinklab.domain.model;

import java.util.Set;

/**
 * Who may ask for a CHANGE_FREEZE override (ADR-034, ADR-035): with security on, only a caller whose verified role is {@code ADMIN}
 * (or {@code SERVICE}, another platform service acting for one); an OPERATOR, REQUESTER or VIEWER may schedule an emergency change
 * normally but cannot waive a freeze. With security off there is no role at all ({@code null}) and nothing is enforced, the same
 * "off by default" posture as the rest of the platform.
 *
 * <p>This deliberately reuses the roles the kit already has. A dedicated change-manager role needs a kit release and a rollout to every
 * service, so instead ADR-036 also lets a member of the tenant's ECAB waive a freeze (decided by the use case, which can read the
 * ECAB; this class only answers for the role).
 */
public final class FreezeOverridePolicy {

    private static final Set<String> ELEVATED = Set.of("ADMIN", "SERVICE");

    private FreezeOverridePolicy() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /** True when the verified role may waive a freeze; {@code null} means security is off, so nothing is enforced. */
    public static boolean permits(String verifiedRole) {
        return verifiedRole == null || ELEVATED.contains(verifiedRole);
    }
}
