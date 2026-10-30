package nexus.output;

import nexus.core.Json;

import java.util.Arrays;
import java.util.List;

/**
 * Fluent builder for a JSON-Schema fragment describing the shape of a structured
 * completion. Produces exactly the {@code parameters} object an LLM tool/function
 * expects, so callers never hand-write schema JSON.
 *
 * <pre>{@code
 * Schema s = Schema.create()
 *     .property("city", "string").required("city")
 *     .property("unit", "string").enumOf("unit", List.of("c","f"));
 * }</pre>
 */
public class Schema {
    private final Json node;

    private Schema(Json node) { this.node = node; }

    public static Schema create() {
        return new Schema(Json.obj()
                .put("type", "object")
                .put("properties", Json.obj())
                .put("required", Json.arr()));
    }

    public Schema property(String name, String type) {
        node.get("properties").put(name, Json.obj().put("type", type));
        return this;
    }

    public Schema property(String name, Schema nested) {
        node.get("properties").put(name, nested.node);
        return this;
    }

    public Schema required(String name) {
        node.get("required").add(name);
        return this;
    }

    public Schema enumOf(String name, List<String> values) {
        Json arr = Json.arr();
        for (String v : values) arr.add(v);
        node.get("properties").get(name).put("enum", arr);
        return this;
    }

    public Schema description(String text) {
        node.put("description", text);
        return this;
    }

    public Json toJson() {
        return node;
    }
}
