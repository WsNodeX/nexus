package nexus.test;

import nexus.core.Json;
import nexus.llm.ChatRequest;
import nexus.llm.ChatResponse;
import nexus.llm.EchoProvider;
import nexus.llm.Message;
import nexus.llm.OpenAiProvider;
import nexus.llm.ScriptedProvider;
import nexus.llm.StreamChunk;
import nexus.llm.Tool;
import nexus.output.Schema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class ProviderTest {

    @Test
    void openAiBuildsExpectedBody() {
        OpenAiProvider op = new OpenAiProvider("oa", "https://api.openai.com/v1", "KEY");
        ChatRequest req = new ChatRequest("gpt-4o")
                .add(Message.system("s"))
                .add(Message.user("u"))
                .add(new Tool("f", "desc", Schema.create().property("x", "string").toJson()))
                .toolChoice("f")
                .temperature(0.5)
                .maxTokens(10);
        Json body = op.buildBody(req, false);
        assertEquals("gpt-4o", body.get("model").asString());
        assertEquals(2, body.get("messages").size());
        assertTrue(body.has("tools"));
        Json tc = body.get("tool_choice");
        assertEquals("function", tc.get("type").asString());
        assertEquals("f", tc.get("function").get("name").asString());
        assertEquals(0.5, body.get("temperature").asDouble(), 1e-9);
        assertEquals(10, body.get("max_tokens").asInt());
        assertFalse(body.has("stream"));
    }

    @Test
    void openAiStreamBodyRequestsStreaming() {
        OpenAiProvider op = new OpenAiProvider("oa", "https://api.openai.com/v1", "KEY");
        Json body = op.buildBody(new ChatRequest("m").add(Message.user("hi")), true);
        assertTrue(body.get("stream").asBool());
    }

    @Test
    void scriptedProviderReturnsScriptedResponse() {
        ScriptedProvider sp = new ScriptedProvider("s", r -> new ChatResponse("hi"));
        assertEquals("hi", sp.chat(new ChatRequest("m").add(Message.user("x"))).content);
    }

    @Test
    void scriptedProviderStreamsWords() {
        ScriptedProvider sp = new ScriptedProvider("s", r -> new ChatResponse("hello world"));
        List<StreamChunk> chunks = sp.chatStream(new ChatRequest("m").add(Message.user("q")))
                .collect(Collectors.toList());
        assertTrue(chunks.size() >= 2);
        assertTrue(chunks.get(chunks.size() - 1).done);
    }

    @Test
    void echoProviderReflectsUserMessage() {
        EchoProvider ep = new EchoProvider();
        assertEquals("echo: hi", ep.chat(new ChatRequest("m").add(Message.user("hi"))).content);
    }
}
