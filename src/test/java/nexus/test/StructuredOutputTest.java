package nexus.test;

import nexus.core.Json;
import nexus.core.errors.NexusException;
import nexus.llm.ChatRequest;
import nexus.llm.ChatResponse;
import nexus.llm.Message;
import nexus.llm.Provider;
import nexus.llm.ToolCall;
import nexus.output.Schema;
import nexus.output.StructuredOutput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StructuredOutputTest {

    private Provider toolCallProvider(String args) {
        return Stubs.named("fake", new ChatResponse() {{
            toolCalls = List.of(new ToolCall("c1", "extract", args));
        }});
    }

    @Test
    void parsesAndValidatesStructuredObject() {
        StructuredOutput so = new StructuredOutput(toolCallProvider("{\"city\":\"Paris\",\"unit\":\"c\"}"), "m");
        Schema s = Schema.create()
                .property("city", "string").required("city")
                .property("unit", "string").enumOf("unit", List.of("c", "f"));
        Json j = so.call(List.of(Message.user("weather in Paris?")), "extract", "desc", s);
        assertEquals("Paris", j.get("city").asString());
        assertEquals("c", j.get("unit").asString());
    }

    @Test
    void rejectsMissingRequiredField() {
        StructuredOutput so = new StructuredOutput(toolCallProvider("{\"unit\":\"c\"}"), "m");
        Schema s = Schema.create().property("city", "string").required("city");
        assertThrows(NexusException.class,
                () -> so.call(List.of(Message.user("x")), "extract", "desc", s));
    }

    @Test
    void rejectsEnumViolation() {
        StructuredOutput so = new StructuredOutput(toolCallProvider("{\"city\":\"X\",\"unit\":\"z\"}"), "m");
        Schema s = Schema.create()
                .property("city", "string").required("city")
                .property("unit", "string").enumOf("unit", List.of("c", "f"));
        assertThrows(NexusException.class,
                () -> so.call(List.of(Message.user("x")), "extract", "desc", s));
    }

    @Test
    void validatorReportsErrors() {
        Json schema = Schema.create().property("a", "string").required("a").toJson();
        List<String> errors = StructuredOutput.validate(schema, Json.obj());
        assertTrue(errors.stream().anyMatch(e -> e.contains("missing required 'a'")));
    }
}
