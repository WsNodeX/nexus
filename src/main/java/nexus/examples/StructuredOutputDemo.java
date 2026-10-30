package nexus.examples;

import nexus.core.Json;
import nexus.llm.ChatRequest;
import nexus.llm.ChatResponse;
import nexus.llm.Message;
import nexus.llm.Provider;
import nexus.llm.ScriptedProvider;
import nexus.llm.ToolCall;
import nexus.output.Schema;
import nexus.output.StructuredOutput;

import java.util.List;

/**
 * Shows structured generation: the model is forced to return a JSON object that
 * matches a schema, and the arguments are validated before being returned as a
 * typed {@link Json}. Offline here via a scripted provider that emits the tool
 * call directly.
 *
 * <p>Run: {@code java nexus.examples.StructuredOutputDemo}</p>
 */
public class StructuredOutputDemo {

    public static void main(String[] args) {
        // Scripted provider returns exactly the tool call we expect.
        Provider p = new ScriptedProvider("fake-llm", req -> {
            ChatResponse r = new ChatResponse();
            r.toolCalls = List.of(new ToolCall("call_1", "extract_weather",
                    "{\"city\":\"Paris\",\"unit\":\"c\",\"humidity\":0.62}"));
            return r;
        });

        StructuredOutput so = new StructuredOutput(p, "demo-model");
        Schema schema = Schema.create()
                .property("city", "string").required("city")
                .property("unit", "string").enumOf("unit", List.of("c", "f"))
                .property("humidity", "number");

        Json result = so.call(
                List.of(Message.user("What's the weather in Paris, in Celsius?")),
                "extract_weather",
                "Extract the city, temperature unit and humidity from a weather question.",
                schema);

        System.out.println("=== Structured output demo ===");
        System.out.println("city     = " + result.get("city").asString());
        System.out.println("unit     = " + result.get("unit").asString());
        System.out.println("humidity = " + result.get("humidity").asDouble());
    }
}
