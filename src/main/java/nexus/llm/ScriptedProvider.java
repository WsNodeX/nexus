package nexus.llm;

import nexus.core.errors.NexusException;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A provider that answers from an in-memory script instead of a network call.
 * Invaluable for unit tests and offline demos: it makes the entire gateway
 * (routing, resilience, caching, structured output) exercisable without a key.
 */
public class ScriptedProvider implements Provider {

    private final String name;
    private final Function<ChatRequest, ChatResponse> script;
    private final Function<ChatRequest, List<StreamChunk>> streamScript;
    private final Set<String> capabilities;

    public ScriptedProvider(String name, Function<ChatRequest, ChatResponse> script) {
        this(name, script, null, Set.of("streaming", "function-calling"));
    }

    public ScriptedProvider(String name, Function<ChatRequest, ChatResponse> script,
                            Function<ChatRequest, List<StreamChunk>> streamScript, Set<String> capabilities) {
        this.name = name;
        this.script = script;
        this.streamScript = streamScript;
        this.capabilities = capabilities;
    }

    @Override
    public String name() { return name; }

    @Override
    public Set<String> capabilities() { return capabilities; }

    @Override
    public ChatResponse chat(ChatRequest req) throws NexusException {
        return script.apply(req);
    }

    @Override
    public Stream<StreamChunk> chatStream(ChatRequest req) throws NexusException {
        if (streamScript != null) {
            return streamScript.apply(req).stream();
        }
        ChatResponse r = script.apply(req);
        String content = r.content == null ? "" : r.content;
        Stream.Builder<StreamChunk> b = Stream.builder();
        for (String word : content.split(" ", -1)) {
            StreamChunk c = new StreamChunk(word + " ");
            b.add(c);
        }
        b.add(StreamChunk.end());
        return b.build();
    }

    /** Convenience: a provider that always returns the given response. */
    public static ScriptedProvider constant(String name, ChatResponse response) {
        return new ScriptedProvider(name, req -> response);
    }
}
