package nexus.llm;

import nexus.core.Json;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Server-Sent Events parser tailored for OpenAI-style streaming completions.
 * Handles the {@code data:} framing, the {@code [DONE]} sentinel and extraction
 * of {@code choices[].delta.content} into {@link StreamChunk}s.
 */
public final class SseParser {

    private SseParser() {}

    /** Parse a complete SSE payload (one or more events) into chunks. */
    public static List<StreamChunk> parse(String text) {
        List<StreamChunk> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (String rawLine : text.split("\n", -1)) {
            String line = rawLine.replace("\r", "");
            if (line.isEmpty()) {
                if (buf.length() > 0) {
                    StreamChunk c = eventToChunk(buf.toString());
                    buf.setLength(0);
                    if (c != null) out.add(c);
                }
                continue;
            }
            if (line.startsWith("data:")) {
                String d = line.substring(5);
                if (d.startsWith(" ")) d = d.substring(1);
                buf.append(d).append('\n');
            }
        }
        if (buf.length() > 0) {
            StreamChunk c = eventToChunk(buf.toString());
            if (c != null) out.add(c);
        }
        return out;
    }

    /** Stream chunks lazily from an input stream (one line read at a time). */
    public static Stream<StreamChunk> stream(InputStream in) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        return StreamSupport.stream(new Spliterators.AbstractSpliterator<StreamChunk>(Long.MAX_VALUE, Spliterator.ORDERED) {
            private final StringBuilder buf = new StringBuilder();
            private boolean closed = false;

            @Override
            public boolean tryAdvance(Consumer<? super StreamChunk> action) {
                if (closed) return false;
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String l = line.replace("\r", "");
                        if (l.isEmpty()) {
                            if (buf.length() > 0) {
                                StreamChunk c = eventToChunk(buf.toString());
                                buf.setLength(0);
                                if (c != null) {
                                    action.accept(c);
                                    if (c.done) { closed = true; }
                                    return true;
                                }
                            }
                            continue;
                        }
                        if (l.startsWith("data:")) {
                            String d = l.substring(5);
                            if (d.startsWith(" ")) d = d.substring(1);
                            buf.append(d).append('\n');
                        }
                    }
                    if (buf.length() > 0) {
                        StreamChunk c = eventToChunk(buf.toString());
                        if (c != null) action.accept(c);
                    }
                    closed = true;
                    return false;
                } catch (IOException e) {
                    closed = true;
                    return false;
                }
            }
        }, false).onClose(() -> {
            try { reader.close(); } catch (IOException ignore) {}
        });
    }

    private static StreamChunk eventToChunk(String event) {
        String trimmed = event.trim();
        if (trimmed.isEmpty()) return null;
        if ("[DONE]".equals(trimmed)) {
            return StreamChunk.end();
        }
        Json json;
        try {
            json = Json.parse(trimmed);
        } catch (RuntimeException e) {
            return null;
        }
        StreamChunk chunk = new StreamChunk();
        chunk.raw = json;
        Json choices = json.get("choices");
        if (choices != null && choices.size() > 0) {
            Json first = choices.get(0);
            Json delta = first.get("delta");
            if (delta != null) {
                Json content = delta.get("content");
                if (content != null && !content.isNull()) {
                    chunk.deltaContent = content.asString();
                }
            }
            Json finish = first.get("finish_reason");
            if (finish != null && !finish.isNull()) {
                chunk.finishReason = finish.asString();
                if ("stop".equals(chunk.finishReason)) chunk.done = true;
            }
        }
        return chunk;
    }
}
