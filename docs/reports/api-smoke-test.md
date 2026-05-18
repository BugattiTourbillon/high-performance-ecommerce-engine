# API Smoke Test Report

- Base URL: `http://localhost:8088`
- Run time: `2026-05-18T16:32:30Z`

| Check | HTTP result | Status |
| --- | --- | --- |
| health | `200` | pass |
| public product list | `200` | pass |
| public popular products | `200` | pass |
| public raw baseline products | `200` | pass |
| admin login | `200` | pass |
| customer register | `201` | pass |
| admin create product | `201` | pass |
| product details | `200` | pass |
| raw product details | `200` | pass |
| admin update inventory | `200` | pass |
| customer get cart | `200` | pass |
| customer add cart item | `200` | pass |
| customer update cart item | `200` | pass |
| customer checkout | `200` | pass |
| customer list orders | `200` | pass |
| admin daily sales job | `200` | pass |
| admin daily sales report | `200` | pass |
| unauthorized cart rejection | `401` | pass |
| customer admin rejection | `403` | pass |

Summary: 14 passed, 0 failed.

Expected result: all rows should be `pass`; invoices should appear under `generated/invoices/` after checkout.
