package io.github.controlleraudit.web;

import io.github.controlleraudit.annotation.Audit;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;

/** Finds an @Audit placed on a @Configuration / @SpringBootApplication class (= audit every controller). */
public class AuditScope {

    private final ApplicationContext context;
    private volatile boolean resolved;
    private volatile Audit global;

    public AuditScope(ApplicationContext context) {
        this.context = context;
    }

    public Audit global() {
        if (!resolved) {
            synchronized (this) {
                if (!resolved) {
                    global = find();
                    resolved = true;
                }
            }
        }
        return global;
    }

    private Audit find() {
        for (String name : context.getBeanNamesForAnnotation(Audit.class)) {
            Class<?> type = context.getType(name);
            if (type == null) {
                continue;
            }
            Class<?> userClass = ClassUtils.getUserClass(type);
            if (AnnotatedElementUtils.hasAnnotation(userClass, Configuration.class)) {
                Audit audit = AnnotatedElementUtils.findMergedAnnotation(userClass, Audit.class);
                if (audit != null) {
                    return audit;
                }
            }
        }
        return null;
    }
}
