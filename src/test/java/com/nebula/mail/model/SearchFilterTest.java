package com.nebula.mail.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SearchFilterTest {

    @Test
    @DisplayName("Should build default INBOX query")
    void testDefaultInboxQuery() {
        SearchFilter filter = new SearchFilter("INBOX", null, null, null, null, false);
        assertEquals("label:INBOX", filter.toGmailQuery());
    }

    @Test
    @DisplayName("Should build SENT folder query")
    void testSentFolderQuery() {
        SearchFilter filter = new SearchFilter("SENT", null, null, null, null, false);
        assertEquals("label:SENT", filter.toGmailQuery());
    }

    @Test
    @DisplayName("Should combine sender, keyword, date range and unread status into valid Gmail query")
    void testComplexFilterQuery() {
        SearchFilter filter = new SearchFilter(
                "INBOX",
                "urgent report",
                "sarah@company.com",
                "2026-09-01",
                "2026-09-07",
                true
        );

        String query = filter.toGmailQuery();
        assertTrue(query.contains("label:INBOX"));
        assertTrue(query.contains("from:(sarah@company.com)"));
        assertTrue(query.contains("is:unread"));
        assertTrue(query.contains("after:2026/09/01"));
        assertTrue(query.contains("before:2026/09/07"));
        assertTrue(query.contains("urgent report"));
    }

    @Test
    @DisplayName("Should handle empty and blank filters gracefully")
    void testEmptyFilter() {
        SearchFilter filter = new SearchFilter(null, "", "  ", null, "", false);
        assertEquals("", filter.toGmailQuery());
    }
}
