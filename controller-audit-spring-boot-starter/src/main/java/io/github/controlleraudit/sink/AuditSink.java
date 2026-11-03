package io.github.controlleraudit.sink;

import io.github.controlleraudit.model.AuditEvent;

import java.util.List;

/**
 * Destination for audit events. Declare any number of beans implementing this interface and they all receive
 * every event (the built-in JDBC and Kafka sinks are just two implementations).
 */
public interface AuditSink {

    void write(AuditEvent event) throws Exception;

    /** Override for efficient bulk writes. Called by the async workers with up to batch-size events. */
    default void writeBatch(List<AuditEvent> events) throws Exception {
        for (AuditEvent event : events) {
            write(event);
        }
    }

    default String name() {
        return getClass().getSimpleName();
    }
}
