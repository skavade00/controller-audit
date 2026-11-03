package io.github.controlleraudit.annotation;

import io.github.controlleraudit.config.ControllerAuditImportSelector;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * Turns on audit logging.
 * <ul>
 *   <li>On the {@code @SpringBootApplication} (or any {@code @Configuration}) class: audit <b>every</b> controller.</li>
 *   <li>On a controller class: audit every handler method of that controller.</li>
 *   <li>On a handler method: audit just that method (needs {@link EnableControllerAudit} or an {@code @Audit}
 *       somewhere else to switch the feature on).</li>
 * </ul>
 * Method-level settings win over class-level, which win over the application-level ones.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ControllerAuditImportSelector.class)
public @interface Audit {

    /** SYNC / ASYNC override. INHERIT = use the next level up. */
    Mode mode() default Mode.INHERIT;

    /** Capture the request body (also needs controller-audit.capture.request-body=true). */
    boolean requestBody() default true;

    /** Capture the response body (also needs controller-audit.capture.response-body=true). */
    boolean responseBody() default true;

    /** Extra header names to mask, on top of controller-audit.mask.headers. */
    String[] maskHeaders() default {};

    /** Extra JSON / form field names to mask, on top of controller-audit.mask.fields. */
    String[] maskFields() default {};
}
