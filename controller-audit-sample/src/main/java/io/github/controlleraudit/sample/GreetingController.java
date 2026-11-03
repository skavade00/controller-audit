package io.github.controlleraudit.sample;

import io.github.controlleraudit.annotation.Audit;
import io.github.controlleraudit.annotation.AuditIgnore;
import io.github.controlleraudit.annotation.Mode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class GreetingController {

    private final JdbcTemplate jdbc;

    public GreetingController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/greet")
    public Map<String, String> greet(@RequestParam(defaultValue = "world") String name) {
        return Map.of("message", "Hello, " + name);
    }

    /** Written synchronously, and "pin" is masked on top of the defaults. */
    @Audit(mode = Mode.SYNC, maskFields = "pin")
    @PostMapping("/login")
    public Map<String, String> login(@RequestBody Map<String, String> credentials) {
        return Map.of("status", "ok", "user", credentials.getOrDefault("username", "?"));
    }

    @GetMapping("/boom")
    public String boom() {
        throw new IllegalStateException("demo failure");
    }

    /** Not audited, otherwise reading the log would create more log. */
    @AuditIgnore
    @GetMapping("/audit-log")
    public List<Map<String, Object>> auditLog() {
        return jdbc.query("select request_time, http_method, uri, response_status, duration_ms, handler, "
                        + "request_headers, request_body, response_body, error "
                        + "from api_audit_log order by request_time desc limit 50",
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (String col : List.of("request_time", "http_method", "uri", "response_status", "duration_ms",
                            "handler", "request_headers", "request_body", "response_body", "error")) {
                        row.put(col, rs.getString(col));
                    }
                    return row;
                });
    }
}
