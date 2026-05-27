package com.aiasistan.documents.parse;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;

/**
 * Apache Tika facade — turn a binary upload into plain text + a content-type
 * guess. AutoDetectParser sniffs magic bytes and dispatches to the right
 * concrete parser (PDFBox for PDFs, POI for Office, mboxParser for emails…),
 * so we only have one entry point regardless of upload type.
 *
 * KISS: the cap on extracted text length is intentional. Without it Tika
 * happily streams a 50 MB PDF into a 50 MB String, which then sits in
 * Postgres TOAST until the next ingestion run. Phase E (chunking) reads
 * the full text directly from MinIO and ignores this field.
 */
@Service
public class DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(DocumentParser.class);

    /** Tika's BodyContentHandler default is 100k chars; we want the full body. */
    private static final int MAX_TEXT_CHARS = -1;

    /** What we store on the metadata row — a short preview only. */
    private static final int PREVIEW_CHARS = 4096;

    /**
     * @param contentType  MIME type sniffed by Tika.
     * @param previewText  First {@value #PREVIEW_CHARS} chars of body —
     *                     useful for UI snippets.
     * @param fullText     Full extracted body. Processing writes this to
     *                     MinIO and the metadata row never carries it.
     */
    public record ParsedDocument(String contentType, String previewText, String fullText) { }

    public ParsedDocument parse(InputStream in, String filenameHint) {
        try {
            BodyContentHandler handler = new BodyContentHandler(MAX_TEXT_CHARS);
            Metadata metadata = new Metadata();
            if (filenameHint != null) {
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filenameHint);
            }
            AutoDetectParser parser = new AutoDetectParser();
            parser.parse(in, handler, metadata, new ParseContext());

            String contentType = metadata.get(Metadata.CONTENT_TYPE);
            String body = handler.toString();
            String preview = body.length() > PREVIEW_CHARS
                    ? body.substring(0, PREVIEW_CHARS)
                    : body;
            log.info("tika.parsed contentType={} bodyLength={}", contentType, body.length());
            return new ParsedDocument(contentType, preview, body);
        } catch (IOException | SAXException | TikaException e) {
            throw new IllegalArgumentException("Tika failed to parse the upload: " + e.getMessage(), e);
        }
    }
}
