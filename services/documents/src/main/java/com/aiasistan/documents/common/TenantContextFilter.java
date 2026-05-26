package com.aiasistan.documents.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads X-Tenant-Id / X-User-Id / X-User-Roles headers (set by Gateway).
 * Public paths (actuator, openapi) skip the requirement.
 */
@Component
@Order(1)
public class TenantContextFilter extends OncePerRequestFilter {

    private static final List<String> EXEMPT_PREFIXES =
            List.of("/actuator", "/v3/api-docs", "/swagger-ui");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (EXEMPT_PREFIXES.stream().anyMatch(path::startsWith)) {
            chain.doFilter(request, response);
            return;
        }

        String tenantHeader = request.getHeader("X-Tenant-Id");
        String userHeader = request.getHeader("X-User-Id");
        if (tenantHeader == null || userHeader == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"code\":\"documents.tenant_context_missing\","
                + "\"message\":\"X-Tenant-Id and X-User-Id headers are required\"}");
            return;
        }

        try {
            String rolesHeader = request.getHeader("X-User-Roles");
            List<String> roles = rolesHeader == null || rolesHeader.isBlank()
                    ? List.of()
                    : Arrays.asList(rolesHeader.split(","));

            TenantContext ctx = new TenantContext(
                    UUID.fromString(tenantHeader),
                    UUID.fromString(userHeader),
                    roles,
                    request.getHeader("X-Trace-Id"),
                    request.getHeader("X-Correlation-Id"));

            TenantContext.set(ctx);
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
