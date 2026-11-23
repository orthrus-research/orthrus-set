package set;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A small JSON reader, so the plugins depend on nothing but Gradle: objects become maps, arrays lists. */
final class Json {
    private final String text;
    private int at;

    private Json(String text) {
        this.text = text;
    }

    static Object parse(String text) {
        Json json = new Json(text);
        Object value = json.value();
        json.space();
        if (json.at != text.length()) {
            throw json.error("text after the value");
        }
        return value;
    }

    private IllegalArgumentException error(String what) {
        return new IllegalArgumentException("set.json: " + what + " at offset " + at);
    }

    private void space() {
        while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
            at++;
        }
    }

    private Object value() {
        space();
        if (at >= text.length()) {
            throw error("unexpected end");
        }
        char c = text.charAt(at);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': return word("true", Boolean.TRUE);
            case 'f': return word("false", Boolean.FALSE);
            case 'n': return word("null", null);
            default: return number();
        }
    }

    private Object word(String word, Object value) {
        if (!text.startsWith(word, at)) {
            throw error("expected " + word);
        }
        at += word.length();
        return value;
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        at++;
        space();
        if (text.charAt(at) == '}') {
            at++;
            return map;
        }
        while (true) {
            space();
            String key = string();
            space();
            if (text.charAt(at++) != ':') {
                throw error("expected ':'");
            }
            map.put(key, value());
            space();
            char c = text.charAt(at++);
            if (c == '}') {
                return map;
            }
            if (c != ',') {
                throw error("expected ',' or '}'");
            }
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        at++;
        space();
        if (text.charAt(at) == ']') {
            at++;
            return list;
        }
        while (true) {
            list.add(value());
            space();
            char c = text.charAt(at++);
            if (c == ']') {
                return list;
            }
            if (c != ',') {
                throw error("expected ',' or ']'");
            }
        }
    }

    private String string() {
        if (text.charAt(at) != '"') {
            throw error("expected a string");
        }
        at++;
        StringBuilder out = new StringBuilder();
        while (true) {
            char c = text.charAt(at++);
            if (c == '"') {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char e = text.charAt(at++);
            switch (e) {
                case 'n': out.append('\n'); break;
                case 't': out.append('\t'); break;
                case 'r': out.append('\r'); break;
                case 'b': out.append('\b'); break;
                case 'f': out.append('\f'); break;
                case 'u':
                    out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                    at += 4;
                    break;
                default: out.append(e);
            }
        }
    }

    private Object number() {
        int start = at;
        while (at < text.length() && "+-0123456789.eE".indexOf(text.charAt(at)) >= 0) {
            at++;
        }
        String number = text.substring(start, at);
        if (number.isEmpty()) {
            throw error("unexpected character");
        }
        if (number.contains(".") || number.contains("e") || number.contains("E")) {
            return Double.parseDouble(number);
        }
        return Long.parseLong(number);
    }
}
