package com.aiasistan.documents.tenant;

import java.util.UUID;

/**
 * Request-scoped tenant + user, held in a ThreadLocal because Spring MVC
 * threads handle one request at a time. Async / reactive paths would need
 * a Context propagation strategy; we are explicitly servlet-only.
 *
 * Authority: the Gateway has already validated the JWT and forwards the
 * tenant/user claims as headers. The Documents service trusts those
 * headers because the Gateway is the only ingress on the internal network.
 */
public final class TenantContext {

    public record Current(UUID tenantId, UUID userId) { }

    private static final ThreadLocal<Current> HOLDER = new ThreadLocal<>();

    private TenantContext() { }

    public static void set(Current ctx) {
        HOLDER.set(ctx);
    }

    public static Current require() {
        Current ctx = HOLDER.get();
        if (ctx == null) {
            throw new IllegalStateException(
                "TenantContext not set — Gateway must inject X-Tenant-Id / X-User-Id");
        }
        return ctx;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
