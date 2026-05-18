# Checkout Contention Stress Report

- Base URL: `http://localhost:8088`
- Concurrent users: `120`
- Initial stock: `40`
- Product ID: `18`
- Wall time: `3.74s`
- Generated: `2026-05-18T16:33:37Z`

| Metric | Value |
| --- | ---: |
| Successful checkouts | 40 |
| Clean insufficient-stock/payment rejections | 80 |
| Server errors | 0 |
| Remaining inventory | 0 |
| p50 checkout latency | 3606.81 ms |
| p95 checkout latency | 3686.21 ms |
| Max checkout latency | 3715.74 ms |

Expected integrity result: successful checkouts must never exceed initial stock, remaining inventory must never be negative, and server errors must be zero.

Observed HTTP statuses: `{200: 40, 400: 80}`
