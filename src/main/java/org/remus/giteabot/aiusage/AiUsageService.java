package org.remus.giteabot.aiusage;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

/**
 * Records and queries the audit log of AI provider interactions: token usage
 * of successful calls and details (including stack traces) of failed calls.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiUsageService {

    /** Page size of the tables on the "Usage" page. */
    public static final int PAGE_SIZE = 20;

    /** Page size for streaming exports — keeps heap bounded (~10 MB worst case). */
    private static final int EXPORT_PAGE_SIZE = 100;

    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;
    private static final int MAX_STACK_TRACE_LENGTH = 100_000;
    private static final Set<String> USAGE_SORT_COLUMNS =
            Set.of("timestamp", "aiIntegrationName", "sessionId", "inputTokens", "outputTokens");
    private static final Set<String> ERROR_SORT_COLUMNS =
            Set.of("timestamp", "aiIntegrationName", "sessionId", "errorMessage");

    private final AiUsageLogRepository usageRepository;
    private final AiErrorLogRepository errorRepository;
    private final MeterRegistry meterRegistry;

    /**
     * Records the token usage of a single AI interaction. Persistence problems
     * are logged but never propagated so that auditing can never break the
     * actual AI workflow.
     */
    @Transactional
    public void recordUsage(String aiIntegrationName, String sessionId,
                            long inputTokens, long outputTokens) {
        try {
            AiUsageLog entry = new AiUsageLog();
            entry.setTimestamp(Instant.now());
            entry.setAiIntegrationName(aiIntegrationName);
            entry.setSessionId(sessionId);
            entry.setInputTokens(inputTokens);
            entry.setOutputTokens(outputTokens);
            usageRepository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to persist AI usage entry: {}", e.getMessage());
        }
    }

    /**
     * Records token usage and increments Prometheus counters tagged with
     * integration, provider, model, repo and activity dimensions.
     */
    @Transactional
    public void recordUsage(String aiIntegrationName, String providerType, String model,
                            String sessionId, String repo, String activityType,
                            long inputTokens, long outputTokens) {
        // 1. Delegate to existing DB recording
        recordUsage(aiIntegrationName, sessionId, inputTokens, outputTokens);

        // 2. Increment Prometheus counters
        incrementCounter("ai.input_tokens_total",
                "Total input/prompt tokens consumed",
                aiIntegrationName, providerType, model, repo, activityType, inputTokens);
        incrementCounter("ai.output_tokens_total",
                "Total output/completion tokens consumed",
                aiIntegrationName, providerType, model, repo, activityType, outputTokens);
                
        // 3. Record histograms for Pareto distribution analysis (excluding repo/model for cardinality)
        recordDistribution("ai.input_tokens_per_call",
                "Distribution of input tokens per API call",
                aiIntegrationName, providerType, activityType, inputTokens);
        recordDistribution("ai.output_tokens_per_call",
                "Distribution of output tokens per API call",
                aiIntegrationName, providerType, activityType, outputTokens);
    }

    /**
     * Records a failed AI interaction with its stack trace. Persistence
     * problems are logged but never propagated.
     */
    @Transactional
    public void recordError(String aiIntegrationName, String sessionId, Throwable error) {
        try {
            AiErrorLog entry = new AiErrorLog();
            entry.setTimestamp(Instant.now());
            entry.setAiIntegrationName(aiIntegrationName);
            entry.setSessionId(sessionId);
            entry.setErrorMessage(truncate(error.getMessage() != null
                    ? error.getMessage() : error.getClass().getName(), MAX_ERROR_MESSAGE_LENGTH));
            entry.setStackTrace(truncate(stackTraceOf(error), MAX_STACK_TRACE_LENGTH));
            errorRepository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to persist AI error entry: {}", e.getMessage());
        }
    }

    /**
     * Removes all recorded AI usage entries.
     */
    @Transactional
    public void clearUsage() {
        usageRepository.deleteAllInBatch();
    }

    @Transactional(readOnly = true)
    public Page<AiUsageLog> findUsage(Instant from, Instant to, int page,
                                      String sortColumn, boolean ascending) {
        String column = USAGE_SORT_COLUMNS.contains(sortColumn) ? sortColumn : "timestamp";
        return usageRepository.findByTimestampBetween(effectiveFrom(from), effectiveTo(to),
                PageRequest.of(page, PAGE_SIZE, sortOf(column, ascending)));
    }

    @Transactional(readOnly = true)
    public Page<AiErrorLog> findErrors(Instant from, Instant to, int page,
                                       String sortColumn, boolean ascending) {
        String column = ERROR_SORT_COLUMNS.contains(sortColumn) ? sortColumn : "timestamp";
        return errorRepository.findByTimestampBetween(effectiveFrom(from), effectiveTo(to),
                PageRequest.of(page, PAGE_SIZE, sortOf(column, ascending)));
    }

    /**
     * Streams all error entries in the given timespan as a JSON array directly
     * to the provided output stream.  Results are fetched in pages of
     * {@value #EXPORT_PAGE_SIZE} rows so that heap usage stays bounded
     * regardless of the total number of matching rows.
     *
     * <p>This method intentionally does <em>not</em> carry a
     * {@code @Transactional} annotation — each page query opens its own
     * short-lived read-only transaction via the Spring Data proxy, which is
     * the correct pattern for streaming responses that outlive a single
     * transaction.</p>
     */
    public void exportErrors(Instant from, Instant to, OutputStream outputStream) throws IOException {
        Instant f = effectiveFrom(from);
        Instant t = effectiveTo(to);

        try (JsonGenerator gen = new ObjectMapper().getFactory().createGenerator(outputStream)) {
            gen.writeStartArray();
            int page = 0;
            Page<AiErrorLog> result;
            do {
                result = errorRepository.findAllByTimestampBetweenOrderByTimestampDesc(
                        f, t, PageRequest.of(page, EXPORT_PAGE_SIZE));
                for (AiErrorLog entry : result.getContent()) {
                    gen.writeStartObject();
                    gen.writeObjectField("timestamp", entry.getTimestamp());
                    gen.writeStringField("aiIntegration", entry.getAiIntegrationName());
                    gen.writeStringField("sessionId", entry.getSessionId());
                    gen.writeStringField("errorMessage", entry.getErrorMessage());
                    gen.writeStringField("stackTrace", entry.getStackTrace());
                    gen.writeEndObject();
                }
                gen.flush();
                page++;
            } while (result.hasNext());
            gen.writeEndArray();
        }
    }

    /**
     * Number of AI errors recorded after the given instant (dashboard badge).
     */
    @Transactional(readOnly = true)
    public long countErrorsSince(Instant after) {
        return errorRepository.countByTimestampAfter(after);
    }

    /**
     * Total input tokens consumed across all recorded AI interactions.
     */
    @Transactional(readOnly = true)
    public long totalInputTokens() {
        return usageRepository.sumInputTokens();
    }

    /**
     * Total output tokens consumed across all recorded AI interactions.
     */
    @Transactional(readOnly = true)
    public long totalOutputTokens() {
        return usageRepository.sumOutputTokens();
    }

    private static Sort sortOf(String column, boolean ascending) {
        Sort sort = Sort.by(ascending ? Sort.Direction.ASC : Sort.Direction.DESC, column);
        if (!"timestamp".equals(column)) {
            sort = sort.and(Sort.by(Sort.Direction.DESC, "timestamp"));
        }
        return sort;
    }

    private static Instant effectiveFrom(Instant from) {
        return from != null ? from : Instant.EPOCH;
    }

    private static Instant effectiveTo(Instant to) {
        return to != null ? to : Instant.now().plusSeconds(60);
    }

    private static String stackTraceOf(Throwable error) {
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private void incrementCounter(String metricName, String description,
                                  String aiIntegration, String provider, String model,
                                  String repo, String activityType, long amount) {
        if (amount <= 0) {
            return;
        }
        String safeIntegration = normalise(aiIntegration);
        String safeProvider = normalise(provider);
        String safeModel = normalise(model);
        String safeRepo = normalise(repo);
        String safeActivity = normalise(activityType);
        
        Counter.builder(metricName)
                .description(description)
                .tag("ai_integration", safeIntegration)
                .tag("provider", safeProvider)
                .tag("model", safeModel)
                .tag("repo", safeRepo)
                .tag("activity", safeActivity)
                .register(meterRegistry)
                .increment(amount);
    }

    private void recordDistribution(String metricName, String description,
                                    String aiIntegration, String provider, String activityType, long amount) {
        if (amount <= 0) {
            return;
        }
        DistributionSummary.builder(metricName)
                .description(description)
                .publishPercentileHistogram()
                .tag("ai_integration", normalise(aiIntegration))
                .tag("provider", normalise(provider))
                .tag("activity", normalise(activityType))
                .register(meterRegistry)
                .record(amount);
    }

    private static String normalise(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
