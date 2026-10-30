package nexus.llm;

import nexus.core.errors.NexusException;

import java.util.Set;
import java.util.stream.Stream;

/**
 * A provider that echoes the last user message back as its completion. Useful as
 * a default when no API key is configured, so examples stay runnable offline.
 */
public class EchoProvider implements Provider {

    private final String name;

    public EchoProvider() { this("echo"); }

    public EchoProvider(String name) { this.name = name; }

    @Override
    public String name() { return name; }

    @Override
    public Set<String> capabilities() { return Set.of("streaming"); }

    @Override
    public ChatResponse chat(ChatRequest req) throws NexusException {
        return new ChatResponse("echo: " + lastUser(req));
    }

    @Override
    public Stream<StreamChunk> chatStream(ChatRequest req) throws NexusException {
        String text = "echo: " + lastUser(req);
        Stream.Builder<StreamChunk> b = Stream.builder();
        for (String word : text.split(" ", -1)) {
            b.add(new StreamChunk(word + " "));
        }
        b.add(StreamChunk.end());
        return b.build();
    }

    private static String lastUser(ChatRequest req) {
        for (int i = req.messages.size() - 1; i >= 0; i--) {
            Message m = req.messages.get(i);
            if (m.role == Message.Role.user && m.content != null) return m.content;
        }
        return "";
    }
}
