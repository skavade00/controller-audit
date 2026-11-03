package io.github.controlleraudit.config;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.List;

/** Imports the core configuration, plus the Kafka configuration when the controller-audit-kafka jar is present. */
public class ControllerAuditImportSelector implements ImportSelector {

    static final String KAFKA_CONFIG = "io.github.controlleraudit.kafka.KafkaAuditConfiguration";

    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        List<String> imports = new ArrayList<>();
        imports.add(ControllerAuditConfiguration.class.getName());
        if (ClassUtils.isPresent(KAFKA_CONFIG, getClass().getClassLoader())) {
            imports.add(KAFKA_CONFIG);
        }
        return imports.toArray(new String[0]);
    }
}
