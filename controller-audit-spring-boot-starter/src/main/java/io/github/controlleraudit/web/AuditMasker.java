package io.github.controlleraudit.web;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Redacts sensitive headers and JSON / form body fields. Regex based so it also works on truncated bodies. */
public class AuditMasker {

    public static final String MASK = "****";

    private record Patterns(Pattern json, Pattern form) {
    }

    private final Map<String, Patterns> cache = new ConcurrentHashMap<>();

    public Map<String, List<String>> maskHeaders(Map<String, List<String>> headers, Set<String> maskedLowerCase) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        headers.forEach((name, values) ->
                out.put(name, maskedLowerCase.contains(name.toLowerCase(Locale.ROOT)) ? List.of(MASK) : values));
        return out;
    }

    public String maskBody(String body, Set<String> fieldsLowerCase) {
        if (body == null || body.isEmpty() || fieldsLowerCase.isEmpty()) {
            return body;
        }
        Patterns p = cache.computeIfAbsent(String.join("|", new TreeSet<>(fieldsLowerCase)),
                key -> build(fieldsLowerCase));
        String result = p.json().matcher(body).replaceAll("$1\"" + MASK + "\"");
        return p.form().matcher(result).replaceAll("$1" + MASK);
    }

    private static Patterns build(Set<String> fields) {
        StringBuilder alt = new StringBuilder();
        for (String f : fields) {
            if (alt.length() > 0) {
                alt.append('|');
            }
            alt.append(Pattern.quote(f));
        }
        Pattern json = Pattern.compile(
                "(\"(?:" + alt + ")\"\\s*:\\s*)(\"(?:[^\"\\\\]|\\\\.)*+\"|[^,}\\]\\s]+)", Pattern.CASE_INSENSITIVE);
        Pattern form = Pattern.compile("((?:^|&)(?:" + alt + ")=)[^&]*", Pattern.CASE_INSENSITIVE);
        return new Patterns(json, form);
    }
}
