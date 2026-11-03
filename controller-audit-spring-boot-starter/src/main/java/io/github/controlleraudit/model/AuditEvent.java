package io.github.controlleraudit.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** One audited HTTP exchange. Headers/bodies are already masked and truncated. */
public record AuditEvent(
        String eventId,
        String correlationId,
        Instant requestTime,
        Instant responseTime,
        long durationMs,
        String httpMethod,
        String uri,
        String queryString,
        String handler,
        String clientIp,
        String username,
        Map<String, List<String>> requestHeaders,
        String requestBody,
        int responseStatus,
        Map<String, List<String>> responseHeaders,
        String responseBody,
        String error) {
}
