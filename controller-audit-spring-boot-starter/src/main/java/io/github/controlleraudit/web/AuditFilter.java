package io.github.controlleraudit.web;

import io.github.controlleraudit.config.ControllerAuditProperties;
import io.github.controlleraudit.core.AuditPublisher;
import io.github.controlleraudit.model.AuditEvent;
import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Wraps the request/response so bodies can be read afterwards, then builds an {@link AuditEvent} if the
 * {@link AuditInterceptor} marked the handler as audited.
 */
public class AuditFilter extends OncePerRequestFilter {

    public static final String TARGET_ATTR = AuditFilter.class.getName() + ".TARGET";
    public static final String USER_ATTR = AuditFilter.class.getName() + ".USER";
    private static final String MDC_KEY = "auditCorrelationId";
    private static final Logger log = LoggerFactory.getLogger(AuditFilter.class);

    private final ControllerAuditProperties props;
    private final AuditPublisher publisher;
    private final AuditMasker masker;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public AuditFilter(ControllerAuditProperties props, AuditPublisher publisher, AuditMasker masker) {
        this.props = props;
        this.publisher = publisher;
        this.masker = masker;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        for (String pattern : props.getExcludePaths()) {
            if (matcher.match(pattern, path)) {
                return true;
            }
        }
        double rate = props.getSamplingRate();
        return rate < 1.0 && ThreadLocalRandom.current().nextDouble() >= rate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper req =
                new ContentCachingRequestWrapper(request, props.getCapture().getMaxBodySize() + 1);
        ContentCachingResponseWrapper res = new ContentCachingResponseWrapper(response);
        Instant start = Instant.now();
        String correlationId = correlationId(request);
        MDC.put(MDC_KEY, correlationId);
        Throwable failure = null;
        try {
            chain.doFilter(req, res);
        } catch (IOException | ServletException | RuntimeException ex) {
            failure = ex;
            throw ex;
        } finally {
            try {
                AuditTarget target = (AuditTarget) req.getAttribute(TARGET_ATTR);
                if (target != null) {
                    publish(req, res, target, start, Instant.now(), correlationId, failure);
                }
            } catch (Exception ex) {
                log.warn("controller-audit: could not build audit event: {}", ex.toString());
            } finally {
                MDC.remove(MDC_KEY);
                res.copyBodyToResponse();
            }
        }
    }

    private void publish(ContentCachingRequestWrapper req, ContentCachingResponseWrapper res, AuditTarget target,
                         Instant start, Instant end, String correlationId, Throwable failure) {
        if (failure == null) {
            Object handled = req.getAttribute(DispatcherServlet.EXCEPTION_ATTRIBUTE);
            if (handled == null) {
                handled = req.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
            }
            if (handled instanceof Throwable t) {
                failure = t;
            }
        }
        String error = failure == null ? null : failure.getClass().getName() + ": " + failure.getMessage();
        // An exception that escaped the DispatcherServlet has not been turned into a 500 yet at this point.
        int status = res.getStatus();
        if (failure != null && status < 400) {
            status = 500;
        }

        AuditEvent event = new AuditEvent(
                UUID.randomUUID().toString(),
                correlationId,
                start,
                end,
                Duration.between(start, end).toMillis(),
                req.getMethod(),
                req.getRequestURI(),
                req.getQueryString(),
                target.handler(),
                clientIp(req),
                (String) req.getAttribute(USER_ATTR),
                masker.maskHeaders(requestHeaders(req), target.maskHeaders()),
                body(req.getContentAsByteArray(), req.getContentType(), req.getCharacterEncoding(),
                        target.requestBody(), target.maskFields()),
                status,
                masker.maskHeaders(responseHeaders(res), target.maskHeaders()),
                body(res.getContentAsByteArray(), res.getContentType(), res.getCharacterEncoding(),
                        target.responseBody(), target.maskFields()),
                error);
        publisher.publish(event, target.mode());
    }

    private String body(byte[] bytes, String contentType, String encoding, boolean enabled, Set<String> fields) {
        if (!enabled || bytes.length == 0) {
            return null;
        }
        if (!isText(contentType)) {
            return "[" + bytes.length + " bytes of " + contentType + " omitted]";
        }
        int max = props.getCapture().getMaxBodySize();
        boolean truncated = bytes.length > max;
        String text = new String(bytes, 0, Math.min(bytes.length, max), charset(contentType, encoding));
        text = masker.maskBody(text, fields);
        return truncated ? text + "...[truncated]" : text;
    }

    private static boolean isText(String contentType) {
        if (contentType == null) {
            return true;
        }
        String ct = contentType.toLowerCase(Locale.ROOT);
        return ct.contains("json") || ct.contains("xml") || ct.startsWith("text/")
                || ct.contains("x-www-form-urlencoded") || ct.contains("yaml");
    }

    private static Charset charset(String contentType, String encoding) {
        if (contentType != null && encoding != null && contentType.toLowerCase(Locale.ROOT).contains("charset=")) {
            try {
                return Charset.forName(encoding);
            } catch (Exception ignored) {
                // fall through
            }
        }
        return StandardCharsets.UTF_8;
    }

    private String correlationId(HttpServletRequest request) {
        String header = props.getCorrelationHeader();
        String value = header == null ? null : request.getHeader(header);
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }

    private String clientIp(HttpServletRequest request) {
        if (props.isTrustForwardedFor()) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private static Map<String, List<String>> requestHeaders(HttpServletRequest request) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, Collections.list(request.getHeaders(name)));
        }
        return headers;
    }

    private static Map<String, List<String>> responseHeaders(HttpServletResponse response) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (String name : response.getHeaderNames()) {
            headers.put(name, new ArrayList<>(response.getHeaders(name)));
        }
        return headers;
    }
}
