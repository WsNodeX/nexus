package nexus.output;

import nexus.core.Json;
import nexus.core.errors.NexusException;
import nexus.llm.ChatRequest;
import nexus.llm.ChatResponse;
import nexus.llm.Message;
import nexus.llm.Provider;
import nexus.llm.Tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns free-form generation into a typed, validated object. It registers a hidden
 * tool whose parameters are the supplied {@link Schema}, forces the model to call
 * it via {@code tool_choice}, then parses and validates the returned arguments.
 *
 * <p>This is the structured-generation primitive: instead of hoping the model
 * emits JSON you can parse, you make the JSON schema the contract and verify it.</p>
 */
public class StructuredOutput {
    private final Provider provider;
    private final String model;

    public StructuredOutput(Provider provider, String model) {
        this.provider = provider;
        this.model = model;
    }

    /** Force a single structured object back from the model. */
    public Json call(List<Message> messages, String toolName, String description, Schema schema) {
        return call(messages, toolName, description, schema, true);
    }

    public Json call(List<Message> messages, String toolName, String description, Schema schema, boolean validate) {
        Tool tool = new Tool(toolName, description, schema.toJson());
        ChatRequest req = new ChatRequest(model);
        for (Message m : messages) req.add(m);
        req.add(tool);
        req.toolChoice(toolName);

        ChatResponse resp = provider.chat(req);
        if (!resp.hasToolCalls()) {
            throw new NexusException("model returned no tool call for structured output");
        }
        String args = resp.toolCalls.get(0).arguments;
        if (args == null || args.isEmpty()) args = "{}";
        Json parsed;
        try {
            parsed = Json.parse(args);
        } catch (RuntimeException e) {
            throw new NexusException("model returned invalid JSON arguments: " + args);
        }
        if (validate) {
            List<String> errors = validate(schema.toJson(), parsed);
            if (!errors.isEmpty()) {
                throw new NexusException("structured output failed validation: " + String.join("; ", errors));
            }
        }
        return parsed;
    }

    /** Validate a value against a JSON-Schema fragment; returns human-readable errors. */
    public static List<String> validate(Json schema, Json value) {
        List<String> errors = new ArrayList<>();
        validate("", schema, value, errors);
        return errors;
    }

    private static void validate(String path, Json schema, Json value, List<String> errors) {
        if (schema == null || schema.isNull()) return;
        String type = schema.has("type") ? schema.get("type").asString() : null;
        if (type != null) {
            boolean ok = switch (type) {
                case "object" -> value.t == Json.T.OBJ;
                case "array" -> value.t == Json.T.ARR;
                case "string" -> value.t == Json.T.STR;
                case "number" -> value.t == Json.T.NUM;
                case "integer" -> value.t == Json.T.NUM;
                case "boolean" -> value.t == Json.T.BOOL;
                case "null" -> value.t == Json.T.NUL;
                default -> true;
            };
            if (!ok) {
                errors.add(path.isEmpty() ? "expected type " + type : path + ": expected type " + type);
                return;
            }
        }
        if (type != null && type.equals("object") && value.t == Json.T.OBJ) {
            Json props = schema.get("properties");
            Json req = schema.get("required");
            if (req != null) {
                for (Json r : req.asArray()) {
                    String name = r.asString();
                    if (!value.has(name)) errors.add(path.isEmpty() ? "missing required '" + name + "'" : path + ": missing required '" + name + "'");
                }
            }
            if (props != null) {
                for (Map.Entry<String, Json> e : props.asObject().entrySet()) {
                    if (value.has(e.getKey())) {
                        validate(path.isEmpty() ? e.getKey() : path + "." + e.getKey(), e.getValue(), value.get(e.getKey()), errors);
                    }
                }
            }
        }
        if (schema.has("enum")) {
            String v = value.t == Json.T.STR ? value.asString() : value.toString();
            boolean found = false;
            for (Json en : schema.get("enum").asArray()) {
                if (en.asString().equals(v)) { found = true; break; }
            }
            if (!found) errors.add((path.isEmpty() ? "value" : path) + ": " + v + " not in enum");
        }
    }
}
