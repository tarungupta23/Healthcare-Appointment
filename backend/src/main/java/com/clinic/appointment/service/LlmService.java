package com.clinic.appointment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Wraps all LLM calls behind a single service so that failure handling -
 * timeouts, retries, and safe fallbacks - lives in one place, and so the
 * provider itself is swappable via config rather than code changes.
 *
 * Two providers are supported out of the box, selected by `app.llm.provider`:
 *
 *  - "anthropic" - Claude via the Messages API. Best model quality; new
 *    accounts get a one-time trial credit (not an ongoing free tier).
 *  - "gemini" (default) - Google Gemini via the AI Studio Generative
 *    Language API. Has a genuinely permanent free tier (no card, no
 *    expiry) on the Flash models, which is why it's the default here so
 *    the project keeps working for free indefinitely. Get a key at
 *    https://aistudio.google.com/apikey.
 *
 * Per the requirement "LLM failures must be handled gracefully, system
 * should not break": every public method here catches its own exceptions
 * and returns a well-formed fallback result rather than propagating an
 * error, so a booking or post-visit-notes submission NEVER fails just
 * because the LLM provider is down, misconfigured, or rate-limited.
 * Callers persist an explicit success/failure status alongside the
 * summary so staff can see which visits need a human-written summary.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.llm.provider:gemini}")
    private String provider;

    // ---- Anthropic config ----
    @Value("${app.llm.anthropic.api-key:}")
    private String anthropicApiKey;
    @Value("${app.llm.anthropic.api-url:https://api.anthropic.com/v1/messages}")
    private String anthropicApiUrl;
    @Value("${app.llm.anthropic.model:claude-sonnet-4-6}")
    private String anthropicModel;

    // ---- Gemini config ----
    @Value("${app.llm.gemini.api-key:}")
    private String geminiApiKey;
    @Value("${app.llm.gemini.api-url:https://generativelanguage.googleapis.com/v1beta/models}")
    private String geminiApiBaseUrl;
    @Value("${app.llm.gemini.model:gemini-2.5-flash}")
    private String geminiModel;

    // ---- Groq config (OpenAI-compatible chat completions API) ----
    @Value("${app.llm.groq.api-key:}")
    private String groqApiKey;
    @Value("${app.llm.groq.api-url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqApiUrl;
    @Value("${app.llm.groq.model:llama-3.3-70b-versatile}")
    private String groqModel;

    @Value("${app.llm.max-tokens:1024}")
    private int maxTokens;
    @Value("${app.llm.timeout-seconds:15}")
    private int timeoutSeconds;
    @Value("${app.llm.max-retries:2}")
    private int maxRetries;

    public static class LlmResult {
        public final boolean success;
        public final String rawText;
        public LlmResult(boolean success, String rawText) {
            this.success = success;
            this.rawText = rawText;
        }
    }

    /**
     * Pre-visit summary: urgency level, chief complaint, and three
     * suggested questions for the doctor, generated strictly as JSON so it
     * can be parsed and rendered in the doctor's UI reliably.
     */
    public LlmResult generatePreVisitSummary(String symptoms) {
        String prompt = "Analyse these symptoms and return ONLY a JSON object (no markdown fences, no prose) " +
                "with exactly these fields: " +
                "\"urgencyLevel\" (one of \"Low\", \"Medium\", \"High\"), " +
                "\"chiefComplaint\" (a short one-sentence summary), " +
                "\"suggestedQuestions\" (an array of exactly 3 short questions the doctor could ask the patient). " +
                "Symptoms: " + safeTruncate(symptoms, 4000);

        return callLlm(prompt);
    }

    /**
     * Post-visit summary: converts clinical notes + prescription into a
     * patient-friendly explanation with medication schedule and follow-up
     * steps, in plain text (this one is shown directly to the patient, not
     * parsed by the app).
     */
    public LlmResult generatePostVisitSummary(String clinicalNotes, String prescriptionSummary, String followUp) {
        String prompt = "Convert these clinical notes into a patient-friendly summary with a medication " +
                "schedule and follow-up steps. Use simple, warm, non-alarming language a patient with no " +
                "medical background can understand. Keep it under 250 words. Do not use markdown headers.\n\n" +
                "Clinical notes: " + safeTruncate(clinicalNotes, 4000) + "\n\n" +
                "Prescription: " + safeTruncate(prescriptionSummary, 2000) + "\n\n" +
                "Additional follow-up instructions from the doctor: " + safeTruncate(followUp, 1000);

        return callLlm(prompt);
    }

    private LlmResult callLlm(String userPrompt) {
        String p = provider == null ? "gemini" : provider.toLowerCase();

        return switch (p) {
            case "anthropic" -> {
                if (anthropicApiKey == null || anthropicApiKey.isBlank()) {
                    log.warn("Anthropic API key not configured (app.llm.provider=anthropic); skipping LLM call and returning fallback.");
                    yield new LlmResult(false, null);
                }
                yield callAnthropic(userPrompt);
            }
            case "groq" -> {
                if (groqApiKey == null || groqApiKey.isBlank()) {
                    log.warn("Groq API key not configured (app.llm.provider=groq); skipping LLM call and returning fallback.");
                    yield new LlmResult(false, null);
                }
                yield callGroq(userPrompt);
            }
            default -> { // "gemini"
                if (geminiApiKey == null || geminiApiKey.isBlank()) {
                    log.warn("Gemini API key not configured (app.llm.provider=gemini); skipping LLM call and returning fallback.");
                    yield new LlmResult(false, null);
                }
                yield callGemini(userPrompt);
            }
        };
    }

    private LlmResult callGroq(String userPrompt) {
        // OpenAI-compatible chat completions format.
        Map<String, Object> body = Map.of(
                "model", groqModel,
                "max_tokens", maxTokens,
                "messages", List.of(Map.of("role", "user", "content", userPrompt))
        );

        try {
            String responseBody = webClient.post()
                    .uri(groqApiUrl)
                    .header("Authorization", "Bearer " + groqApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .retryWhen(Retry.backoff(maxRetries, Duration.ofSeconds(1)).filter(this::isRetryable))
                    .block();

            String text = extractGroqText(responseBody);
            if (text == null || text.isBlank()) {
                log.error("Groq returned an empty response body");
                return new LlmResult(false, null);
            }
            return new LlmResult(true, text.trim());

        } catch (Exception ex) {
            log.error("Groq call failed after retries: {}", ex.getMessage());
            return new LlmResult(false, null);
        }
    }

    private String extractGroqText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                return choices.get(0).path("message").path("content").asText();
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to parse Groq response JSON: {}", e.getMessage());
            return null;
        }
    }

    private LlmResult callAnthropic(String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", anthropicModel,
                "max_tokens", maxTokens,
                "messages", List.of(Map.of("role", "user", "content", userPrompt))
        );

        try {
            String responseBody = webClient.post()
                    .uri(anthropicApiUrl)
                    .header("x-api-key", anthropicApiKey)
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .retryWhen(Retry.backoff(maxRetries, Duration.ofSeconds(1)).filter(this::isRetryable))
                    .block();

            String text = extractAnthropicText(responseBody);
            if (text == null || text.isBlank()) {
                log.error("Anthropic returned an empty response body");
                return new LlmResult(false, null);
            }
            return new LlmResult(true, text.trim());

        } catch (Exception ex) {
            log.error("Anthropic call failed after retries: {}", ex.getMessage());
            return new LlmResult(false, null);
        }
    }

    private LlmResult callGemini(String userPrompt) {
        String uri = geminiApiBaseUrl + "/" + geminiModel + ":generateContent?key=" + geminiApiKey;

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", userPrompt)))),
                "generationConfig", Map.of("maxOutputTokens", maxTokens)
        );

        try {
            String responseBody = webClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .retryWhen(Retry.backoff(maxRetries, Duration.ofSeconds(1)).filter(this::isRetryable))
                    .block();

            String text = extractGeminiText(responseBody);
            if (text == null || text.isBlank()) {
                log.error("Gemini returned an empty response body");
                return new LlmResult(false, null);
            }
            return new LlmResult(true, text.trim());

        } catch (Exception ex) {
            log.error("Gemini call failed after retries: {}", ex.getMessage());
            return new LlmResult(false, null);
        }
    }

    private boolean isRetryable(Throwable throwable) {
        // Retry on network/timeout errors; a bad request or auth error
        // (400/401/403/429 quota) would just fail again immediately, so we
        // don't spin on those - 429 in particular should back off far
        // longer than a request-scoped retry budget allows.
        String msg = throwable.getMessage();
        if (msg == null) return true;
        return !(msg.contains("400") || msg.contains("401") || msg.contains("403") || msg.contains("429"));
    }

    private String extractAnthropicText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("content");
            if (content.isArray() && content.size() > 0) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode block : content) {
                    if ("text".equals(block.path("type").asText())) {
                        sb.append(block.path("text").asText());
                    }
                }
                return sb.toString();
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to parse Anthropic response JSON: {}", e.getMessage());
            return null;
        }
    }

    private String extractGeminiText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray() && parts.size() > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (JsonNode part : parts) {
                        sb.append(part.path("text").asText());
                    }
                    return sb.toString();
                }
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to parse Gemini response JSON: {}", e.getMessage());
            return null;
        }
    }

    private String safeTruncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) : text;
    }
}
