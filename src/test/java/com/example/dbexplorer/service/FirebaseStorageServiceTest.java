package com.example.dbexplorer.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the pure helper logic of {@link FirebaseStorageService}:
 * document-name resolution, folder-path sanitization and public-URL construction.
 * These cover the upload behaviour without needing live Firebase credentials.
 */
class FirebaseStorageServiceTest {

    // ---- resolveName ----------------------------------------------------

    @Test
    void usesDocumentNameWhenItHasExtension() {
        assertEquals("report.pdf", FirebaseStorageService.resolveName("report.pdf", "original.pdf"));
    }

    @Test
    void appendsOriginalExtensionWhenDocumentNameHasNone() {
        assertEquals("report.pdf", FirebaseStorageService.resolveName("report", "scan.pdf"));
    }

    @Test
    void fallsBackToOriginalWhenDocumentNameBlank() {
        assertEquals("scan.pdf", FirebaseStorageService.resolveName("", "scan.pdf"));
        assertEquals("scan.pdf", FirebaseStorageService.resolveName("   ", "scan.pdf"));
        assertEquals("scan.pdf", FirebaseStorageService.resolveName(null, "scan.pdf"));
    }

    @Test
    void keepsDocumentNameWhenOriginalHasNoExtension() {
        assertEquals("report", FirebaseStorageService.resolveName("report", "noext"));
    }

    @Test
    void trimsWhitespaceAroundDocumentName() {
        assertEquals("report.pdf", FirebaseStorageService.resolveName("  report.pdf  ", "x.pdf"));
    }

    // ---- sanitizePath ---------------------------------------------------

    @Test
    void stripsLeadingAndTrailingSlashes() {
        assertEquals("invoices", FirebaseStorageService.sanitizePath("/invoices/"));
        assertEquals("invoices", FirebaseStorageService.sanitizePath("invoices"));
        assertEquals("a/b", FirebaseStorageService.sanitizePath("/a/b/"));
    }

    @Test
    void emptyFolderStaysEmpty() {
        assertEquals("", FirebaseStorageService.sanitizePath(""));
        assertEquals("", FirebaseStorageService.sanitizePath("   "));
        assertEquals("", FirebaseStorageService.sanitizePath(null));
    }

    // ---- buildPublicUrl -------------------------------------------------

    @Test
    void buildsEncodedPublicUrl() {
        String url = FirebaseStorageService.buildPublicUrl("my-bucket.appspot.com", "invoices/report 2024.pdf");
        // Spaces must be percent-encoded (%20), NOT form-encoded ('+'), or GCS returns NoSuchKey.
        assertEquals("https://storage.googleapis.com/my-bucket.appspot.com/invoices/report%202024.pdf", url);
    }

    @Test
    void preservesPathSeparatorsWhileEncodingSegments() {
        String url = FirebaseStorageService.buildPublicUrl("b", "a/b/c.txt");
        assertEquals("https://storage.googleapis.com/b/a/b/c.txt", url);
    }
}
