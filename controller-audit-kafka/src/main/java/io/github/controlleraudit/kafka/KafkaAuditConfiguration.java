package io.github.controlleraudit.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.controlleraudit.config.ControllerAuditProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Loaded automatically (via ControllerAuditImportSelector) when this jar is on the classpath.
 * Only active when controller-audit.kafka.enabled=true.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(prefix = "controller-audit.kafka", name = "enabled", havingValue = "true")
public class KafkaAuditConfiguration {

    @Bean
    public KafkaAuditSink controllerAuditKafkaSink(ControllerAuditProperties props,
                                                   ObjectProvider<KafkaTemplate<String, String>> template) {
        KafkaTemplate<String, String> kafkaTemplate = template.getIfAvailable();
        if (kafkaTemplate == null) {
            throw new IllegalStateException(
                    "controller-audit.kafka.enabled=true but no KafkaTemplate<String,String> bean is available. "
                            + "Configure spring.kafka.bootstrap-servers.");
        }
        ObjectMapper mapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new KafkaAuditSink(kafkaTemplate, props.getKafka().getTopic(), mapper);
    }
}
