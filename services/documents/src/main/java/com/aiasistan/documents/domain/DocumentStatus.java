package com.aiasistan.documents.domain;

public enum DocumentStatus {
    UPLOADED,
    PARSING,
    CHUNKING,
    EMBEDDING,
    READY,
    FAILED
}
