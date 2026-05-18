package org.remus.giteabot.prworkflow.config;

import org.remus.giteabot.agent.shared.AgentJackson;
import org.remus.giteabot.prworkflow.WorkflowParamField;
import org.remus.giteabot.prworkflow.WorkflowParamsSchema;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates and normalises the {@code params_json} payload stored on a
 * {@link WorkflowSelection} against the
 * {@link WorkflowParamsSchema} declared by the corresponding
 * {@link org.remus.giteabot.prworkflow.PrWorkflow}.
 *
 * <p>Behaviour:</p>
 * <ul>
 *     <li>Missing keys for non-required fields are filled in from
 *     {@link WorkflowParamField#defaultValue()} when present, otherwise
 *     dropped.</li>
 *     <li>Required fields must be present and non-blank; otherwise an
 *     {@link IllegalArgumentException} with a human-friendly message is
 *     thrown.</li>
 *     <li>Values are type-coerced (e.g. {@code "42"} → JSON number) so the
 *     persisted JSON is canonical and consumers can rely on typed access.</li>
 *     <li>Unknown keys (not declared in the schema) are silently dropped — the
 *     UI never submits them, but old rows after a workflow downgrade should
 *     not break.</li>
 * </ul>
 */
@Component
public class WorkflowParamsValidator {

    private static final ObjectMapper MAPPER = AgentJackson.mapper();

    /**
     * @param paramsJson raw JSON object (may be {@code null}, blank, or a
     *                   non-object — treated as empty input)
     * @param schema     descriptor of allowed/required fields
     * @return canonical JSON representation suitable for persistence
     * @throws IllegalArgumentException when validation fails
     */
    public String validateAndCanonicalise(String paramsJson, WorkflowParamsSchema schema) {
        JsonNode parsed = parse(paramsJson);
        ObjectNode normalised = JsonNodeFactory.instance.objectNode();
        List<String> errors = new ArrayList<>();

        for (WorkflowParamField field : schema.fields()) {
            JsonNode value = parsed != null && parsed.isObject() ? parsed.get(field.name()) : null;
            if (value == null || value.isNull() || (value.isString() && value.asString().isBlank())) {
                if (field.defaultValue() != null && !field.defaultValue().isBlank()) {
                    value = coerce(field.defaultValue(), field, errors);
                } else if (field.required()) {
                    errors.add("Parameter '" + field.label() + "' is required");
                    continue;
                } else {
                    continue;
                }
            } else {
                value = coerce(value, field, errors);
            }
            if (value != null) {
                normalised.set(field.name(), value);
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
        try {
            return MAPPER.writeValueAsString(normalised);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to serialise workflow params", e);
        }
    }

    /**
     * Convenience for callers that already hold a typed map.
     */
    public String validateAndCanonicalise(Map<String, ?> params, WorkflowParamsSchema schema) {
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        if (params != null) {
            for (Map.Entry<String, ?> entry : params.entrySet()) {
                Object value = entry.getValue();
                if (value == null) {
                    input.putNull(entry.getKey());
                } else {
                    input.put(entry.getKey(), value.toString());
                }
            }
        }
        return validateAndCanonicalise(input.toString(), schema);
    }

    /**
     * Parses a persisted params JSON back into a typed map suitable for
     * passing to a workflow at runtime. Returns an empty map when the
     * payload is null/blank.
     */
    public Map<String, Object> parseToMap(String paramsJson) {
        JsonNode parsed = parse(paramsJson);
        Map<String, Object> result = new LinkedHashMap<>();
        if (parsed == null || !parsed.isObject()) {
            return result;
        }
        for (Map.Entry<String, JsonNode> entry : parsed.properties()) {
            JsonNode v = entry.getValue();
            if (v == null || v.isNull()) {
                result.put(entry.getKey(), null);
            } else if (v.isBoolean()) {
                result.put(entry.getKey(), v.asBoolean());
            } else if (v.isInt() || v.isLong()) {
                result.put(entry.getKey(), v.asLong());
            } else if (v.isNumber()) {
                result.put(entry.getKey(), v.asDouble());
            } else {
                result.put(entry.getKey(), v.asString());
            }
        }
        return result;
    }

    private JsonNode parse(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(paramsJson);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid JSON for workflow params: " + e.getMessage());
        }
    }

    private JsonNode coerce(Object raw, WorkflowParamField field, List<String> errors) {
        String text = raw instanceof JsonNode node ? (node.isString() ? node.asString() : node.toString())
                : raw == null ? null : raw.toString();
        if (raw instanceof JsonNode node && !node.isString()) {
            // Already typed (boolean / number) — convert via stringification then re-parse below.
            text = node.asString();
        }
        if (text == null) {
            if (field.required()) {
                errors.add("Parameter '" + field.label() + "' is required");
            }
            return null;
        }
        String trimmed = text.trim();
        return switch (field.type()) {
            case STRING, TEXT, SECRET -> JsonNodeFactory.instance.stringNode(trimmed);
            case BOOLEAN -> {
                if (trimmed.equalsIgnoreCase("true") || trimmed.equals("1") || trimmed.equalsIgnoreCase("on")) {
                    yield JsonNodeFactory.instance.booleanNode(true);
                }
                if (trimmed.equalsIgnoreCase("false") || trimmed.equals("0") || trimmed.equalsIgnoreCase("off")
                        || trimmed.isEmpty()) {
                    yield JsonNodeFactory.instance.booleanNode(false);
                }
                errors.add("Parameter '" + field.label() + "' must be a boolean (true/false)");
                yield null;
            }
            case INTEGER -> {
                try {
                    yield JsonNodeFactory.instance.numberNode(Long.parseLong(trimmed));
                } catch (NumberFormatException nfe) {
                    errors.add("Parameter '" + field.label() + "' must be an integer");
                    yield null;
                }
            }
        };
    }
}


