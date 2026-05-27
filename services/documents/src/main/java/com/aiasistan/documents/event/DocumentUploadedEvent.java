package com.aiasistan.documents.event;

import com.aiasistan.documents.domain.Document;

/**
 * Spring application event carried inside the upload transaction. Consumed
 * by {@link DocumentEventPublisher} *after* the tx commits, so by the time
 * the Redis Stream entry is visible, the DB row is too.
 *
 * Without AFTER_COMMIT semantics, Processing can race and try to UPDATE a
 * row that has not yet been committed — saw exactly that on the Phase E
 * smoke test before this rewire.
 */
public record DocumentUploadedEvent(Document document, String textObjectKey) {
}
