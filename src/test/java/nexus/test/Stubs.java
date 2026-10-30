package nexus.test;

import nexus.llm.ChatRequest;
import nexus.llm.ChatResponse;
import nexus.llm.Provider;

import java.util.Set;

/** Small provider stubs shared across the test suite. */
public final class Stubs {
    private Stubs() {}

    public static Provider named(String name, ChatResponse resp) {
        return new Provider() {
            @Override public String name() { return name; }
            @Override public ChatResponse chat(ChatRequest req) { return resp; }
        };
    }

    public static Provider namedCaps(String name, Set<String> caps, ChatResponse resp) {
        return new Provider() {
            @Override public String name() { return name; }
            @Override public Set<String> capabilities() { return caps; }
            @Override public ChatResponse chat(ChatRequest req) { return resp; }
        };
    }
}
