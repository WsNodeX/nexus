package nexus.llm;

import nexus.core.Json;

/**
 * One incremental piece of a streaming completion. {@code deltaContent} holds
 * the newly arrived text fragment (may be null on the final usage chunk);
 * {@code done} is true once the provider signals the end of the stream.
 */
public class StreamChunk {
    public String deltaContent;
    public String finishReason;
    public boolean done;
    public Json raw;

    public StreamChunk() {}

    public StreamChunk(String deltaContent) {
        this.deltaContent = deltaContent;
    }

    public static StreamChunk end() {
        StreamChunk c = new StreamChunk();
        c.done = true;
        return c;
    }

    @Override
    public String toString() {
        return "StreamChunk{done=" + done + ", content=" + deltaContent + ", finish=" + finishReason + '}';
    }
}
