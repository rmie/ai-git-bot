package org.remus.giteabot.ai.google;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GoogleAiRequest {

    @JsonProperty("systemInstruction")
    private Content systemInstruction;

    private List<Content> contents;

    @JsonProperty("generationConfig")
    private GenerationConfig generationConfig;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Content {
        private String role;
        private List<Part> parts;
    }

    @Data
    @Builder
    public static class Part {
        private String text;
    }

    @Data
    @Builder
    public static class GenerationConfig {
        @JsonProperty("maxOutputTokens")
        private int maxOutputTokens;
    }
}
