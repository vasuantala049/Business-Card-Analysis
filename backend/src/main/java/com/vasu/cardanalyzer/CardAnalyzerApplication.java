package com.vasu.cardanalyzer;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CardAnalyzerApplication {
    public static void main(String[] args) {
        loadDotEnvIfPresent();
        SpringApplication.run(CardAnalyzerApplication.class, args);
    }

    private static void loadDotEnvIfPresent() {
        List<Path> candidates = List.of(Path.of(".env"), Path.of("..", ".env"));

        for (Path candidate : candidates) {
            if (!Files.isRegularFile(candidate)) {
                continue;
            }

            try (BufferedReader reader = Files.newBufferedReader(candidate)) {
                reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#") && line.contains("="))
                        .forEach(line -> {
                            int separatorIndex = line.indexOf('=');
                            String key = line.substring(0, separatorIndex).trim();
                            String value = line.substring(separatorIndex + 1).trim();

                            if (!key.isEmpty() && System.getProperty(key) == null && System.getenv(key) == null) {
                                System.setProperty(key, value);
                            }
                        });
                return;
            } catch (IOException ignored) {
                // If loading the .env file fails, fall back to normal environment/property resolution.
            }
        }
    }
}
