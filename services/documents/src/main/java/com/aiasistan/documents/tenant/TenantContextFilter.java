package com.aiasistan.documents.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Reads X-Tenant-Id / X-User-Id headers (set by Gateway after JWT validation)
 * and binds them to the request-scoped ThreadLocal. Also pushes the values
 * into SLF4J MDC so every downstream log line carries them.
 *
 * Public paths that don't need tenant context (e.g. /actuator/*, swagger)
 * skip the requirement; everything else 401s if headers are missing.
 */
@Component
@Order(10)
public class TenantContextFilter extends OncePerRequestFilter {

    private static final String HEADER_TENANT = "X-Tenant-Id";
    private static final String HEADER_USER = "X-User-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        String tenantHeader = request.getHeader(HEADER_TENANT);
        String userHeader = request.getHeader(HEADER_USER);

        boolean skip = isPublicPath(request.getRequestURI());
        if (!skip && (tenantHeader == null || userHeader == null)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"code\":\"documents.tenant_context_missing\"," +
                "\"message\":\"Missing X-Tenant-Id or X-User-Id header.\"}");
            return;
        }

        try {
            if (!skip) {
                TenantContext.Current ctx = new TenantContext.Current(
                        UUID.fromString(tenantHeader),
                        UUID.fromString(userHeader));
                TenantContext.set(ctx);
                MDC.put("tenant_id", tenantHeader);
                MDC.put("user_id", userHeader);
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove("tenant_id");
            MDC.remove("user_id");
        }
    }

    private boolean isPublicPath(String uri) {
        return uri.startsWith("/actuator")
                || uri.startsWith("/health")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/swagger");
    }
}
