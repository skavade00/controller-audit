package io.github.controlleraudit.config;

import io.github.controlleraudit.annotation.Mode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "controller-audit")
public class ControllerAuditProperties {

    public enum OverflowPolicy { DROP, BLOCK, CALLER_RUNS }

    public enum DataSourceType { APP, SEPARATE }

    /** Master switch. */
    private boolean enabled = true;
    /** Default write mode (SYNC or ASYNC); @Audit(mode=...) can override. */
    private Mode mode = Mode.ASYNC;
    /** Fraction of requests to audit, 0.0 - 1.0. */
    private double samplingRate = 1.0;
    /** Use the first X-Forwarded-For entry as client IP (only enable behind a trusted proxy). */
    private boolean trustForwardedFor = false;
    /** Incoming header carrying a correlation id; generated when absent. */
    private String correlationHeader = "X-Request-Id";
    /** Ant patterns that are never audited. */
    private List<String> excludePaths = new ArrayList<>(List.of("/actuator/**", "/error"));

    private final Capture capture = new Capture();
    private final Mask mask = new Mask();
    private final Async async = new Async();
    private final Jdbc jdbc = new Jdbc();
    private final Kafka kafka = new Kafka();

    public static class Capture {
        private boolean requestBody = true;
        private boolean responseBody = true;
        /** Max body bytes stored per request / response. */
        private int maxBodySize = 10240;

        public boolean isRequestBody() { return requestBody; }
        public void setRequestBody(boolean requestBody) { this.requestBody = requestBody; }
        public boolean isResponseBody() { return responseBody; }
        public void setResponseBody(boolean responseBody) { this.responseBody = responseBody; }
        public int getMaxBodySize() { return maxBodySize; }
        public void setMaxBodySize(int maxBodySize) { this.maxBodySize = maxBodySize; }
    }

    public static class Mask {
        private List<String> headers = new ArrayList<>(
                List.of("Authorization", "Cookie", "Set-Cookie", "X-Api-Key"));
        private List<String> fields = new ArrayList<>(
                List.of("password", "passwd", "secret", "token", "access_token", "refresh_token", "api_key"));

        public List<String> getHeaders() { return headers; }
        public void setHeaders(List<String> headers) { this.headers = headers; }
        public List<String> getFields() { return fields; }
        public void setFields(List<String> fields) { this.fields = fields; }
    }

    public static class Async {
        private int queueCapacity = 10000;
        private int workerThreads = 1;
        private int batchSize = 100;
        /** How long an idle worker waits for events before re-checking shutdown. */
        private long idlePollMs = 500;
        private OverflowPolicy overflowPolicy = OverflowPolicy.DROP;
        private long shutdownTimeoutMs = 10000;

        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
        public int getWorkerThreads() { return workerThreads; }
        public void setWorkerThreads(int workerThreads) { this.workerThreads = workerThreads; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public long getIdlePollMs() { return idlePollMs; }
        public void setIdlePollMs(long idlePollMs) { this.idlePollMs = idlePollMs; }
        public OverflowPolicy getOverflowPolicy() { return overflowPolicy; }
        public void setOverflowPolicy(OverflowPolicy overflowPolicy) { this.overflowPolicy = overflowPolicy; }
        public long getShutdownTimeoutMs() { return shutdownTimeoutMs; }
        public void setShutdownTimeoutMs(long shutdownTimeoutMs) { this.shutdownTimeoutMs = shutdownTimeoutMs; }
    }

    public static class Jdbc {
        private boolean enabled = true;
        private String tableName = "api_audit_log";
        private boolean autoCreateTable = true;
        /** APP = reuse the application's DataSource, SEPARATE = build one from the url/username/password below. */
        private DataSourceType datasource = DataSourceType.APP;
        private String url;
        private String username;
        private String password;
        private String driverClassName;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public boolean isAutoCreateTable() { return autoCreateTable; }
        public void setAutoCreateTable(boolean autoCreateTable) { this.autoCreateTable = autoCreateTable; }
        public DataSourceType getDatasource() { return datasource; }
        public void setDatasource(DataSourceType datasource) { this.datasource = datasource; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getDriverClassName() { return driverClassName; }
        public void setDriverClassName(String driverClassName) { this.driverClassName = driverClassName; }
    }

    public static class Kafka {
        private boolean enabled = false;
        private String topic = "api-audit-events";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
    public double getSamplingRate() { return samplingRate; }
    public void setSamplingRate(double samplingRate) { this.samplingRate = samplingRate; }
    public boolean isTrustForwardedFor() { return trustForwardedFor; }
    public void setTrustForwardedFor(boolean trustForwardedFor) { this.trustForwardedFor = trustForwardedFor; }
    public String getCorrelationHeader() { return correlationHeader; }
    public void setCorrelationHeader(String correlationHeader) { this.correlationHeader = correlationHeader; }
    public List<String> getExcludePaths() { return excludePaths; }
    public void setExcludePaths(List<String> excludePaths) { this.excludePaths = excludePaths; }
    public Capture getCapture() { return capture; }
    public Mask getMask() { return mask; }
    public Async getAsync() { return async; }
    public Jdbc getJdbc() { return jdbc; }
    public Kafka getKafka() { return kafka; }
}
