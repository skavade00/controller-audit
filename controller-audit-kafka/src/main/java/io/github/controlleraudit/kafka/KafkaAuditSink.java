package io.github.controlleraudit.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.controlleraudit.model.AuditEvent;
import io.github.controlleraudit.sink.AuditSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/** Publishes each event as JSON to a Kafka topic, keyed by correlation id. */
public class KafkaAuditSink implements AuditSink {

    private static final Logger log = LoggerFactory.getLogger(KafkaAuditSink.class);

    private final KafkaTemplate<String, String> template;
    private final String topic;
    private final ObjectMapper mapper;

    public KafkaAuditSink(KafkaTemplate<String, String> template, String topic, ObjectMapper mapper) {
        this.template = template;
        this.topic = topic;
        this.mapper = mapper;
    }

    @Override
    public void write(AuditEvent event) throws Exception {
        String key = event.correlationId() != null ? event.correlationId() : event.eventId();
        template.send(topic, key, mapper.writeValueAsString(event)).whenComplete((result, ex) -> {
            if (ex != null) {
                log.warn("controller-audit: Kafka send failed: {}", ex.toString());
            }
        });
    }
}
