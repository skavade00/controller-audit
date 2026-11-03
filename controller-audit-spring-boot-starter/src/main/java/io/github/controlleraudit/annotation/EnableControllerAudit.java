package io.github.controlleraudit.annotation;

import io.github.controlleraudit.config.ControllerAuditImportSelector;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * Switches the audit machinery on without auditing anything by itself. Use it on your main class when you only
 * want to annotate individual controllers / methods with {@link Audit}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ControllerAuditImportSelector.class)
public @interface EnableControllerAudit {
}
