# Catalog Benchmark Report

- Base URL: `http://localhost:8088`
- Requests per scenario: `240`
- Concurrency: `40`
- Generated: `2026-05-18T16:33:04Z`

| Scenario | Success | Failures | Error rate | Throughput | Avg | p50 | p95 | Max |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| before_raw_catalog | 240 | 0 | 0.0% | 180.73 rps | 199.38 ms | 174.68 ms | 375.71 ms | 716.3 ms |
| after_cached_catalog | 240 | 0 | 0.0% | 341.99 rps | 105.77 ms | 84.22 ms | 229.99 ms | 337.39 ms |
| before_raw_popular | 240 | 0 | 0.0% | 351.05 rps | 108.24 ms | 94.79 ms | 190.39 ms | 350.14 ms |
| after_cached_popular | 240 | 0 | 0.0% | 424.39 rps | 89.37 ms | 83.98 ms | 168.87 ms | 273.61 ms |

Interpretation: the `before_*` endpoints bypass Redis and intentionally represent the unoptimized read path. The `after_*` endpoints are the production optimized cacheable APIs.

Chart artifact: `docs/reports/screenshots/catalog-before-after-p95.svg`
