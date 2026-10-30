package nexus.test;

import nexus.llm.SseParser;
import nexus.llm.StreamChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SseParserTest {

    @Test
    void parsesMultipleEventsAndDone() {
        String sse = "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\" world\"}}]}\n\n"
                + "data: [DONE]\n";
        List<StreamChunk> chunks = SseParser.parse(sse);
        assertEquals(3, chunks.size());
        assertEquals("Hello", chunks.get(0).deltaContent);
        assertEquals(" world", chunks.get(1).deltaContent);
        assertTrue(chunks.get(2).done);
        assertNull(chunks.get(2).deltaContent);
    }

    @Test
    void ignoresBlankAndNonDataLines() {
        String sse = ": comment\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\"x\"}}]}\n\n";
        List<StreamChunk> chunks = SseParser.parse(sse);
        assertEquals(1, chunks.size());
        assertEquals("x", chunks.get(0).deltaContent);
    }

    @Test
    void detectsFinishReason() {
        String sse = "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n";
        List<StreamChunk> chunks = SseParser.parse(sse);
        assertEquals(1, chunks.size());
        assertEquals("stop", chunks.get(0).finishReason);
        assertTrue(chunks.get(0).done);
    }
}
