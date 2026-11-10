package hu.primemobile.inventory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.primemobile.inventory.dto.ParserDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
@Slf4j
public class ParserClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RestClient restClient;

    public ParserClient(@Value("${app.parser.url:http://localhost:5000}") String parserUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(60));

        this.restClient = RestClient.builder()
                .baseUrl(parserUrl)
                .requestFactory(factory)
                .build();
        log.info("Initialized ParserClient targeting {}", parserUrl);
    }

    public ParserDto.ParsedInvoiceResponse parseInvoicePdf(MultipartFile file) {
        try {
            log.info("Sending PDF invoice '{}' to parser-service", file.getOriginalFilename());
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });

            return restClient.post()
                    .uri("/parse/pdf")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(ParserDto.ParsedInvoiceResponse.class);

        } catch (RestClientResponseException e) {
            String detail = extractDetailMessage(e);
            log.warn("Parser service rejected PDF '{}': {}", file.getOriginalFilename(), detail);
            throw new IllegalArgumentException(detail, e);
        } catch (IOException e) {
            log.error("Failed to read PDF file bytes", e);
            throw new IllegalArgumentException("Nem sikerült beolvasni a feltöltött PDF fájlt: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error communicating with parser-service", e);
            throw new RuntimeException("A PDF feldolgozó szolgáltatás jelenleg nem érhető el: " + e.getMessage(), e);
        }
    }

    public ParserDto.ParsedSpreadsheetResponse parseSpreadsheet(MultipartFile file) {
        try {
            log.info("Sending spreadsheet '{}' to parser-service", file.getOriginalFilename());
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });

            return restClient.post()
                    .uri("/parse/excel")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(ParserDto.ParsedSpreadsheetResponse.class);

        } catch (RestClientResponseException e) {
            String detail = extractDetailMessage(e);
            log.warn("Parser service rejected spreadsheet '{}': {}", file.getOriginalFilename(), detail);
            throw new IllegalArgumentException(detail, e);
        } catch (IOException e) {
            log.error("Failed to read spreadsheet file bytes", e);
            throw new IllegalArgumentException("Nem sikerült beolvasni a feltöltött táblázatot: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error communicating with parser-service for spreadsheet", e);
            throw new RuntimeException("A táblázat-feldolgozó szolgáltatás hibát jelzett: " + e.getMessage(), e);
        }
    }

    private String extractDetailMessage(RestClientResponseException e) {
        try {
            String rawBody = e.getResponseBodyAsString(StandardCharsets.UTF_8);
            if (rawBody != null && !rawBody.isBlank()) {
                JsonNode node = MAPPER.readTree(rawBody);
                if (node.hasNonNull("detail")) {
                    return node.get("detail").asText();
                }
                if (node.hasNonNull("message")) {
                    return node.get("message").asText();
                }
            }
        } catch (Exception ignored) {
        }
        return "A dokumentum feldolgozása sikertelen: ismeretlen számla- vagy táblázatformátum. Kérjük, jelezd a fejlesztőnek!";
    }
}
