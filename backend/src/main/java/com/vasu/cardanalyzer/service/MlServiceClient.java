package com.vasu.cardanalyzer.service;

import com.vasu.cardanalyzer.dto.ExtractionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class MlServiceClient {

    private final WebClient mlServiceWebClient;

    public ExtractionResult extract(MultipartFile file) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        try {
            builder.part("file", file.getBytes())
                    .filename(file.getOriginalFilename())
                    .contentType(MediaType.parseMediaType(file.getContentType()));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read uploaded file", e);
        }

        return mlServiceWebClient.post()
                .uri("/extract")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(ExtractionResult.class)
                .block();
    }
}
