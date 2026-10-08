package com.vasu.cardanalyzer.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class QwenHealthCheck {

    private static final Logger log = LoggerFactory.getLogger(QwenHealthCheck.class);

    @Value("${qwen.health-url:http://localhost:9090/health}")
    private String healthUrl;

    @PostConstruct
    public void checkQwenHealth() {
        log.info("Checking Qwen health at {}", healthUrl);
        RestTemplate restTemplate = new RestTemplate();
        try {
            restTemplate.getForEntity(healthUrl, String.class);
            log.info("Qwen is up and running at {}", healthUrl);
        } catch (Exception e) {
            log.error("Failed to connect to Qwen health endpoint at {}: {}", healthUrl, e.getMessage());
            throw new IllegalStateException("Qwen is not available at " + healthUrl + ". Stopping Spring Boot startup.");
        }
    }
}
