package com.nebula.mail.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AiToolRegistryTest {

    private final AiToolRegistry toolRegistry = new AiToolRegistry();

    @Test
    @DisplayName("Should declare all required tools with valid Gemini schema")
    @SuppressWarnings("unchecked")
    void testGeminiToolsDeclaration() {
        List<Map<String, Object>> tools = toolRegistry.getGeminiToolsDeclaration();
        assertNotNull(tools);
        assertEquals(1, tools.size());

        List<Map<String, Object>> declarations = (List<Map<String, Object>>) tools.get(0).get("functionDeclarations");
        assertNotNull(declarations);
        assertEquals(7, declarations.size(), "Should define exactly 7 native AI tools");

        List<String> names = declarations.stream()
                .map(d -> (String) d.get("name"))
                .toList();

        assertTrue(names.contains("search_emails"));
        assertTrue(names.contains("navigate_folder"));
        assertTrue(names.contains("open_email"));
        assertTrue(names.contains("compose_email"));
        assertTrue(names.contains("reply_to_email"));
        assertTrue(names.contains("summarize_email"));
        assertTrue(names.contains("mark_as_read"));

        // Verify each tool has non-empty description and parameter schema
        for (Map<String, Object> tool : declarations) {
            assertNotNull(tool.get("name"), "Tool name must not be null");
            assertNotNull(tool.get("description"), "Tool description must not be null");
            Map<String, Object> params = (Map<String, Object>) tool.get("parameters");
            assertNotNull(params, "Tool parameters must be defined");
            assertEquals("OBJECT", params.get("type"));
        }
    }
}
