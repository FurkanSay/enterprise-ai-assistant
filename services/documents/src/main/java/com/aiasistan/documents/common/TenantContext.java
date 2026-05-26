package com.aiasistan.documents.common;

import java.util.List;
import java.util.UUID;

/**
 * Tenant context for the current request thread.
 *
 * <p>Populated by {@link TenantContextFilter} from Gateway-forwarded headers.
 * Stored in a ThreadLocal — accessible from any layer without param passing.
 *
 * <p>Authority: Gateway is the only entity that decides tenant_id (after JWT
 * validation). Downstream services trust the headers.
 */
public record TenantContext(
        UUID tenantId,
        UUID userId,
        List<String> roles,
        String traceId,
        String correlationId) {

    private static final ThreadLocal<TenantContext> CURRENT = new ThreadLocal<>();

    public static void set(TenantContext ctx) {
        CURRENT.set(ctx);
    }

    public static TenantContext current() {
        TenantContext ctx = CURRENT.get();
        if (ctx == null) {
            throw new IllegalStateException(
                "TenantContext not set — middleware ordering or auth wiring issue.");
        }
        return ctx;
    }

    public static void clear() {
        CURRENT.remove();
    }

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
