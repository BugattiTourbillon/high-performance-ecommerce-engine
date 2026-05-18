# AOP Architecture Document

## Goal

Measure runtime cost of controllers, services, and repositories without mixing timing code into business logic.

## Implementation summary

The aspect lives in `com.ecommerce.engine.aspect.PerformanceMonitoringAspect` and uses an `@Around` advice with this pointcut family:

- `com.ecommerce.engine.controller..*`
- `com.ecommerce.engine.service..*`
- `com.ecommerce.engine.repository..*`

For every intercepted method it:

1. Captures `System.nanoTime()` before execution.
2. Proceeds with the method call.
3. Records success or exception outcome.
4. Publishes a Micrometer timer named `commerce.method.execution` with tags:
   - `class`
   - `method`
   - `outcome`
5. Emits a warning log when execution time crosses `app.monitoring.slow-call-threshold-ms`.

## Why AOP was the right fit

- No controller or service is polluted with stopwatch code.
- New services automatically inherit the same monitoring policy.
- Benchmark runs can compare metrics before and after optimization through Actuator/Micrometer output.
- Slow-path logging remains centralized and consistent.

## Operational use

During stress tests, compare:

- `GET /actuator/metrics/commerce.method.execution`
- `GET /actuator/metrics/http.server.requests`
- application logs containing `Slow method detected`

That combination makes it easier to separate transport latency from service-layer or repository-layer bottlenecks.

## Extension ideas

- Add percentile histograms for `commerce.method.execution`
- Ship metrics to Prometheus/Grafana
- Add tags for tenant, endpoint group, or lock wait classification
