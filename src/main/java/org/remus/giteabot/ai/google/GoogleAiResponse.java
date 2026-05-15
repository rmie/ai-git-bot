package org.remus.giteabot.ai.google;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleAiResponse {

    private List<Candidate> candidates;

    @JsonProperty("usageMetadata")
    private UsageMetadata usageMetadata;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Candidate {
        private Content content;

        @JsonProperty("finishReason")
        private String finishReason;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Content {
        private String role;
        private List<Part> parts;
    }

    /**
     * Polymorphic response part. May carry plain {@code text} or, when the
     * model wants to invoke a tool (Step 6), a {@link #functionCall} block.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Part {
        private String text;
        @JsonProperty("functionCall")
        private FunctionCall functionCall;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FunctionCall {
        private String name;
        private Map<String, Object> args;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UsageMetadata {
        @JsonProperty("promptTokenCount")
        private int promptTokenCount;

        @JsonProperty("candidatesTokenCount")
        private int candidatesTokenCount;

        @JsonProperty("totalTokenCount")
        private int totalTokenCount;
    }
}

