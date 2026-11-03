package io.github.controlleraudit.sink;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.controlleraudit.model.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.regex.Pattern;

/** Writes events to a relational table with batched inserts. */
public class JdbcAuditSink implements AuditSink, InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(JdbcAuditSink.class);
    private static final Pattern SAFE_NAME =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");

    private final JdbcTemplate jdbc;
    private final String table;
    private final boolean autoCreate;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String insertSql;

    public JdbcAuditSink(JdbcTemplate jdbc, String table, boolean autoCreate) {
        if (table == null || !SAFE_NAME.matcher(table).matches()) {
            throw new IllegalArgumentException("Invalid controller-audit.jdbc.table-name: " + table);
        }
        this.jdbc = jdbc;
        this.table = table;
        this.autoCreate = autoCreate;
        this.insertSql = "INSERT INTO " + table + " (event_id, correlation_id, request_time, response_time, "
                + "duration_ms, http_method, uri, query_string, handler, client_ip, username, request_headers, "
                + "request_body, response_status, response_headers, response_body, error) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    }

    @Override
    public void afterPropertiesSet() {
        if (!autoCreate) {
            return;
        }
        try {
            jdbc.execute(createTableSql(table));
        } catch (Exception ex) {
            log.error("controller-audit: could not auto-create table {}: {}", table, ex.toString());
        }
    }

    public static String createTableSql(String table) {
        return "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "event_id VARCHAR(64) PRIMARY KEY, "
                + "correlation_id VARCHAR(128), "
                + "request_time TIMESTAMP NOT NULL, "
                + "response_time TIMESTAMP NULL, "
                + "duration_ms BIGINT, "
                + "http_method VARCHAR(10), "
                + "uri VARCHAR(2048), "
                + "query_string VARCHAR(2048), "
                + "handler VARCHAR(512), "
                + "client_ip VARCHAR(64), "
                + "username VARCHAR(256), "
                + "request_headers TEXT, "
                + "request_body TEXT, "
                + "response_status INT, "
                + "response_headers TEXT, "
                + "response_body TEXT, "
                + "error VARCHAR(2048))";
    }

    @Override
    public void write(AuditEvent event) {
        writeBatch(List.of(event));
    }

    @Override
    public void writeBatch(List<AuditEvent> events) {
        jdbc.batchUpdate(insertSql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                AuditEvent e = events.get(i);
                ps.setString(1, e.eventId());
                ps.setString(2, cut(e.correlationId(), 128));
                ps.setTimestamp(3, Timestamp.from(e.requestTime()));
                ps.setTimestamp(4, e.responseTime() == null ? null : Timestamp.from(e.responseTime()));
                ps.setLong(5, e.durationMs());
                ps.setString(6, cut(e.httpMethod(), 10));
                ps.setString(7, cut(e.uri(), 2048));
                ps.setString(8, cut(e.queryString(), 2048));
                ps.setString(9, cut(e.handler(), 512));
                ps.setString(10, cut(e.clientIp(), 64));
                ps.setString(11, cut(e.username(), 256));
                ps.setString(12, json(e.requestHeaders()));
                ps.setString(13, e.requestBody());
                ps.setInt(14, e.responseStatus());
                ps.setString(15, json(e.responseHeaders()));
                ps.setString(16, e.responseBody());
                ps.setString(17, cut(e.error(), 2048));
            }

            @Override
            public int getBatchSize() {
                return events.size();
            }
        });
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
