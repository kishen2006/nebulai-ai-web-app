package com.nebula.mail.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Provides Spring Beans for Google HTTP Transport and JSON parsing.
 */
@Configuration
public class GmailClientConfig {

    @Bean
    public JsonFactory jsonFactory() {
        return GsonFactory.getDefaultInstance();
    }

    @Bean
    public NetHttpTransport netHttpTransport() throws GeneralSecurityException, IOException {
        return GoogleNetHttpTransport.newTrustedTransport();
    }
}
