package nexus.test;

import nexus.core.Json;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JsonTest {

    @Test
    void parsesNumbers() {
        assertEquals(42, Json.parse("42").asInt());
        assertEquals(3.14, Json.parse("3.14").asDouble(), 1e-9);
        assertEquals(-7, Json.parse("-7").asInt());
    }

    @Test
    void parsesStringsWithEscapes() {
        assertEquals("a\nb", Json.parse("\"a\\nb\"").asString());
        assertEquals("A", Json.parse("\"\\u0041\"").asString());
        assertEquals("quote\"slash\\", Json.parse("\"quote\\\"slash\\\\\"").asString());
    }

    @Test
    void parsesNestedStructures() {
        Json j = Json.parse("{\"name\":\"x\",\"n\":3,\"list\":[1,2,{\"ok\":true}],\"flag\":false,\"nil\":null}");
        assertEquals("x", j.get("name").asString());
        assertEquals(3, j.get("n").asInt());
        assertTrue(j.get("list").get(2).get("ok").asBool());
        assertTrue(j.get("nil").isNull());
        assertEquals(3, j.get("list").size());
    }

    @Test
    void builderRoundTrips() {
        Json built = Json.obj()
                .put("a", 1)
                .put("b", "two")
                .put("c", Json.arr().add(1).add(2).add(3));
        String s = built.toString();
        Json parsed = Json.parse(s);
        assertEquals(built, parsed);
        assertEquals(3, parsed.get("c").size());
    }

    @Test
    void rejectsMalformedInput() {
        assertThrows(RuntimeException.class, () -> Json.parse("{"));
        assertThrows(RuntimeException.class, () -> Json.parse("{\"a\":}"));
        assertThrows(RuntimeException.class, () -> Json.parse("\"unterminated"));
    }

    @Test
    void prettyPrintContainsNewlines() {
        Json j = Json.obj().put("k", "v");
        assertTrue(j.toPretty().contains("\n"));
        assertEquals(j, Json.parse(j.toPretty()));
    }
}
