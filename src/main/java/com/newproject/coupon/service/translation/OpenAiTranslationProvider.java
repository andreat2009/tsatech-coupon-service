package com.newproject.coupon.service.translation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newproject.coupon.config.CouponTranslationProperties;
import com.newproject.coupon.dto.LocalizedContent;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OpenAiTranslationProvider implements TranslationProvider {
    private static final Logger logger = LoggerFactory.getLogger(OpenAiTranslationProvider.class);
    private static final char[] DEFAULT_CACERTS_PASSWORD = "changeit".toCharArray();

    private final CouponTranslationProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiTranslationProvider(CouponTranslationProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public TranslationResult translateCouponContent(String sourceLanguage, LocalizedContent sourceContent, Set<String> targetLanguages) {
        TranslationResult result = new TranslationResult();

        if (targetLanguages == null || targetLanguages.isEmpty()) {
            return result;
        }

        if (!properties.isEnabled()) {
            result.getWarnings().add("Coupon translation is disabled");
            return result;
        }

        if (!"openai".equalsIgnoreCase(trimToNull(properties.getProvider()))) {
            result.getWarnings().add("Translation provider is not OpenAI");
            return result;
        }

        String apiKey = trimToNull(properties.getOpenai().getApiKey());
        if (apiKey == null) {
            result.getWarnings().add("OPENAI_API_KEY missing: translation skipped");
            return result;
        }

        String sourceName = trimToNull(sourceContent != null ? sourceContent.getName() : null);
        if (sourceName == null) {
            result.getWarnings().add("Missing source coupon name");
            return result;
        }

        try {
            String content = callOpenAi(
                apiKey,
                firstNonBlank(trimToNull(properties.getModel()), "gpt-4o-mini"),
                normalizeBaseUrl(firstNonBlank(trimToNull(properties.getOpenai().getBaseUrl()), "https://api.openai.com/v1")),
                sourceLanguage,
                sourceContent,
                targetLanguages
            );

            Map<String, LocalizedContent> translated = parseTranslations(content, targetLanguages);
            result.setTranslations(translated);
            if (translated.isEmpty()) {
                result.getWarnings().add("OpenAI response parsed but no translations were produced");
            }
        } catch (Exception ex) {
            logger.warn("OpenAI coupon translation failed: {}", ex.getMessage());
            result.getWarnings().add("OpenAI coupon translation failed: " + ex.getMessage());
        }

        return result;
    }

    private String callOpenAi(
        String apiKey,
        String model,
        String baseUrl,
        String sourceLanguage,
        LocalizedContent sourceContent,
        Set<String> targetLanguages
    ) throws IOException, InterruptedException {
        HttpClient client = buildOpenAiHttpClient();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("temperature", 0.2);
        payload.put("response_format", Map.of("type", "json_object"));
        payload.put("messages", List.of(
            Map.of(
                "role", "system",
                "content", "You translate ecommerce coupon titles. Return ONLY a valid JSON object with language codes as keys and field name. Keep coupon codes, brand names, and numbers unchanged. Escape quotes, backslashes, tabs and new lines with standard JSON escaping."
            ),
            Map.of(
                "role", "user",
                "content", buildUserPrompt(sourceLanguage, sourceContent, targetLanguages)
            )
        ));

        String body = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/chat/completions"))
            .timeout(Duration.ofMillis(Math.max(1000, properties.getTimeoutMs())))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + " from OpenAI: " + abbreviate(trimToNull(response.body()), 240));
        }

        JsonNode root = objectMapper.readTree(response.body());
        String content = root.path("choices").path(0).path("message").path("content").asText();
        String trimmed = trimToNull(content);
        if (trimmed == null) {
            throw new IOException("Empty OpenAI content");
        }
        return trimmed;
    }

    private HttpClient buildOpenAiHttpClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(Math.max(1000, properties.getTimeoutMs())));

        SSLContext context = loadJvmDefaultCacertsContext();
        if (context != null) {
            builder.sslContext(context);
        }

        return builder.build();
    }

    private SSLContext loadJvmDefaultCacertsContext() {
        String javaHome = trimToNull(System.getProperty("java.home"));
        if (javaHome == null) {
            return null;
        }

        List<Path> candidates = List.of(
            Path.of(javaHome, "lib", "security", "cacerts"),
            Path.of(javaHome, "jre", "lib", "security", "cacerts")
        );

        for (Path candidate : candidates) {
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            try (InputStream in = Files.newInputStream(candidate)) {
                KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                keyStore.load(in, DEFAULT_CACERTS_PASSWORD);

                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(keyStore);

                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());
                return sslContext;
            } catch (Exception ex) {
                logger.debug("Unable to load JVM cacerts from {}: {}", candidate, ex.getMessage());
            }
        }

        logger.warn("Unable to load JVM default cacerts; OpenAI HTTPS calls will use process truststore config");
        return null;
    }

    private Map<String, LocalizedContent> parseTranslations(String content, Set<String> targetLanguages) throws IOException {
        String jsonPayload = extractJsonPayload(content);
        JsonNode root = readModelJson(jsonPayload);

        JsonNode candidate = root.has("translations") && root.get("translations").isObject()
            ? root.get("translations")
            : root;

        Map<String, LocalizedContent> translations = new LinkedHashMap<>();
        for (String language : new LinkedHashSet<>(targetLanguages)) {
            JsonNode langNode = candidate.get(language);
            if (langNode == null || !langNode.isObject()) {
                continue;
            }

            LocalizedContent localized = new LocalizedContent();
            localized.setName(trimToNull(langNode.path("name").asText(null)));

            if (localized.getName() != null) {
                translations.put(language, localized);
            }
        }

        return translations;
    }



    private JsonNode readModelJson(String jsonPayload) throws IOException {
        try {
            return objectMapper.readTree(jsonPayload);
        } catch (IOException ex) {
            String sanitized = sanitizeModelJson(jsonPayload);
            if (!sanitized.equals(jsonPayload)) {
                return objectMapper.readTree(sanitized);
            }
            throw ex;
        }
    }

    private String sanitizeModelJson(String value) {
        StringBuilder sanitized = new StringBuilder(value.length() + 32);
        boolean inString = false;
        boolean escaping = false;

        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);

            if (escaping) {
                if (!isValidJsonEscape(current)) {
                    sanitized.append('\\');
                }
                sanitized.append(current);
                escaping = false;
                continue;
            }

            if (current == '"') {
                sanitized.append(current);
                inString = !inString;
                continue;
            }

            if (inString && current == '\\') {
                sanitized.append(current);
                escaping = true;
                continue;
            }

            if (inString && current < 0x20) {
                sanitized.append(escapeControlCharacter(current));
                continue;
            }

            sanitized.append(current);
        }

        if (escaping) {
            sanitized.append('\\');
        }

        return sanitized.toString();
    }

    private boolean isValidJsonEscape(char value) {
        return value == '"' || value == '\\' || value == '/' || value == 'b' || value == 'f'
            || value == 'n' || value == 'r' || value == 't' || value == 'u';
    }

    private String escapeControlCharacter(char value) {
        return switch (value) {
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            case '\b' -> "\\b";
            case '\f' -> "\\f";
            default -> String.format("\\u%04x", (int) value);
        };
    }
    private String extractJsonPayload(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```") && trimmed.contains("\n")) {
            int start = trimmed.indexOf('\n') + 1;
            int end = trimmed.lastIndexOf("```");
            if (end > start) {
                trimmed = trimmed.substring(start, end).trim();
            }
        }

        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }
        return trimmed;
    }

    private String buildUserPrompt(String sourceLanguage, LocalizedContent sourceContent, Set<String> targetLanguages) {
        String sourceName = firstNonBlank(trimToNull(sourceContent.getName()), "");

        return "Source language: " + sourceLanguage + "\n"
            + "Target languages: " + String.join(",", targetLanguages) + "\n"
            + "Coupon name: " + sourceName + "\n"
            + "Return JSON only, schema: {\"en\":{\"name\":\"...\"}, ...}";
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String abbreviate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen) + "...";
    }
}
