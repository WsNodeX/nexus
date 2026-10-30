package nexus.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A tiny, dependency-free JSON value model with a hand-written parser and
 * serializer. It supports the full JSON grammar (objects, arrays, strings with
 * escapes, numbers, booleans and null) and offers a fluent builder API so the
 * rest of the framework can construct request bodies and tool schemas without
 * pulling in a third-party library.
 *
 * <p>Instances are immutable after construction; the {@code put}/{@code add}
 * builders mutate the receiver and return it for chaining.</p>
 */
public final class Json {

    public enum T { OBJ, ARR, STR, NUM, BOOL, NUL }

    public final T t;
    private final Map<String, Json> obj;
    private final List<Json> arr;
    private final String str;
    private final double num;
    private final boolean bool;
    private final String numStr;

    private Json(T t, Map<String, Json> obj, List<Json> arr, String str, double num, boolean bool, String numStr) {
        this.t = t;
        this.obj = obj;
        this.arr = arr;
        this.str = str;
        this.num = num;
        this.bool = bool;
        this.numStr = numStr;
    }

    // ---- factories -------------------------------------------------------

    public static Json obj() {
        return new Json(T.OBJ, new LinkedHashMap<>(), null, null, 0, false, null);
    }

    public static Json obj(Map<String, Json> m) {
        return new Json(T.OBJ, new LinkedHashMap<>(m), null, null, 0, false, null);
    }

    public Json put(String k, Json v) {
        if (t != T.OBJ) throw new IllegalStateException("put() on non-object");
        obj.put(k, v);
        return this;
    }

    public Json put(String k, String v) { return put(k, str(v)); }

    public Json put(String k, int v) { return put(k, num(v)); }

    public Json put(String k, long v) { return put(k, num(v)); }

    public Json put(String k, double v) { return put(k, num(v)); }

    public Json put(String k, boolean v) { return put(k, bool(v)); }

    public static Json arr() {
        return new Json(T.ARR, null, new ArrayList<>(), null, 0, false, null);
    }

    public Json add(Json v) {
        if (t != T.ARR) throw new IllegalStateException("add() on non-array");
        arr.add(v);
        return this;
    }

    public Json add(String v) { return add(str(v)); }

    public Json add(int v) { return add(num(v)); }

    public Json add(long v) { return add(num(v)); }

    public Json add(double v) { return add(num(v)); }

    public Json add(boolean v) { return add(bool(v)); }

    public static Json str(String s) {
        return new Json(T.STR, null, null, s, 0, false, null);
    }

    public static Json num(int n) { return num((double) n, null); }

    public static Json num(long n) { return num((double) n, null); }

    public static Json num(double d) { return num(d, null); }

    private static Json num(double d, String repr) {
        return new Json(T.NUM, null, null, null, d, false, repr);
    }

    public static Json bool(boolean b) {
        return new Json(T.BOOL, null, null, null, 0, b, null);
    }

    public static Json nil() {
        return new Json(T.NUL, null, null, null, 0, false, null);
    }

    // ---- accessors -------------------------------------------------------

    public Map<String, Json> asObject() {
        if (t != T.OBJ) throw new IllegalStateException("not an object");
        return obj;
    }

    public List<Json> asArray() {
        if (t != T.ARR) throw new IllegalStateException("not an array");
        return arr;
    }

    public String asString() {
        if (t != T.STR) throw new IllegalStateException("not a string");
        return str;
    }

    public double asDouble() {
        if (t != T.NUM) throw new IllegalStateException("not a number");
        return num;
    }

    public int asInt() { return (int) asDouble(); }

    public long asLong() { return (long) asDouble(); }

    public boolean asBool() {
        if (t != T.BOOL) throw new IllegalStateException("not a boolean");
        return bool;
    }

    public boolean isNull() { return t == T.NUL; }

    public boolean has(String k) { return t == T.OBJ && obj.containsKey(k); }

    public Json get(String k) { return t == T.OBJ ? obj.get(k) : null; }

    public Json get(int i) { return t == T.ARR ? arr.get(i) : null; }

    public int size() {
        if (t == T.OBJ) return obj.size();
        if (t == T.ARR) return arr.size();
        return 0;
    }

    // ---- parsing ---------------------------------------------------------

    public static Json parse(String s) {
        Parser p = new Parser(s);
        Json v = p.parseValue();
        p.skipWs();
        if (!p.atEnd()) throw new JsonException("trailing characters at " + p.i);
        return v;
    }

    // ---- serialization ---------------------------------------------------

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        write(sb);
        return sb.toString();
    }

    public String toPretty() {
        StringBuilder sb = new StringBuilder();
        writePretty(sb, 0);
        return sb.toString();
    }

    void write(StringBuilder sb) {
        switch (t) {
            case OBJ:
                sb.append('{');
                int i = 0;
                for (Map.Entry<String, Json> e : obj.entrySet()) {
                    if (i++ > 0) sb.append(',');
                    quote(sb, e.getKey());
                    sb.append(':');
                    e.getValue().write(sb);
                }
                sb.append('}');
                break;
            case ARR:
                sb.append('[');
                int j = 0;
                for (Json e : arr) {
                    if (j++ > 0) sb.append(',');
                    e.write(sb);
                }
                sb.append(']');
                break;
            case STR:
                quote(sb, str);
                break;
            case NUM:
                sb.append(numStr != null ? numStr : formatNum(num));
                break;
            case BOOL:
                sb.append(bool ? "true" : "false");
                break;
            case NUL:
                sb.append("null");
                break;
        }
    }

    private void writePretty(StringBuilder sb, int depth) {
        String indent = "  ".repeat(depth);
        String inner = "  ".repeat(depth + 1);
        switch (t) {
            case OBJ:
                if (obj.isEmpty()) { sb.append("{}"); return; }
                sb.append("{\n");
                int i = 0;
                for (Map.Entry<String, Json> e : obj.entrySet()) {
                    if (i++ > 0) sb.append(",\n");
                    sb.append(inner);
                    quote(sb, e.getKey());
                    sb.append(": ");
                    e.getValue().writePretty(sb, depth + 1);
                }
                sb.append('\n').append(indent).append('}');
                break;
            case ARR:
                if (arr.isEmpty()) { sb.append("[]"); return; }
                sb.append("[\n");
                int j = 0;
                for (Json e : arr) {
                    if (j++ > 0) sb.append(",\n");
                    sb.append(inner);
                    e.writePretty(sb, depth + 1);
                }
                sb.append('\n').append(indent).append(']');
                break;
            default:
                write(sb);
        }
    }

    static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    static String formatNum(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return "null";
        if (d == Math.rint(d) && Math.abs(d) < 1e15) return Long.toString((long) d);
        return Double.toString(d);
    }

    // ---- equality --------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Json)) return false;
        Json j = (Json) o;
        if (t != j.t) return false;
        switch (t) {
            case OBJ: return obj.equals(j.obj);
            case ARR: return arr.equals(j.arr);
            case STR: return Objects.equals(str, j.str);
            case NUM: return Double.compare(num, j.num) == 0;
            case BOOL: return bool == j.bool;
            case NUL: return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        switch (t) {
            case OBJ: return obj.hashCode();
            case ARR: return arr.hashCode();
            case STR: return str.hashCode();
            case NUM: return Double.hashCode(num);
            case BOOL: return Boolean.hashCode(bool);
            default: return 0;
        }
    }

    // ---- parser ----------------------------------------------------------

    static final class Parser {
        final String s;
        int i = 0;

        Parser(String s) { this.s = s; }

        boolean atEnd() { return i >= s.length(); }

        void skipWs() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++;
                else break;
            }
        }

        Json parseValue() {
            skipWs();
            if (i >= s.length()) throw new JsonException("unexpected end of input");
            char c = s.charAt(i);
            switch (c) {
                case '{': return parseObject();
                case '[': return parseArray();
                case '"': return str(parseString());
                case 't': case 'f': return parseBool();
                case 'n': return parseNull();
                default: return parseNumber();
            }
        }

        Json parseObject() {
            expect('{');
            skipWs();
            Json o = obj();
            if (peek() == '}') { i++; return o; }
            while (true) {
                skipWs();
                if (peek() != '"') throw new JsonException("expected key at " + i);
                String key = parseString();
                skipWs();
                expect(':');
                skipWs();
                o.put(key, parseValue());
                skipWs();
                char c = peek();
                if (c == ',') { i++; continue; }
                if (c == '}') { i++; break; }
                throw new JsonException("expected ',' or '}' at " + i);
            }
            return o;
        }

        Json parseArray() {
            expect('[');
            skipWs();
            Json a = arr();
            if (peek() == ']') { i++; return a; }
            while (true) {
                a.add(parseValue());
                skipWs();
                char c = peek();
                if (c == ',') { i++; continue; }
                if (c == ']') { i++; break; }
                throw new JsonException("expected ',' or ']' at " + i);
            }
            return a;
        }

        String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (i >= s.length()) throw new JsonException("bad escape at end");
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (i + 4 > s.length()) throw new JsonException("bad unicode escape");
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                            break;
                        default: throw new JsonException("bad escape \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new JsonException("unterminated string");
        }

        Json parseNumber() {
            int start = i;
            if (peek() == '-') i++;
            while (i < s.length()) {
                char c = s.charAt(i);
                if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') i++;
                else break;
            }
            String repr = s.substring(start, i);
            try {
                return num(Double.parseDouble(repr), repr);
            } catch (NumberFormatException e) {
                throw new JsonException("invalid number '" + repr + "'");
            }
        }

        Json parseBool() {
            if (s.startsWith("true", i)) { i += 4; return bool(true); }
            if (s.startsWith("false", i)) { i += 5; return bool(false); }
            throw new JsonException("invalid literal at " + i);
        }

        Json parseNull() {
            if (s.startsWith("null", i)) { i += 4; return nil(); }
            throw new JsonException("invalid literal at " + i);
        }

        char peek() {
            if (i >= s.length()) throw new JsonException("unexpected end of input");
            return s.charAt(i);
        }

        void expect(char c) {
            if (peek() != c) throw new JsonException("expected '" + c + "' at " + i);
            i++;
        }
    }
}
