# Contributing

1. Fork and create a branch.
2. `mvn verify` must pass (Java 17+, Maven 3.9+).
3. Add tests for new behaviour; keep the `AuditSink` SPI backward compatible.
4. Open a pull request describing the change and why.

Ideas that are welcome: WebFlux support, more sinks (Elasticsearch, S3, files), Micrometer metrics,
retry/fallback for failing sinks, Flyway/Liquibase schema scripts.
