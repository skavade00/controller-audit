# Controller Audit

Annotation-driven audit logging for Spring Boot controllers. Add the dependency, put `@Audit` on your
application class, and every request/response (timestamps, duration, headers, bodies, handler, user, client IP,
errors) is written to a database, synchronously or asynchronously, and optionally to Kafka.

* Java 17+, Spring Boot 3.x, servlet (Spring MVC) apps
* No auto-configuration: **nothing happens unless you use an annotation**
* Sensitive headers and fields are masked before anything leaves the request thread
* A failing sink never breaks your request

## Requirements

* Java 17+ and Spring Boot 3.x
* A Spring MVC (servlet) web app, i.e. `spring-boot-starter-web` (WebFlux is not supported yet)
* A database reachable through a `DataSource` (PostgreSQL, MySQL, H2 ...) with its JDBC driver on the classpath.
  Or point the audit log at its own database, see [Configuration](#configuration-applicationyml-or-env-vars-eg-controller_audit_modesync)

## How to use it in your project

### Step 1: get the library

*Until it is published to Maven Central*, build and install it into your local Maven repository once:

```bash
git clone https://github.com/your-github-username/controller-audit.git
cd controller-audit
mvn clean install
```

(Use `-DskipTests` to skip the tests. Repeat this whenever you update the library.)

### Step 2: add the dependency

```xml
<dependency>
  <groupId>io.github.controlleraudit</groupId>
  <artifactId>controller-audit-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Reload Maven in your IDE.

### Step 3: add the annotation

```java
@SpringBootApplication
@Audit                      // audit every controller
public class MyApp {
    public static void main(String[] args) {
        SpringApplication.run(MyApp.class, args);
    }
}
```

No other configuration is needed. By default it uses your application's `DataSource`, creates the table
`api_audit_log` if it does not exist, and writes events asynchronously.

### Step 4: run and verify

1. Start your app and call any endpoint, e.g. `curl localhost:8080/your-endpoint`
2. Check the table (events are written in the background, so allow a moment):

```sql
select request_time, http_method, uri, response_status, duration_ms
from api_audit_log order by request_time desc limit 10;
```

### Step 5 (optional): auditing only some controllers

```java
@RestController
@Audit                                      // this controller only
public class OrderController { ... }

@Audit(mode = Mode.SYNC, maskFields = "pin") // a single method (also add @EnableControllerAudit on the main class)
@PostMapping("/login")
public Token login(...) { ... }

@AuditIgnore                                // never audit this one
@GetMapping("/health")
public String health() { ... }
```

### Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| `Could not find artifact ...controller-audit-spring-boot-starter` | Step 1 not done (run `mvn clean install` in the library folder) |
| App fails at startup: "expected exactly one DataSource" | Your app has no DataSource, or two without `@Primary`. Configure one, or set `controller-audit.jdbc.datasource=SEPARATE` (with `url`, `username`, `password`), or `controller-audit.jdbc.enabled=false` |
| Nothing is logged | `@Audit` / `@EnableControllerAudit` missing, the endpoint is in an excluded path (`/actuator/**`, `/error`), or the method has `@AuditIgnore`. Check the app log for `controller-audit:` warnings |
| Table not created | `controller-audit.jdbc.auto-create-table` is false, or the DB user lacks `CREATE TABLE` permission |
| Rows appear late | Normal in `ASYNC` mode. Use `mode: SYNC` to write inside the request |

### Try the demo first

```bash
mvn -pl controller-audit-sample -am package
java -jar controller-audit-sample/target/controller-audit-sample-0.1.0-SNAPSHOT.jar
curl "localhost:8080/greet?name=you"
curl localhost:8080/audit-log
```

## Annotations

| Annotation | Where | Effect |
|---|---|---|
| `@Audit` | main/`@Configuration` class | enable + audit **all** controllers |
| `@Audit` | controller class | audit all its handler methods (also enables the feature) |
| `@Audit` | handler method | audit only that method (needs `@EnableControllerAudit` or another `@Audit`) |
| `@EnableControllerAudit` | main class | enable without auditing anything by itself |
| `@AuditIgnore` | class or method | never audit, even if an outer scope has `@Audit` |

`@Audit` attributes: `mode` (`INHERIT`/`SYNC`/`ASYNC`), `requestBody`, `responseBody`, `maskHeaders`, `maskFields`.
Method beats class beats application-level; masks are the union of all levels plus the global config.

```java
@Audit(mode = Mode.SYNC, maskFields = "pin")
@PostMapping("/login")
public Token login(@RequestBody Credentials c) { ... }
```

## Configuration (`application.yml` or env vars, e.g. `CONTROLLER_AUDIT_MODE=SYNC`)

```yaml
controller-audit:
  enabled: true
  mode: ASYNC                 # SYNC | ASYNC (default for @Audit with mode=INHERIT)
  sampling-rate: 1.0          # 0.0 - 1.0
  trust-forwarded-for: false  # only behind a trusted proxy
  correlation-header: X-Request-Id
  exclude-paths: [/actuator/**, /error]
  capture:
    request-body: true
    response-body: true
    max-body-size: 10240      # bytes
  mask:
    headers: [Authorization, Cookie, Set-Cookie, X-Api-Key]   # replaces the defaults if set
    fields:  [password, passwd, secret, token, access_token, refresh_token, api_key]
  async:
    queue-capacity: 10000
    worker-threads: 1
    batch-size: 100
    idle-poll-ms: 500
    overflow-policy: DROP     # DROP | BLOCK | CALLER_RUNS
    shutdown-timeout-ms: 10000
  jdbc:
    enabled: true
    table-name: api_audit_log
    auto-create-table: true
    datasource: APP           # APP = your DataSource, SEPARATE = the settings below
    url: jdbc:postgresql://audit-db:5432/audit
    username: audit
    password: secret
    driver-class-name: org.postgresql.Driver
  kafka:
    enabled: false
    topic: api-audit-events
```

## Kafka (optional)

Add the extra module, configure Spring Kafka as usual and switch it on:

```xml
<dependency>
  <groupId>io.github.controlleraudit</groupId>
  <artifactId>controller-audit-kafka</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```
```yaml
spring.kafka.bootstrap-servers: localhost:9092
controller-audit.kafka.enabled: true
```
Each event is sent as JSON (key = correlation id). Kafka and JDBC can run side by side; set
`controller-audit.jdbc.enabled=false` for Kafka only.

## Custom sinks

Any bean implementing `AuditSink` receives every event:

```java
@Component
class ConsoleSink implements AuditSink {
    public void write(AuditEvent e) { System.out.println(e.httpMethod() + " " + e.uri() + " -> " + e.responseStatus()); }
}
```
Override `writeBatch` if your target supports bulk writes.

## Table schema

Created automatically (`CREATE TABLE IF NOT EXISTS`, tested shape for H2 / PostgreSQL / MySQL). For other
databases or managed migrations, create it yourself and set `auto-create-table: false`:

`event_id` (PK), `correlation_id`, `request_time`, `response_time`, `duration_ms`, `http_method`, `uri`,
`query_string`, `handler`, `client_ip`, `username`, `request_headers` (JSON text), `request_body`,
`response_status`, `response_headers` (JSON text), `response_body`, `error`.
You will probably want an index on `request_time`.

## How it works

```
Filter (wraps request/response, runs first)
  -> HandlerInterceptor (knows the handler; reads @Audit/@AuditIgnore; stores AuditTarget on the request)
  -> controller runs
Filter (after chain) -> build AuditEvent -> mask + truncate -> AuditPublisher
        SYNC : sinks written on the request thread
        ASYNC: bounded queue -> worker threads -> sink.writeBatch(...)
```

## Known limitations

* Servlet/MVC only (no WebFlux yet); async dispatch (`DeferredResult`, `Callable`) responses are not captured
* Streaming/SSE and file downloads: bodies are omitted for non-text content types
* Async mode can lose queued events on a hard crash; use `SYNC` for must-not-lose auditing
* Masking is name based (JSON keys / form fields); it does not understand every body format
* No retry/fallback for failed sinks yet (failures are logged)

## Build

```
mvn verify
mvn -pl controller-audit-sample -am package && java -jar controller-audit-sample/target/*.jar
curl localhost:8080/greet?name=you ; curl -XPOST localhost:8080/login -H 'Content-Type: application/json' \
     -d '{"username":"a","password":"b"}' ; curl localhost:8080/audit-log
```

Before publishing to Maven Central, change the `groupId` / base package, fill in `<scm>`, `<developers>`,
and add the source/javadoc/GPG plugins in a release profile.

## License

MIT
