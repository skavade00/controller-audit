# Changelog

## 0.1.0 (unreleased)
- `@Audit`, `@AuditIgnore`, `@EnableControllerAudit`
- Request/response capture: timestamps, duration, headers, bodies, handler, client IP, user, correlation id, error
- Sync and async (bounded queue, batched) writing with DROP / BLOCK / CALLER_RUNS overflow policies
- JDBC sink (app or separate DataSource, optional table auto-creation)
- Optional Kafka sink (`controller-audit-kafka`)
- Header and JSON/form field masking, body size limit, path exclusion, sampling
