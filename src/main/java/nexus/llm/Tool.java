package nexus.llm;

import nexus.core.Json;

/**
 * A function the model is allowed to call. {@code parameters} is a JSON Schema
 * object describing the arguments, built with {@link nexus.core.Json}.
 */
public class Tool {
    public String name;
    public String description;
    public Json parameters;

    public Tool() {}

    public Tool(String name, String description, Json parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }

    public static Tool of(String name, String description, Json parameters) {
        return new Tool(name, description, parameters);
    }

    /** Serialize to the OpenAI {@code tools[]} shape. */
    public Json toJson() {
        return Json.obj()
                .put("type", "function")
                .put("function", Json.obj()
                        .put("name", name)
                        .put("description", description)
                        .put("parameters", parameters == null ? Json.obj() : parameters));
    }
}
