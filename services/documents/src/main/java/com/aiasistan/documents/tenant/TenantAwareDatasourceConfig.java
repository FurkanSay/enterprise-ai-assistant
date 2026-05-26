package com.aiasistan.documents.tenant;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Bridges TenantContext (ThreadLocal) into the Postgres session as
 * `app.current_tenant_id` so the FORCE ROW LEVEL SECURITY policies
 * created in infra/postgres/init/06-documents-tables.sql actually
 * filter rows.
 *
 * Strategy: every @Transactional method is intercepted by this aspect.
 * Before the underlying call runs, we execute
 *   SET LOCAL app.current_tenant_id = '<uuid>'
 * inside the same transaction. SET LOCAL auto-resets on commit/rollback,
 * which is exactly what we want — the next transaction on this connection
 * (whoever picks it up from the pool) starts with no tenant binding.
 */
@Configuration
public class TenantAwareDatasourceConfig {

    @Aspect
    @Component
    public static class TenantAwareTransactionAspect {

        private static final Logger log = LoggerFactory.getLogger(TenantAwareTransactionAspect.class);

        @PersistenceContext
        private EntityManager em;

        @Around("@annotation(org.springframework.transaction.annotation.Transactional) "
                + "|| @within(org.springframework.transaction.annotation.Transactional)")
        public Object setTenantBeforeTx(ProceedingJoinPoint joinPoint) throws Throwable {
            TenantContext.Current ctx;
            try {
                ctx = TenantContext.require();
            } catch (IllegalStateException missingCtx) {
                // No tenant context (e.g. internal startup or scheduled jobs).
                // We deliberately do NOT silently bypass RLS — proceed without
                // setting the GUC so any read of a tenant-scoped table returns
                // 0 rows. Callers that need to bypass RLS must explicitly use
                // the `platform_admin` role.
                log.debug("tx.tenant_aware.no_context method={}", joinPoint.getSignature());
                return joinPoint.proceed();
            }

            // Postgres accepts a string literal here; UUID.toString() is
            // already canonical, no SQL escaping needed.
            em.createNativeQuery(
                    "SELECT set_config('app.current_tenant_id', :tid, true)")
                    .setParameter("tid", ctx.tenantId().toString())
                    .getSingleResult();
            return joinPoint.proceed();
        }
    }
}
