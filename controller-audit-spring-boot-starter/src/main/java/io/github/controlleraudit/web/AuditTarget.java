package io.github.controlleraudit.web;

import io.github.controlleraudit.annotation.Mode;

import java.util.Set;

/** Effective audit settings for one handler method, resolved from annotations + properties. */
public record AuditTarget(
        String handler,
        Mode mode,
        boolean requestBody,
        boolean responseBody,
        Set<String> maskHeaders,
        Set<String> maskFields) {
}
