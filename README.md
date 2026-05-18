# High-Performance E-Commerce Backend Engine

Spring Boot backend for an e-commerce system where concurrency control, ACID transaction boundaries, background work, and benchmarkability matter as much as functional endpoints.

## What is implemented

- JWT-based registration and login with stateless security for multi-instance load balancing
- Product catalog endpoints with Redis-backed caching for product pages and popular products
- Cart management with pessimistic cart-row locking to avoid same-user lost updates
- Checkout flow with explicit transaction boundaries and deterministic inventory locking
- Payment simulation protected by a bounded `Semaphore` to avoid downstream overload collapse
- Async post-checkout automation that generates invoice PDFs and simulates email notifications
- Daily chunk-oriented sales aggregation using Spring Batch
- Spring AOP performance monitoring that records Micrometer timers and warns on slow methods
- Integration test coverage for oversell prevention under concurrent checkout pressure

## Architecture

- Controllers: HTTP transport only
- Services: transaction boundaries, concurrency rules, business logic
- Repositories: JPA access with explicit lock modes where needed
- Aspect: cross-cutting performance instrumentation
- Batch: scheduled/manual daily aggregation job
- Cache: Redis for hot catalog reads

## Core concurrency choices

### Inventory integrity

Checkout locks inventory rows with `PESSIMISTIC_WRITE` in ascending product-id order. That combination is important:

- The lock itself prevents two buyers from decrementing the same stock row at the same time.
- The deterministic ordering avoids deadlocks when two carts overlap on the same products.
- `@Version` on inventory and product still protects non-checkout update paths from silent lost updates.

### Cart integrity

Cart mutations lock the cart row before item changes so a user cannot race `add/remove/checkout` requests against themselves and end up with duplicate or lost item updates.

### Resource protection

The payment simulator uses a fair `Semaphore` to bound concurrent payment calls. That keeps the app from turning a slow dependency into unbounded thread growth.

## Local run

### Infrastructure

```bash
docker compose up -d
```

This starts:

- PostgreSQL on `localhost:5432`
- Redis on `localhost:6379`

### Application

Use Maven or the Maven wrapper once available in your environment:

```bash
mvn spring-boot:run
```

Default seeded admin user in non-test profiles:

- username: `admin`
- password: `Admin1234!`

## Key endpoints

### Public

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/products`
- `GET /api/products/popular`
- `GET /api/products/{productId}`

### Authenticated customer

- `GET /api/cart`
- `POST /api/cart/items`
- `PUT /api/cart/items/{productId}`
- `DELETE /api/cart/items/{productId}`
- `POST /api/orders/checkout`
- `GET /api/orders`

### Admin

- `POST /api/admin/products`
- `PUT /api/admin/inventory/{productId}`
- `POST /api/admin/jobs/daily-sales?salesDate=YYYY-MM-DD`
- `GET /api/admin/reports/daily-sales/{salesDate}`

## Sample flow

### Register

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"cam","email":"cam@example.com","password":"Secret123!"}'
```

### Add to cart

```bash
curl -X POST http://localhost:8080/api/cart/items \
  -H 'Authorization: Bearer <JWT>' \
  -H 'Content-Type: application/json' \
  -d '{"productId":1,"quantity":2}'
```

### Checkout

```bash
curl -X POST http://localhost:8080/api/orders/checkout \
  -H 'Authorization: Bearer <JWT>' \
  -H 'Content-Type: application/json' \
  -d '{"paymentToken":"tok_demo_success"}'
```

Generated invoices are written to `generated/invoices/`.

## Stress testing

See [stress/jmeter/README.md](stress/jmeter/README.md) for a JMeter-oriented workflow and benchmark checklist.

## Additional docs

- [AOP Architecture](docs/AOP-ARCHITECTURE.md)
- [Concurrency Notes](docs/CONCURRENCY-AND-INTEGRITY.md)
