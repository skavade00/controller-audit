package io.github.controlleraudit.config;

import io.github.controlleraudit.core.AuditPublisher;
import io.github.controlleraudit.sink.AuditSink;
import io.github.controlleraudit.sink.JdbcAuditSink;
import io.github.controlleraudit.web.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.sql.DataSource;

/** Imported by @Audit / @EnableControllerAudit (there is deliberately no auto-configuration entry). */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "controller-audit", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ControllerAuditProperties.class)
public class ControllerAuditConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "controller-audit.jdbc", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public JdbcAuditSink controllerAuditJdbcSink(ControllerAuditProperties props,
                                                 ObjectProvider<DataSource> appDataSource) {
        ControllerAuditProperties.Jdbc jdbc = props.getJdbc();
        DataSource target;
        if (jdbc.getDatasource() == ControllerAuditProperties.DataSourceType.SEPARATE) {
            if (jdbc.getUrl() == null || jdbc.getUrl().isBlank()) {
                throw new IllegalStateException(
                        "controller-audit.jdbc.datasource=SEPARATE requires controller-audit.jdbc.url");
            }
            DataSourceBuilder<?> builder = DataSourceBuilder.create()
                    .url(jdbc.getUrl())
                    .username(jdbc.getUsername())
                    .password(jdbc.getPassword());
            if (jdbc.getDriverClassName() != null && !jdbc.getDriverClassName().isBlank()) {
                builder.driverClassName(jdbc.getDriverClassName());
            }
            target = builder.build();
        } else {
            target = appDataSource.getIfUnique();
            if (target == null) {
                throw new IllegalStateException("controller-audit: expected exactly one (or one @Primary) DataSource. "
                        + "Set controller-audit.jdbc.datasource=SEPARATE or controller-audit.jdbc.enabled=false.");
            }
        }
        return new JdbcAuditSink(new JdbcTemplate(target), jdbc.getTableName(), jdbc.isAutoCreateTable());
    }

    @Bean
    public AuditPublisher controllerAuditPublisher(ControllerAuditProperties props,
                                                   ObjectProvider<AuditSink> sinks) {
        return new AuditPublisher(sinks.orderedStream().toList(), props.getAsync());
    }

    @Bean
    public AuditMasker controllerAuditMasker() {
        return new AuditMasker();
    }

    @Bean
    public AuditScope controllerAuditScope(ApplicationContext context) {
        return new AuditScope(context);
    }

    @Bean
    public AuditInterceptor controllerAuditInterceptor(ControllerAuditProperties props, AuditScope scope) {
        return new AuditInterceptor(props, scope);
    }

    @Bean
    public WebMvcConfigurer controllerAuditWebMvcConfigurer(AuditInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor);
            }
        };
    }

    @Bean
    public FilterRegistrationBean<AuditFilter> controllerAuditFilter(ControllerAuditProperties props,
                                                                     AuditPublisher publisher,
                                                                     AuditMasker masker) {
        FilterRegistrationBean<AuditFilter> registration =
                new FilterRegistrationBean<>(new AuditFilter(props, publisher, masker));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
