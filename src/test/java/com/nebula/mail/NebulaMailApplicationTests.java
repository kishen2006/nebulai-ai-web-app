package com.nebula.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "google.oauth.client-id=test-client-id",
        "google.oauth.client-secret=test-client-secret",
        "google.oauth.redirect-uri=http://localhost:8080/api/auth/google/callback",
        "gemini.api-key=test-api-key"
})
class NebulaMailApplicationTests {

    @Test
    @DisplayName("Should successfully load Spring Boot Application Context")
    void contextLoads() {
    }
}
