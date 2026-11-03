package io.github.controlleraudit.web;

import io.github.controlleraudit.annotation.Audit;
import io.github.controlleraudit.annotation.AuditIgnore;
import io.github.controlleraudit.annotation.Mode;
import io.github.controlleraudit.config.ControllerAuditProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.security.Principal;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs inside the DispatcherServlet, where the matched handler is known. Resolves the annotations and
 * leaves the result on the request for {@link AuditFilter}.
 */
public class AuditInterceptor implements HandlerInterceptor {

    private final ControllerAuditProperties props;
    private final AuditScope scope;
    private final Map<String, Optional<AuditTarget>> cache = new ConcurrentHashMap<>();

    public AuditInterceptor(ControllerAuditProperties props, AuditScope scope) {
        this.props = props;
        this.scope = scope;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod hm) || request.getAttribute(AuditFilter.TARGET_ATTR) != null) {
            return true;
        }
        String key = hm.getBeanType().getName() + "#" + hm.getMethod().toGenericString();
        Optional<AuditTarget> target = cache.computeIfAbsent(key, k -> Optional.ofNullable(resolve(hm)));
        target.ifPresent(t -> {
            request.setAttribute(AuditFilter.TARGET_ATTR, t);
            // Read the principal here: inside the security chain it is already known, in the outer filter it is not.
            Principal principal = request.getUserPrincipal();
            if (principal != null) {
                request.setAttribute(AuditFilter.USER_ATTR, principal.getName());
            }
        });
        return true;
    }

    private AuditTarget resolve(HandlerMethod hm) {
        Class<?> type = hm.getBeanType();
        if (AnnotatedElementUtils.hasAnnotation(hm.getMethod(), AuditIgnore.class)
                || AnnotatedElementUtils.hasAnnotation(type, AuditIgnore.class)) {
            return null;
        }
        Audit method = AnnotatedElementUtils.findMergedAnnotation(hm.getMethod(), Audit.class);
        Audit cls = AnnotatedElementUtils.findMergedAnnotation(type, Audit.class);
        Audit global = scope.global();
        Audit primary = method != null ? method : cls != null ? cls : global;
        if (primary == null) {
            return null;
        }

        Mode mode = null;
        for (Audit a : new Audit[]{method, cls, global}) {
            if (a != null && a.mode() != Mode.INHERIT) {
                mode = a.mode();
                break;
            }
        }
        if (mode == null) {
            mode = props.getMode() == null || props.getMode() == Mode.INHERIT ? Mode.ASYNC : props.getMode();
        }

        Set<String> headers = new HashSet<>();
        props.getMask().getHeaders().forEach(h -> headers.add(h.toLowerCase(Locale.ROOT)));
        Set<String> fields = new HashSet<>();
        props.getMask().getFields().forEach(f -> fields.add(f.toLowerCase(Locale.ROOT)));
        for (Audit a : new Audit[]{method, cls, global}) {
            if (a != null) {
                for (String h : a.maskHeaders()) {
                    headers.add(h.toLowerCase(Locale.ROOT));
                }
                for (String f : a.maskFields()) {
                    fields.add(f.toLowerCase(Locale.ROOT));
                }
            }
        }

        String handlerName = type.getName() + "#" + hm.getMethod().getName();
        return new AuditTarget(handlerName, mode,
                props.getCapture().isRequestBody() && primary.requestBody(),
                props.getCapture().isResponseBody() && primary.responseBody(),
                Set.copyOf(headers), Set.copyOf(fields));
    }
}
