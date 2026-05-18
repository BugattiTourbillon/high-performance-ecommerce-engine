# JMeter Stress-Testing Guide

## Goal

Validate that the service survives 100+ concurrent users without overselling stock or corrupting orders.

## Recommended scenarios

### Catalog cache warm path

- `GET /api/products`
- `GET /api/products/popular`

Success criteria:

- latency drops after warm-up because Redis absorbs repeated reads
- no error spikes under 100 to 200 concurrent users

### Checkout contention path

- create a product with limited inventory
- provision many users with carts targeting the same product
- run concurrent `POST /api/orders/checkout` requests

Success criteria:

- successful orders never exceed available inventory
- failed orders are clean `400` responses for insufficient stock, not data corruption
- stock never becomes negative

## Metrics to inspect

- `GET /actuator/metrics/http.server.requests`
- `GET /actuator/metrics/commerce.method.execution`
- PostgreSQL lock waits
- Redis hit ratio
- application logs for `Slow method detected`

## Suggested JMeter thread groups

### Product listing benchmark

- users: 120
- ramp-up: 15 seconds
- loop count: 20
- endpoint: `GET /api/products?page=0&size=20`

### Hot checkout benchmark

- users: 120
- ramp-up: 5 seconds
- loop count: 1
- endpoint: `POST /api/orders/checkout`
- body template:

```json
{
  "paymentToken": "tok_jmeter_${__threadNum}"
}
```

## Recommended assertions

- HTTP status is `200` or `400`
- no `500` errors
- order count in database never exceeds original stock
- inventory quantity never goes below zero

## Before/after optimization comparison

Capture these numbers before and after changing lock strategy, cache TTLs, or pool sizes:

- p50 latency
- p95 latency
- error rate
- successful checkout count
- database CPU / connection pool saturation

## Handy curl checks during a run

```bash
curl http://localhost:8080/actuator/metrics/http.server.requests
curl http://localhost:8080/actuator/metrics/commerce.method.execution
curl http://localhost:8080/api/products/popular
```
