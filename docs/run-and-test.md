
This document is the handoff guide for running and validating the High-Performance E-Commerce Backend Engine.

## 1. Prerequisites

Use Java 21. On this machine:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

Required tools:

- Java 21
- Maven
- Docker Desktop or compatible Docker Engine
- Python 3
- curl

## 2. Start Infrastructure

For single-instance local development:

```bash
docker compose up -d
```

Expected services:

- PostgreSQL: `localhost:5432`
- Redis: `localhost:6379`

## 3. Run Tests

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test
```

Expected output:

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

What this verifies:

- all API endpoint groups work through Spring MVC
- authentication and authorization rules work
- checkout creates paid orders
- batch sales report job runs
- benchmark baseline endpoints respond
- concurrent checkout cannot oversell inventory

## 4. Package The Application

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q -DskipTests package
```

Expected output file:

```text
target/high-performance-engine-0.0.1-SNAPSHOT.jar
```

## 5. Run Single App Instance

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn spring-boot:run
```

Default URL:

```text
http://localhost:8080
```

Default admin:

```text
username: admin
password: Admin1234!
```

Health check:

```bash
curl http://localhost:8080/actuator/health
```

Expected output:

```json
{"status":"UP"}
```

## 6. Run Full API Smoke Test

```bash
BASE_URL=http://localhost:8080 scripts/api-smoke-test.sh
```

Generated report:

```text
docs/reports/api-smoke-test.md
```

Expected summary:

```text
Summary: 20 passed, 0 failed.
```

The script validates:

- `/actuator/health`
- `/api/auth/register`
- `/api/auth/login`
- `/api/products`
- `/api/products/popular`
- `/api/products/{id}`
- `/api/benchmark/products/raw`
- `/api/benchmark/products/popular/raw`
- `/api/benchmark/products/{id}/raw`
- `/api/admin/products`
- `/api/admin/inventory/{id}`
- `/api/cart`
- `/api/cart/items`
- `/api/cart/items/{id}`
- `/api/orders/checkout`
- `/api/orders`
- `/api/admin/jobs/daily-sales`
- `/api/admin/reports/daily-sales/{date}`
- unauthenticated rejection
- customer-to-admin forbidden rejection

## 7. Run Before/After Benchmark

```bash
BASE_URL=http://localhost:8080 REQUESTS=240 CONCURRENCY=40 scripts/catalog-benchmark.py
```

Generated files:

```text
docs/reports/catalog-benchmark.md
docs/reports/catalog-benchmark.csv
docs/reports/screenshots/catalog-before-after-p95.svg
```

The comparison is:

| Before optimization | After optimization |
| --- | --- |
| `/api/benchmark/products/raw` | `/api/products` |
| `/api/benchmark/products/popular/raw` | `/api/products/popular` |

Expected output properties:

- `failures` should be `0`
- `error_rate_pct` should be `0`
- cached endpoints should normally have better p95 latency after warm-up

## 8. Run 100+ Concurrent User Stress Test

```bash
BASE_URL=http://localhost:8080 USERS=120 STOCK=40 scripts/checkout-contention.py
```

Generated report:

```text
docs/reports/checkout-contention-report.md
```

Expected integrity properties:

```text
Successful checkouts <= Initial stock
Remaining inventory >= 0
Server errors = 0
```

This is the required proof for serving at least 100 concurrent users without data loss or inventory corruption.

## 9. Run Load-Balanced Deployment Simulation

Build the jar first:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q -DskipTests package
```

Start two backend instances behind nginx:

```bash
docker compose -f docker-compose.loadbalancer.yml up --build
```

Load-balanced URL:

```text
http://localhost:8088
```

Run the same tests through nginx:

```bash
BASE_URL=http://localhost:8088 scripts/api-smoke-test.sh
BASE_URL=http://localhost:8088 REQUESTS=240 CONCURRENCY=40 scripts/catalog-benchmark.py
BASE_URL=http://localhost:8088 USERS=120 STOCK=40 scripts/checkout-contention.py
```

Expected result:

- smoke test passes
- benchmark report generated
- stress report generated
- no inventory goes negative
- no `500` error spike

## 10. Useful Manual API Checks

Login:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Admin1234!"}'
```

List products:

```bash
curl http://localhost:8080/api/products
```

Raw baseline list:

```bash
curl 'http://localhost:8080/api/benchmark/products/raw?page=0&size=20'
```

Metrics:

```bash
curl http://localhost:8080/actuator/metrics/http.server.requests
curl http://localhost:8080/actuator/metrics/commerce.method.execution
curl http://localhost:8080/actuator/prometheus
```

