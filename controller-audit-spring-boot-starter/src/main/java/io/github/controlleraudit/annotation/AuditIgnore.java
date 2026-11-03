package io.github.controlleraudit.annotation;

import java.lang.annotation.*;

/** Excludes a controller or handler method from auditing, even if an enclosing scope has {@link Audit}. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditIgnore {
}
