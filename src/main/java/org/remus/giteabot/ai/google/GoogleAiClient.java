package org.remus.giteabot.ai.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.remus.giteabot.ai.AbstractAiClient;
import org.remus.giteabot.ai.AiMessage;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AI client implementation for Google's Gemini REST API.
 */
@Slf4j
public class GoogleAiClient extends AbstractAiClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;

    public GoogleAiClient(RestClient restClient, String model, int maxTokens,
                          int maxDiffCharsPerChunk, int maxDiffChunks,
                          int retryTruncatedChunkChars) {
        super(model, maxTokens, maxDiffCharsPerChunk, maxDiffChunks, retryTruncatedChunkChars);
        this.restClient = restClient;
    }

    @Override
    protected String sendReviewRequest(String systemPrompt, String effectiveModel,
                                       int maxTokens, String userMessage) {
        GoogleAiRequest request = GoogleAiRequest.builder()
                .systemInstruction(content(null, systemPrompt))
                .contents(List.of(content("user", userMessage)))
                .generationConfig(GoogleAiRequest.GenerationConfig.builder()
                        .maxOutputTokens(maxTokens)
                        .build())
                .build();

        return doRequest(effectiveModel, request, "review");
    }

    @Override
    protected String sendChatRequest(String systemPrompt, String effectiveModel,
                                     int maxTokens, List<AiMessage> messages) {
        List<GoogleAiRequest.Content> contents = new ArrayList<>();
        for (AiMessage message : messages) {
            contents.add(content(toGoogleRole(message.getRole()), message.getContent()));
        }

        GoogleAiRequest request = GoogleAiRequest.builder()
                .systemInstruction(content(null, systemPrompt))
                .contents(contents)
                .generationConfig(GoogleAiRequest.GenerationConfig.builder()
                        .maxOutputTokens(maxTokens)
                        .build())
                .build();

        return doRequest(effectiveModel, request, "chat");
    }

    @Override
    protected boolean isPromptTooLongError(HttpClientErrorException e) {
        String body = e.getResponseBodyAsString();
        if (body == null) {
            return false;
        }
        String normalized = body.toLowerCase(Locale.ROOT);
        return normalized.contains("input token count")
                || normalized.contains("maximum number of tokens")
                || normalized.contains("exceeds the maximum")
                || normalized.contains("token limit");
    }

    private String doRequest(String model, GoogleAiRequest request, String context) {
        try {
            GoogleAiResponse response = restClient.post()
                    .uri("/v1beta/" + toModelPath(model) + ":generateContent")
                    .body(request)
                    .retrieve()
                    .body(GoogleAiResponse.class);

            return extractText(response, context);
        } catch (HttpClientErrorException e) {
            if (isPromptTooLongError(e)) {
                throw e;
            }
            throw new IllegalStateException("Google AI request failed: " + safeErrorMessage(e), e);
        }
    }

    private String extractText(GoogleAiResponse response, String context) {
        if (response == null || response.getCandidates() == null || response.getCandidates().isEmpty()) {
            log.warn("Empty response from Google AI API");
            return "Unable to generate " + context + " - empty response from AI.";
        }

        GoogleAiResponse.Candidate firstCandidate = response.getCandidates().getFirst();
        if (firstCandidate == null
                || firstCandidate.getContent() == null
                || firstCandidate.getContent().getParts() == null
                || firstCandidate.getContent().getParts().isEmpty()) {
            log.warn("Empty response from Google AI API");
            return "Unable to generate " + context + " - empty response from AI.";
        }

        String result = firstCandidate.getContent().getParts().stream()
                .map(GoogleAiResponse.Part::getText)
                .filter(text -> text != null && !text.isBlank())
                .reduce("", (a, b) -> a + b);

        if (result.isBlank()) {
            log.warn("Empty text response from Google AI API");
            return "Unable to generate " + context + " - empty response from AI.";
        }

        if (response.getUsageMetadata() != null) {
            log.info("Google AI {} response: {} prompt tokens, {} candidate tokens",
                    context,
                    response.getUsageMetadata().getPromptTokenCount(),
                    response.getUsageMetadata().getCandidatesTokenCount());
        }

        return result;
    }

    private GoogleAiRequest.Content content(String role, String text) {
        return GoogleAiRequest.Content.builder()
                .role(role)
                .parts(List.of(GoogleAiRequest.Part.builder().text(text).build()))
                .build();
    }

    private String toGoogleRole(String role) {
        if ("assistant".equals(role)) {
            return "model";
        }
        return "user";
    }

    private String toModelPath(String model) {
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("Google AI integration requires a model");
        }
        String trimmed = model.trim();
        if (trimmed.startsWith("/") || trimmed.contains("?") || trimmed.contains("#")) {
            throw new IllegalStateException("Invalid Google AI model name: " + model);
        }
        return trimmed.startsWith("models/") ? trimmed : "models/" + trimmed;
    }

    private String safeErrorMessage(HttpClientErrorException e) {
        String responseBody = e.getResponseBodyAsString();
        String message = extractGoogleErrorMessage(responseBody);
        if (message == null || message.isBlank()) {
            message = e.getStatusText();
        }
        return e.getStatusCode() + " " + message;
    }

    private String extractGoogleErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode message = OBJECT_MAPPER.readTree(responseBody).path("error").path("message");
            return message.isTextual() ? message.asText() : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
