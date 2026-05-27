package com.aiasistan.documents.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Tenant scoping is enforced by Postgres RLS, NOT by query predicates.
 * The TenantContextFilter sets app.current_tenant_id before any of these
 * methods run, so a plain `findById(...)` will silently return empty for
 * cross-tenant ids.
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findBySha256(String sha256);

    Page<Document> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Document> findAllBySourceSessionIdOrderByCreatedAtDesc(
            UUID sourceSessionId, Pageable pageable);
}
